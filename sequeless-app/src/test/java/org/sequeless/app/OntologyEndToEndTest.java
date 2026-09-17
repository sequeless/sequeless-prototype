package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port, wired to its shipped {@code
 * application.yaml} defaults, and proves {@code /ontology} end to end against a real Postgres
 * container: export, a consistent replace-import that is reflected both in a later export and in
 * {@code GET /types}, and a rejected import for an inconsistent document.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason {@code ObjectsEndToEndTest}
 * does: {@code sequeless.persistence.adapter=postgres} is baked into {@code application.yaml}, so
 * this context needs a reachable Postgres regardless of which endpoint a given test exercises.
 *
 * <p>{@code sequeless.validation.adapter=shacl} is set explicitly via {@code properties} — already
 * {@code application.yaml}'s own default, so functionally a no-op — purely so this class's {@code
 * @SpringBootTest} signature differs from every other {@code @SpringBootTest} class in this module.
 * Without some distinguishing property, Spring's test context cache would treat two bare-looking
 * signatures as the same {@code MergedContextConfiguration} and reuse a context built against an
 * earlier test class's now-stopped Testcontainers container; see {@code ObjectsEndToEndTest}'s
 * javadoc for the full explanation.
 *
 * <p>The import-then-verify test appends a new class to the currently exported ontology rather than
 * asserting an exact type count, since {@code importOntologyOfAnInconsistentDocumentIs422} and any
 * other test in this class share one Spring context (and so one in-memory {@code OntologyPort}
 * state) across the whole class; a failed import never swaps that state (see {@code
 * JenaOntologyPort#importDocument}), so it cannot affect the type count, but test execution order is
 * otherwise not guaranteed, and this test must not assume it runs first.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "sequeless.validation.adapter=shacl")
class OntologyEndToEndTest extends PostgresTestcontainersSupport {

    private static final String INCONSISTENT_TURTLE =
            """
            @prefix ex:  <https://sequeless.dev/ns/ref#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            <https://sequeless.dev/ns/ref-inconsistent>
                a owl:Ontology .

            ex:Machine
                a owl:Class ;
                owl:disjointWith ex:Person .

            ex:Person
                a owl:Class .

            ex:Cyborg
                a owl:Class ;
                rdfs:subClassOf ex:Machine ,
                    ex:Person .

            ex:c1
                a ex:Cyborg .
            """;

    @LocalServerPort private int port;
    @Autowired private DataSource sequelessPersistenceDataSource;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void exportReturnsTurtleContainingTheReferenceTypes() {
        String turtle = exportOntology();

        assertThat(turtle).contains("ex:Task", "ex:Project", "ex:Person", "ex:WorkItem", "ex:Deliverable");
    }

    @Test
    void importOfAModifiedOntologyIsAcceptedAndPersistedAndVisibleAfterwards() {
        JdbcClient jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
        long rowsBefore =
                jdbcClient.sql("SELECT COUNT(*) FROM sq_ontology_document WHERE tenant_id = 'default'")
                        .query(Long.class)
                        .single();

        String current = exportOntology();
        String modified =
                current
                        + "\nex:Milestone a owl:Class ; rdfs:subClassOf ex:Deliverable ; sq:label \"Milestone\" .\n";

        byte[] responseBody =
                restTestClient
                        .post()
                        .uri("/ontology")
                        .contentType(MediaType.valueOf("text/turtle"))
                        .body(modified)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                        .expectBody()
                        .jsonPath("$.accepted")
                        .isEqualTo(true)
                        .jsonPath("$.consistent")
                        .isEqualTo(true)
                        .returnResult()
                        .getResponseBody();
        assertThat(responseBody).isNotNull();

        // Persisted to sq_ontology_document via the Postgres-backed OntologyDocumentStore, not just
        // swapped in-memory: a new active row for the default tenant, one more than before.
        long rowsAfter =
                jdbcClient.sql("SELECT COUNT(*) FROM sq_ontology_document WHERE tenant_id = 'default'")
                        .query(Long.class)
                        .single();
        assertThat(rowsAfter).isEqualTo(rowsBefore + 1);

        boolean activeRowHasMilestone =
                jdbcClient.sql(
                                "SELECT content FROM sq_ontology_document "
                                        + "WHERE tenant_id = 'default' AND active")
                        .query(String.class)
                        .single()
                        .contains("Milestone");
        assertThat(activeRowHasMilestone).isTrue();

        restTestClient
                .get()
                .uri("/types")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[?(@.name == 'Milestone')]")
                .exists();

        assertThat(exportOntology()).contains("Milestone");
    }

    @Test
    void importOfAnInconsistentDocumentIs422() {
        restTestClient
                .post()
                .uri("/ontology")
                .contentType(MediaType.valueOf("text/turtle"))
                .body(INCONSISTENT_TURTLE)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.consistent")
                .isEqualTo(false)
                .jsonPath("$.issues[0].message")
                .value(message -> assertThat((String) message).contains("Cyborg"));
    }

    private String exportOntology() {
        byte[] body =
                restTestClient
                        .get()
                        .uri("/ontology")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.valueOf("text/turtle"))
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(body).isNotNull();
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
}
