package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Test-first end-to-end specification of the SPA fallback ({@code SpaWebConfiguration} + {@code
 * SpaFallbackResourceResolver} in {@code org.sequeless.app.config}) when NO Angular build is
 * present — the situation of every plain {@code mvn verify} on CI, because the Angular output
 * directory {@code sequeless-app/src/main/resources/static/} is gitignored and only populated by a
 * separate UI build. With the fallback enabled (its default) but no {@code index.html} to fall
 * back to, the application must behave exactly as it did before the UI existed: unknown paths
 * (including {@code /} and client-side deep links) are plain 404s, and the API, Actuator and
 * springdoc keep answering normally.
 *
 * <p>This is the class that guarantees a plain {@code mvn verify} stays green without a UI build:
 * if the fallback ever assumed the index exists (e.g. handed Spring an unreadable resource, or
 * failed at startup when the location is empty), every Java-only build would break. Its
 * resolver-level counterpart is {@code
 * SpaFallbackResourceResolverTest.deepLinkResolvesToNothingWhenTheIndexDoesNotExist}; the
 * with-assets counterpart is {@link SpaFallbackWithAssetsTest}.
 *
 * <p>These tests currently FAIL wherever they depend on the not-yet-implemented configuration
 * (and are written test-first on purpose); do not weaken the assertions, implement the behaviour.
 *
 * <p><b>Why both web-resource locations are pinned to a path that does not exist.</b> On a
 * developer machine {@code src/main/resources/static/} may well hold a real Angular build (it is
 * gitignored, not absent by construction), and Boot would then serve its {@code index.html} as the
 * welcome page at {@code /} — making this test pass or fail depending on whether someone ran the
 * UI build locally. Pointing both {@code spring.web.resources.static-locations} (Boot's own
 * static handler and welcome-page lookup) and {@code sequeless.web.spa.location} (the SPA
 * fallback's index location) at {@code classpath:/sequeless-no-spa-build/}, which nothing
 * provides, reproduces the CI "no UI built" state deterministically everywhere. The fallback itself
 * is left at its default ({@code sequeless.web.spa.enabled=true}), which is the case that matters.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason every other {@code
 * @SpringBootTest} in this module does: {@code sequeless.persistence.adapter=postgres} is baked
 * into {@code application.yaml}, so this context needs a reachable Postgres regardless of which
 * endpoint a given test exercises. See that class's javadoc.
 *
 * <p><b>Context-cache uniqueness.</b> Two {@code @SpringBootTest} classes extending {@code
 * PostgresTestcontainersSupport} must NOT have byte-identical {@code @SpringBootTest} signatures,
 * or Spring's test-context cache reuses a context whose {@code DataSource} points at a
 * Testcontainers container that was already destroyed (see {@code AcceptanceCriteriaIT}'s and
 * {@code ApiPathPrefixTest}'s javadoc on the same point). The two location properties above
 * already make this class's {@code properties} array unique in the module — no other class sets
 * any {@code spring.web.resources.*} or {@code sequeless.web.*} property, and {@link
 * SpaFallbackWithAssetsTest} uses a different location value — so no extra no-op property is
 * needed.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "spring.web.resources.static-locations=classpath:/sequeless-no-spa-build/",
            "sequeless.web.spa.location=classpath:/sequeless-no-spa-build/"
        })
class SpaFallbackWithoutAssetsTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void rootIsNotFoundWhenNoUiHasBeenBuilt() {
        // No index.html anywhere: there is nothing to serve at "/", and the fallback must not
        // invent a response.
        restTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void clientSideDeepLinkIsNotFoundWhenNoUiHasBeenBuilt() {
        // The fallback is enabled by default, so this path passes every guard in
        // SpaFallbackResourceResolver (not reserved, no dot in the last segment); only the missing
        // index keeps it a 404.
        restTestClient
                .get()
                .uri("/ontology/classes/Project")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void apiIsStillServedWhenNoUiHasBeenBuilt() throws Exception {
        String json =
                restTestClient
                        .get()
                        .uri("/api/whoami")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        // Same single-tenant contract ApiPathPrefixTest.whoAmiIsServedUnderTheApiPrefix asserts:
        // registering a /** resource handler must not shadow the annotated controllers.
        // Read as String and parse with a Jackson 2 ObjectMapper, as the sibling end-to-end tests do:
        // Boot 4's RestTestClient converts bodies with Jackson 3 (tools.jackson), which cannot
        // construct the Jackson 2 JsonNode type this module's tests use.
        JsonNode body = new ObjectMapper().readTree(json);
        assertThat(body.get("tenant").asText()).isEqualTo("default");
    }

    @Test
    void actuatorHealthIsStillServedWhenNoUiHasBeenBuilt() {
        restTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void openApiDocumentIsStillServedWhenNoUiHasBeenBuilt() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
