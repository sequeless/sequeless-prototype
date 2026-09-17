package org.sequeless.app;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Proves plan.md §9's ontology-persistence-across-restart acceptance criterion: an ontology
 * imported via {@code POST /ontology} is still visible through {@code GET /types} after the
 * importing application context is closed and a brand-new one is started against the same
 * Postgres — even when that second context's {@code sequeless.ontology.source} points at a file
 * that does not exist, which would fail startup if the new context ever fell back to reading it.
 * This proves the new type is served from {@code sq_ontology_document}, not from any file on disk
 * (F43: {@code POST /ontology} persists via {@code PostgresOntologyDocumentStore.activate} before
 * ever swapping the in-memory model, and {@code JenaOntologyPort#fromStore} only reads {@code
 * sequeless.ontology.source} when the store has no active document yet for the tenant).
 *
 * <p>Deliberately does not use {@code @SpringBootTest}: that annotation caches and reuses a single
 * {@code ApplicationContext} per unique configuration, which is the opposite of what this test
 * needs — two genuinely separate {@code ApplicationContext}s, started and closed in sequence,
 * against the one shared {@link PostgresTestcontainersSupport#POSTGRES} container. Follows {@code
 * InconsistentOntologyStartupTest}/{@code PortBindingStartupFailureTest}'s manually-driven {@code
 * SpringApplicationBuilder} pattern, but with {@code WebApplicationType.SERVLET} and {@code
 * server.port=0} (rather than {@code NONE}) since this test needs to make real HTTP calls against
 * each context in turn, and command-line arguments rather than {@code
 * SpringApplicationBuilder.properties(...)} for the same reason those two tests use them: the
 * latter is the lowest-precedence property source and would be silently overridden by {@code
 * application.yaml}'s own defaults.
 */
class OntologyPersistenceAcrossRestartIT extends PostgresTestcontainersSupport {

    @Test
    void importedOntologySurvivesRestartWithoutRereadingAnyFile() {
        ConfigurableApplicationContext contextA =
                new SpringApplicationBuilder(SequelessApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .run(
                                "--server.port=0",
                                "--sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                                "--sequeless.persistence.username=" + POSTGRES.getUsername(),
                                "--sequeless.persistence.password=" + POSTGRES.getPassword());
        try {
            RestTestClient clientA = clientFor(contextA);

            String currentTurtle =
                    new String(
                            clientA
                                    .get()
                                    .uri("/ontology")
                                    .exchange()
                                    .expectStatus()
                                    .isOk()
                                    .expectBody()
                                    .returnResult()
                                    .getResponseBody(),
                            StandardCharsets.UTF_8);

            String modified =
                    currentTurtle
                            + "\nex:Milestone a owl:Class ; rdfs:subClassOf ex:Deliverable ; sq:label \"Milestone\" .\n";

            clientA
                    .post()
                    .uri("/ontology")
                    .contentType(MediaType.valueOf("text/turtle"))
                    .body(modified)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.accepted")
                    .isEqualTo(true)
                    .jsonPath("$.consistent")
                    .isEqualTo(true);
        } finally {
            contextA.close();
        }

        ConfigurableApplicationContext contextB =
                new SpringApplicationBuilder(SequelessApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .run(
                                "--server.port=0",
                                "--sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                                "--sequeless.persistence.username=" + POSTGRES.getUsername(),
                                "--sequeless.persistence.password=" + POSTGRES.getPassword(),
                                "--sequeless.ontology.source=classpath:does-not-exist.ttl");
        try {
            RestTestClient clientB = clientFor(contextB);

            clientB
                    .get()
                    .uri("/types")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$[?(@.name == 'Milestone')]")
                    .exists();
        } finally {
            contextB.close();
        }
    }

    private static RestTestClient clientFor(ConfigurableApplicationContext context) {
        int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
