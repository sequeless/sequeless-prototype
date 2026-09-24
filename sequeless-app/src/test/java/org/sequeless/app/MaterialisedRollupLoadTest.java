package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Load-style acceptance test (T13) proving {@code ex:openTaskCount} — a {@code sq:count} Rollup
 * over {@code ex:Task}, marked {@code sq:materialised true} in {@code reference.ttl} — survives
 * 200 concurrent recomputes of the same {@code ex:Project} with no lost update: the materialised
 * value stored in {@code sq_object.props} must converge to exactly the same final value as the
 * freshly-recomputed-on-read aggregate, and {@code filter[openTaskCount][eq]=0} must find the
 * Project once it gets there.
 *
 * <p>Mirrors {@link AutomationEndToEndTest}'s and {@link TransitionsEndToEndTest}'s idiom: same
 * real Postgres Testcontainer via {@link PostgresTestcontainersSupport}, same {@code
 * RestTestClient} usage, same hand-rolled deadline-loop polling style.
 *
 * <p>Per F69 (recorded during T12): {@code ex:totalEstimatedHours} (a separate, non-materialised
 * {@code sq:sum} Rollup over {@code ex:estimatedHours}) crashes {@code GET /objects/Project/{id}}
 * with a {@code NullPointerException} if any Task belonging to the Project has no {@code
 * estimatedHours} set — Postgres {@code SUM()} over an all-NULL group returns SQL {@code NULL},
 * and {@code DecimalValue}'s constructor rejects {@code null}. Every one of the 200 Tasks this test
 * creates sets {@code estimatedHours} for exactly this reason.
 *
 * <p>The {@code properties} array below differs from every other {@code @SpringBootTest} class in
 * this module (per {@code TransitionsEndToEndTest}'s and {@code AutomationEndToEndTest}'s own
 * javadoc on this point) so this class gets its own Spring test context/Testcontainers Postgres
 * container rather than reusing another class's already-stopped one. {@code
 * sequeless.automation.relay.poll-interval-ms=75} is a value not used by any sibling class.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=inprocess",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.relay.poll-interval-ms=75"
        })
class MaterialisedRollupLoadTest extends PostgresTestcontainersSupport {

    private static final String OPEN_TASK_COUNT_IRI = "https://sequeless.dev/ns/ref#openTaskCount";
    private static final int TASK_COUNT = 200;

    @LocalServerPort private int port;

    @Autowired private DataSource sequelessPersistenceDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void twoHundredConcurrentTaskCompletionsProduceNoLostUpdateOnOpenTaskCount() throws Exception {
        RestTestClient client = restTestClient();

        BusinessObjectResponse project = createProject(client, "Load test project");
        BusinessObjectResponse owner = createPerson(client, "Load Test Owner");
        BusinessObjectResponse activated = setOwnerAndActivate(client, project, "Load test project", owner);
        UUID projectId = project.id();

        // Create 200 Tasks, all pointing at the same Project, all status "open" (!= "done", so all
        // count toward openTaskCount initially), each with estimatedHours set per F69 above.
        List<BusinessObjectResponse> tasks = new ArrayList<>(TASK_COUNT);
        for (int i = 0; i < TASK_COUNT; i++) {
            tasks.add(createTask(client, "Load task " + i, projectId.toString(), "open"));
        }

        // Confirm the materialised value has converged after the 200 sequential creates before
        // starting the concurrent phase.
        awaitOpenTaskCount(client, projectId, TASK_COUNT, Duration.ofSeconds(20));

        // Fire 200 concurrent PUTs, each flipping exactly one Task's status to "done". There is no
        // contention on the Task writes themselves (200 distinct objects, each PUT uses that Task's
        // own current expectedVersion) -- the actual race is 200 concurrent recomputes of the SAME
        // Project's openTaskCount.
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(TASK_COUNT);
            for (BusinessObjectResponse task : tasks) {
                futures.add(CompletableFuture.runAsync(() -> completeTask(client, task), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } finally {
            executor.shutdown();
        }

        // Poll until the on-read openTaskCount stabilizes at 0 -- a load test, so give it real time.
        awaitOpenTaskCount(client, projectId, 0, Duration.ofSeconds(60));

        BusinessObjectResponse finalProject = fetchProject(client, projectId);
        assertThat(finalProject.properties().get("openTaskCount"))
                .as("on-read openTaskCount is 0 once all 200 Tasks are done")
                .isEqualTo(0);

        // The on-read value above is *freshly recomputed on every GET* (per reference.ttl), never
        // read from the materialised store -- so it can reach 0 before the async recompute pipeline
        // has finished writing that same 0 back into sq_object.props. Poll the STORED value directly
        // until it also converges, with a generous ceiling; this is the actual "no lost update
        // among 200 concurrent recomputes" proof.
        Long storedValue = awaitStoredOpenTaskCount(projectId, 0L, Duration.ofSeconds(60));
        assertThat(storedValue)
                .as("stored (materialised) openTaskCount matches the on-read GET response")
                .isEqualTo(0L);

        // filter[openTaskCount][eq]=0 filters against that same materialised store, so it too may
        // lag on-read convergence -- poll it rather than asserting on a single snapshot.
        awaitFilterFindsProject(client, projectId, Duration.ofSeconds(30));
    }

    private Long awaitStoredOpenTaskCount(UUID projectId, long expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Long lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = storedOpenTaskCount(projectId);
            if (Long.valueOf(expected).equals(lastSeen)) {
                return lastSeen;
            }
            sleep(Duration.ofMillis(200));
        }
        assertThat(lastSeen)
                .as("Project %s stored openTaskCount eventually reaches %d", projectId, expected)
                .isEqualTo(expected);
        return lastSeen;
    }

    private void awaitFilterFindsProject(RestTestClient client, UUID projectId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        java.util.Set<String> lastSeen = java.util.Set.of();
        while (Instant.now().isBefore(deadline)) {
            JsonNode filtered = fetchAsJson(client, "/objects/Project?filter[openTaskCount][eq]=0&size=200");
            lastSeen = idsOf(filtered);
            if (lastSeen.contains(projectId.toString())) {
                return;
            }
            sleep(Duration.ofMillis(200));
        }
        assertThat(lastSeen)
                .as("querying filter[openTaskCount][eq]=0 eventually finds the converged Project")
                .contains(projectId.toString());
    }

    private void completeTask(RestTestClient client, BusinessObjectResponse task) {
        client.put()
                .uri("/objects/Task/" + task.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "version",
                                task.version(),
                                "properties",
                                Map.of(
                                        "title", task.properties().get("title"),
                                        "belongsToProject", task.properties().get("belongsToProject"),
                                        "status", "done",
                                        "estimatedHours", task.properties().get("estimatedHours"))))
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    private Long storedOpenTaskCount(UUID projectId) {
        JdbcClient jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
        return jdbcClient
                .sql("SELECT (props -> :iri ->> 'integer')::bigint FROM sq_object WHERE id = :id")
                .param("iri", OPEN_TASK_COUNT_IRI)
                .param("id", projectId)
                .query(Long.class)
                .single();
    }

    private void awaitOpenTaskCount(
            RestTestClient client, UUID projectId, int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Object lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            BusinessObjectResponse project = fetchProject(client, projectId);
            lastSeen = project.properties().get("openTaskCount");
            if (Long.valueOf(expected).equals(lastSeen) || Integer.valueOf(expected).equals(lastSeen)) {
                return;
            }
            sleep(Duration.ofMillis(200));
        }
        assertThat(lastSeen)
                .as("Project %s openTaskCount eventually reaches %d", projectId, expected)
                .isIn(expected, Long.valueOf(expected));
    }

    private BusinessObjectResponse fetchProject(RestTestClient client, UUID projectId) {
        BusinessObjectResponse response =
                client.get()
                        .uri("/objects/Project/" + projectId)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(response).isNotNull();
        return response;
    }

    private BusinessObjectResponse setOwnerAndActivate(
            RestTestClient client,
            BusinessObjectResponse project,
            String title,
            BusinessObjectResponse owner) {
        client.put()
                .uri("/objects/Project/" + project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "version",
                                1,
                                "properties",
                                Map.of(
                                        "title", List.of(title),
                                        "owner", owner.id().toString())))
                .exchange()
                .expectStatus()
                .isOk();

        return client.post()
                .uri("/objects/Project/" + project.id() + "/transitions/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedVersion", 2))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createProject(RestTestClient client, String title) {
        return client.post()
                .uri("/objects/Project")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("title", List.of(title))))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createPerson(RestTestClient client, String name) {
        return client.post()
                .uri("/objects/Person")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("name", name)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createTask(
            RestTestClient client, String title, String belongsToProjectId, String status) {
        return client.post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "properties",
                                Map.of(
                                        "title", List.of(title),
                                        "belongsToProject", belongsToProjectId,
                                        "status", status,
                                        // See this class's javadoc (F69): every Task must set
                                        // estimatedHours or GET /objects/Project/{id} crashes with
                                        // an NPE computing the (unrelated) totalEstimatedHours
                                        // rollup over an all-NULL group.
                                        "estimatedHours", 1)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private JsonNode fetchAsJson(RestTestClient client, String uri) {
        byte[] responseBody =
                client.get()
                        .uri(uri)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private java.util.Set<String> idsOf(JsonNode result) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode item : result.get("items")) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
