package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port, wired to its shipped {@code
 * application.yaml} defaults, and proves {@code /api/objects} end to end against a real Postgres
 * container: create, read, browse, update (both the happy path and its two precondition failure
 * modes), and delete.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason {@code TypesEndToEndTest}
 * does: {@code sequeless.persistence.adapter=postgres} is baked into {@code application.yaml}, so
 * this context needs a reachable Postgres regardless of which endpoint a given test exercises.
 *
 * <p>Every test creates its own {@code Task} rather than sharing fixtures across tests, since the
 * shared container/context persists rows across the whole test class.
 *
 * <p>{@code sequeless.persistence.adapter=postgres} is set explicitly via {@code properties} —
 * already {@code application.yaml}'s own default, so functionally a no-op — purely so this class's
 * {@code @SpringBootTest} signature differs from {@code TypesEndToEndTest}'s bare {@code
 * @SpringBootTest(webEnvironment = RANDOM_PORT)}. Without some distinguishing property, Spring's
 * test context cache treats the two as the same {@code MergedContextConfiguration} (the
 * {@code @DynamicPropertySource} method they both inherit from {@link PostgresTestcontainersSupport}
 * is the same {@code Method} either way, so it does not by itself distinguish them) and reuses
 * whichever context was built first — including its {@code DataSource}, still pointed at that
 * earlier test class's now-stopped Testcontainers container, since {@code @Testcontainers} stops a
 * static {@code @Container} field's container after the last test in its owning class. {@code
 * WhoAmIEndToEndTest} and {@code ReasonerSwitchEndToEndTest} already avoid this the same way, each
 * with their own distinguishing {@code properties} value.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "sequeless.persistence.adapter=postgres")
class ObjectsEndToEndTest extends PostgresTestcontainersSupport {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createsReadsAndListsATask() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Write plan")));

        assertThat(created.type()).isEqualTo("Task");
        assertThat(created.version()).isEqualTo(1L);
        assertThat(created.properties()).containsEntry("title", List.of("Write plan"));
        assertThat(created.audit().createdBy()).isEqualTo("anonymous");

        BusinessObjectResponse read = restTestClient
                .get()
                .uri("/api/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("ETag", "\"1\"")
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(read).isEqualTo(created);

        JsonNode page = fetchAsJson("/api/objects/Task?page=0&size=20");
        assertThat(page.get("page").asInt()).isEqualTo(0);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalItems").asLong()).isGreaterThanOrEqualTo(1L);
        boolean present = false;
        for (JsonNode item : page.get("items")) {
            if (item.get("id").asText().equals(created.id().toString())) {
                present = true;
            }
        }
        assertThat(present).as("created task appears in browse").isTrue();
    }

    /**
     * {@code POST /api/objects/Task} answers 201 with a {@code Location} header naming the created
     * object's own {@code /api}-prefixed resource path, plus the initial {@code ETag}.
     *
     * <p><b>Why the prefix is asserted here, not just the request path.</b> {@code ObjectsController}
     * used to hard-code the header as {@code URI.create("/objects/" + type + "/" + id)}. The
     * {@code /api} prefix is applied by {@code ApiPathPrefixConfiguration}'s {@code
     * PathMatchConfigurer#addPathPrefix} scoped to {@code org.sequeless.app.rest} ({@code
     * sequeless.api.base-path}), which changes which request paths route to a controller but never
     * touches a string the controller builds itself — so the hard-coded form kept advertising the
     * old, now-unrouted {@code /objects/...} path. The header must instead be derived from the
     * request path the object was actually created through, so it follows whatever prefix is
     * configured.
     *
     * <p><b>Why it must stay a bare path (no scheme/host).</b> The Angular dev-server proxy
     * ({@code sequeless-ui}) forwards {@code /api} to this app and passes {@code Location} through
     * untouched; an absolute {@code http://localhost:<app-port>/...} URL would point the browser
     * past the proxy at the backend's own origin. A root-relative path resolves against whichever
     * origin the client actually spoke to.
     */
    @Test
    void createReturnsApiPrefixedRelativeLocationAndETag() {
        Map<String, Object> body = Map.of("properties", Map.of("title", List.of("Ship it")));
        var response = restTestClient
                .post()
                .uri("/api/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated();

        BusinessObjectResponse created =
                response.expectBody(BusinessObjectResponse.class).returnResult().getResponseBody();
        assertThat(created).isNotNull();

        response.expectHeader()
                .value("Location", location -> {
                    assertThat(location).isEqualTo("/api/objects/Task/" + created.id());
                    URI locationUri = URI.create(location);
                    assertThat(locationUri.getScheme()).as("Location scheme").isNull();
                    assertThat(locationUri.getHost()).as("Location host").isNull();
                })
                .expectHeader()
                .valueEquals("ETag", "\"1\"");
    }

    @Test
    void updateWithCorrectVersionSucceeds() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Original title")));

        Map<String, Object> updateBody = Map.of("version", 1, "properties", Map.of("title", List.of("Updated title")));

        BusinessObjectResponse updated = restTestClient
                .put()
                .uri("/api/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("ETag", "\"2\"")
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.properties()).containsEntry("title", List.of("Updated title"));
    }

    @Test
    void updateWithWrongVersionIs409() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Stale me")));

        Map<String, Object> updateBody = Map.of("version", 99, "properties", Map.of("title", List.of("Will not land")));

        restTestClient
                .put()
                .uri("/api/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateBody)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.expectedVersion")
                .isEqualTo(99);
    }

    @Test
    void updateWithNeitherVersionNorIfMatchIs428() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("No version supplied")));

        Map<String, Object> updateBody = Map.of("properties", Map.of("title", List.of("Attempted update")));

        restTestClient
                .put()
                .uri("/api/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateBody)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void updateWithMismatchedBodyAndIfMatchIs400() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Mismatch me")));

        Map<String, Object> updateBody =
                Map.of("version", 1, "properties", Map.of("title", List.of("Attempted update")));

        restTestClient
                .put()
                .uri("/api/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"2\"")
                .body(updateBody)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void updateAcceptsIfMatchAloneWithNoBodyVersion() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("If-Match only")));

        Map<String, Object> updateBody = Map.of("properties", Map.of("title", List.of("Updated via If-Match")));

        restTestClient
                .put()
                .uri("/api/objects/Task/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .body(updateBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("ETag", "\"2\"");
    }

    @Test
    void deleteThenReadIs404AndDisappearsFromBrowse() {
        BusinessObjectResponse created = createTask(Map.of("title", List.of("Delete me")));

        restTestClient
                .delete()
                .uri("/api/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        restTestClient
                .get()
                .uri("/api/objects/Task/" + created.id())
                .exchange()
                .expectStatus()
                .isNotFound();

        JsonNode page = fetchAsJson("/api/objects/Task?page=0&size=200");
        for (JsonNode item : page.get("items")) {
            assertThat(item.get("id").asText()).isNotEqualTo(created.id().toString());
        }
    }

    private BusinessObjectResponse createTask(Map<String, Object> properties) {
        Map<String, Object> body = Map.of("properties", properties);
        return restTestClient
                .post()
                .uri("/api/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private JsonNode fetchAsJson(String uri) {
        byte[] responseBody = restTestClient
                .get()
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
}
