package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.List;
import java.util.Map;
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
