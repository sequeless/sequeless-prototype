package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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
 * Proves, end to end against a real Postgres container, every {@code /objects} acceptance criterion
 * plan.md §9 and the Verification section list that {@code ObjectsEndToEndTest} (T12's happy-path
 * and precondition-handling coverage) does not already exercise: the two rejection shapes
 * ({@code source=structural} and {@code source=shacl}), a reference pointing at the wrong type, a
 * stale-version conflict, soft delete surviving as a row with {@code deleted_at} set, and the outbox
 * row count tracking only successful mutations.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason every other {@code
 * @SpringBootTest} in this module does: {@code sequeless.persistence.adapter=postgres} is baked into
 * {@code application.yaml}, so this context needs a reachable Postgres regardless of which endpoint a
 * given test exercises.
 *
 * <p>{@code sequeless.ontology.adapter=jena} is set explicitly via {@code properties} — already
 * {@code application.yaml}'s own default, so functionally a no-op — purely so this class's {@code
 * @SpringBootTest} signature differs from every other {@code @SpringBootTest} class in this module,
 * per {@code ObjectsEndToEndTest}'s javadoc explaining why a distinguishing property is required to
 * avoid Spring's test-context cache reusing another class's now-stopped Testcontainers container.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "sequeless.ontology.adapter=jena")
class AcceptanceCriteriaIT extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;
    @Autowired private DataSource sequelessPersistenceDataSource;

    private RestTestClient restTestClient;
    private JdbcClient jdbcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
    }

    @Test
    void addWithoutTitleReturnsStructuralViolationOnTitle() {
        // ex:title has minCardinality 1 and no maxCardinality (F39): omitting the key entirely is
        // what triggers the required-property violation, not sending an empty list (an empty list
        // would still fail the same minCardinality check, but omission is the more literal reading
        // of "a Task without a title").
        Map<String, Object> body = Map.of("properties", Map.of());

        restTestClient
                .post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.source")
                .isEqualTo("structural")
                .jsonPath("$.violations[?(@.property == 'title')]")
                .exists();
    }

    @Test
    void addWithEstimatedHoursOutOfRangeReturnsShaclViolation() {
        // ex:TaskShape's sh:maxInclusive on estimatedHours is 1000; 5000 violates it.
        // estimatedHours gained an owl:maxCardinality 1 restriction in Phase 4 (T5, so its
        // Project-level rollup can sum it in SQL), so it is now scalar-expecting like name
        // (F27's comment below): send a bare 5000, not a list.
        Map<String, Object> body =
                Map.of(
                        "properties",
                        Map.of("title", List.of("Overbudget task"), "estimatedHours", 5000));

        restTestClient
                .post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.source")
                .isEqualTo("shacl");
    }

    @Test
    void addWithWrongReferenceTypeReturnsStructuralViolation() {
        // belongsToProject's range is ex:Project; pointing it at a Person must be rejected by
        // StructuralValidator's reference-type check (F27/plan §4), not by SHACL.
        // ex:name gained an owl:maxCardinality 1 restriction (T13, so Person.name can serve as a
        // display-label extraction target for reference facets), so it is now scalar-expecting:
        // send a bare string, not a list.
        BusinessObjectResponse person = createPerson(Map.of("name", "Grace Hopper"));

        Map<String, Object> body =
                Map.of(
                        "properties",
                        Map.of("title", List.of("Misfiled task"), "belongsToProject", person.id().toString()));

        restTestClient
                .post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.source")
                .isEqualTo("structural")
                .jsonPath("$.violations[?(@.property == 'belongsToProject')]")
                .exists();
    }

    @Test
    void editWithStaleVersionReturns409() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Edit me twice")));

        // First edit lands and moves the object to version 2.
        restTestClient
                .put()
                .uri("/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("version", 1, "properties", Map.of("title", List.of("First edit"))))
                .exchange()
                .expectStatus()
                .isOk();

        // Second edit reuses the now-stale version 1 and must be rejected with 409.
        restTestClient
                .put()
                .uri("/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("version", 1, "properties", Map.of("title", List.of("Second edit, stale"))))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.expectedVersion")
                .isEqualTo(1);
    }

    @Test
    void deleteHidesFromBrowseAndReadButRowSurvivesWithDeletedAtSet() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Soft delete me")));

        restTestClient
                .delete()
                .uri("/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        restTestClient
                .get()
                .uri("/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isNotFound();

        var page =
                restTestClient
                        .get()
                        .uri("/objects/Task?page=0&size=200")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(new String(page)).doesNotContain(created.id().toString());

        var deletedAt =
                jdbcClient
                        .sql("SELECT deleted_at FROM sq_object WHERE id = ?")
                        .param(created.id())
                        .query(java.sql.Timestamp.class)
                        .optional();
        assertThat(deletedAt).isPresent();
        assertThat(deletedAt.get()).isNotNull();
    }

    @Test
    void outboxRowCountTracksSuccessfulMutationsOnly() {
        long before = outboxCount();
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Outbox add")));
        assertThat(outboxCount()).as("successful add").isEqualTo(before + 1);

        long beforeEdit = outboxCount();
        restTestClient
                .put()
                .uri("/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("version", 1, "properties", Map.of("title", List.of("Outbox edit"))))
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(outboxCount()).as("successful edit").isEqualTo(beforeEdit + 1);

        long beforeDelete = outboxCount();
        restTestClient
                .delete()
                .uri("/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(outboxCount()).as("successful delete").isEqualTo(beforeDelete + 1);

        long beforeFailedAdd = outboxCount();
        restTestClient
                .post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of()))
                .exchange()
                .expectStatus()
                .isBadRequest();
        assertThat(outboxCount()).as("failed add").isEqualTo(beforeFailedAdd);
    }

    @Test
    void readAndBrowseAProjectComputeRollupsFromItsTasksWithNothingStored() {
        BusinessObjectResponse project = create("Project", Map.of("title", List.of("Phase 4 rollup")));
        String projectId = project.id().toString();

        // openTaskCount counts Tasks with status != "done": two of the three below qualify.
        // totalEstimatedHours sums estimatedHours over every Task regardless of status: 3+5+2=10.
        createTask(
                Map.of(
                        "title", List.of("Done task"),
                        "status", "done",
                        "estimatedHours", 3,
                        "belongsToProject", projectId));
        createTask(
                Map.of(
                        "title", List.of("Open task"),
                        "status", "open",
                        "estimatedHours", 5,
                        "belongsToProject", projectId));
        createTask(
                Map.of(
                        "title", List.of("In-progress task"),
                        "status", "in-progress",
                        "estimatedHours", 2,
                        "belongsToProject", projectId));

        JsonNode read = fetchAsJson("/objects/Project/" + projectId);
        assertThat(read.get("properties").get("openTaskCount").asInt()).isEqualTo(2);
        assertThat(read.get("properties").get("totalEstimatedHours").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(10));

        JsonNode page = fetchAsJson("/objects/Project?page=0&size=200");
        JsonNode browsedItem = null;
        for (JsonNode item : page.get("items")) {
            if (item.get("id").asText().equals(projectId)) {
                browsedItem = item;
            }
        }
        assertThat(browsedItem).as("created project appears in browse").isNotNull();
        assertThat(browsedItem.get("properties").get("openTaskCount").asInt()).isEqualTo(2);
        assertThat(browsedItem.get("properties").get("totalEstimatedHours").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(10));

        // Prove nothing is stored: the derived properties' local names must not appear anywhere in
        // the row's raw JSONB, since they are computed on read rather than written by ValueCoercer.
        String propsJson =
                jdbcClient
                        .sql("SELECT props::text FROM sq_object WHERE id = ?")
                        .param(UUID.fromString(projectId))
                        .query(String.class)
                        .single();
        assertThat(propsJson).doesNotContain("openTaskCount").doesNotContain("totalEstimatedHours");
    }

    @Test
    void putWithADerivedPropertyReturns400NamingIt() {
        BusinessObjectResponse project = create("Project", Map.of("title", List.of("Immutable rollup")));

        Map<String, Object> updateBody =
                Map.of(
                        "version",
                        1,
                        "properties",
                        Map.of("title", List.of("Immutable rollup"), "openTaskCount", 5));

        byte[] responseBody =
                restTestClient
                        .put()
                        .uri("/objects/Project/" + project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(updateBody)
                        .exchange()
                        .expectStatus()
                        .isBadRequest()
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                        .expectBody()
                        .returnResult()
                        .getResponseBody();

        JsonNode body = readJson(responseBody);
        assertThat(body.get("source").asText()).isEqualTo("structural");

        JsonNode violation = null;
        for (JsonNode candidate : body.get("violations")) {
            if ("openTaskCount".equals(candidate.get("property").asText())) {
                violation = candidate;
            }
        }
        assertThat(violation).as("violation naming openTaskCount").isNotNull();
        assertThat(violation.get("propertyIri").asText())
                .isEqualTo("https://sequeless.dev/ns/ref#openTaskCount");
        assertThat(violation.get("message").asText())
                .contains("is a derived property and cannot be set directly");
    }

    private JsonNode fetchAsJson(String uri) {
        return readJson(
                restTestClient
                        .get()
                        .uri(uri)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody());
    }

    private JsonNode readJson(byte[] responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private long outboxCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM sq_outbox").query(Long.class).single();
    }

    private BusinessObjectResponse createTask(Map<String, Object> properties) {
        return create("Task", properties);
    }

    private BusinessObjectResponse createPerson(Map<String, Object> properties) {
        return create("Person", properties);
    }

    private BusinessObjectResponse create(String type, Map<String, Object> properties) {
        Map<String, Object> body = Map.of("properties", properties);
        return restTestClient
                .post()
                .uri("/objects/" + type)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
