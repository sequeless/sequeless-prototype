package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Test-first end-to-end specification of the SPA fallback ({@code SpaWebConfiguration} + {@code
 * SpaFallbackResourceResolver} in {@code org.sequeless.app.config}) when an Angular build IS
 * present: {@code /} and every client-side deep link are answered with the SPA's {@code
 * index.html}, real bundles are served verbatim, and — the point of the resolver's guards — a
 * missing bundle, an unknown API URL, Actuator and springdoc are never answered with the UI shell.
 *
 * <p>These tests currently FAIL: the fallback is not implemented yet. They are written test-first
 * on purpose; do not weaken the assertions, implement the behaviour. The resolver's decision table
 * is specified in isolation by {@code
 * org.sequeless.app.config.SpaFallbackResourceResolverTest}; the "no UI built" counterpart is
 * {@link SpaFallbackWithoutAssetsTest}.
 *
 * <p><b>The fixture build.</b> Instead of depending on a real Angular build (gitignored, absent on
 * CI), this class points the fallback at {@code src/test/resources/test-spa/}: an {@code
 * index.html} carrying the marker {@value #INDEX_MARKER} and a fake hashed bundle {@code
 * assets/app-abc123.js} carrying {@value #ASSET_MARKER}. The markers let each assertion tell "the
 * SPA index came back" apart from any other body. Both {@code sequeless.web.spa.location} (where
 * the fallback's {@code index.html} is resolved from, together with the default {@code
 * sequeless.web.spa.index=index.html}) and {@code spring.web.resources.static-locations} (Boot's
 * static locations, which the fallback's {@code /**} handler serves real files from, and which
 * Boot's welcome-page lookup for {@code /} also uses) are set to {@code classpath:/test-spa/}, so
 * a real Angular build a developer may have left in {@code src/main/resources/static/} can never
 * leak into, or mask a failure in, these assertions.
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
 * {@code ApiPathPrefixTest}'s javadoc on the same point). The {@code classpath:/test-spa/}
 * location properties already make this class's {@code properties} array unique in the module —
 * no other class sets {@code spring.web.resources.*} or {@code sequeless.web.*}, and {@link
 * SpaFallbackWithoutAssetsTest} points the same keys at a different (non-existent) location — so
 * no extra no-op property is needed.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "spring.web.resources.static-locations=classpath:/test-spa/",
            "sequeless.web.spa.location=classpath:/test-spa/"
        })
class SpaFallbackWithAssetsTest extends PostgresTestcontainersSupport {

    /** Present only in {@code src/test/resources/test-spa/index.html}. */
    static final String INDEX_MARKER = "SEQUELESS-TEST-SPA-INDEX";

    /** Present only in {@code src/test/resources/test-spa/assets/app-abc123.js}. */
    static final String ASSET_MARKER = "SEQUELESS-TEST-SPA-ASSET";

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private String getSpaIndexAt(String path) {
        return restTestClient
                .get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void rootServesTheSpaIndex() {
        // "/" must be served from sequeless.web.spa.location + sequeless.web.spa.index, i.e. the
        // same index the deep links get, not some other welcome page.
        assertThat(getSpaIndexAt("/")).contains(INDEX_MARKER);
    }

    @Test
    void clientSideDeepLinkServesTheSpaIndex() {
        // A reload or bookmark of an Angular route: no such file exists, the path is not reserved
        // and its last segment has no dot, so SpaFallbackResourceResolver answers with the index
        // and the Angular router renders the page client-side.
        assertThat(getSpaIndexAt("/ontology/classes/Project")).contains(INDEX_MARKER);
    }

    @Test
    void multiSegmentObjectEditDeepLinkServesTheSpaIndex() {
        // Deeper route with an id segment: the fallback must not depend on path depth.
        assertThat(getSpaIndexAt("/objects/Project/some-id/edit")).contains(INDEX_MARKER);
    }

    @Test
    void existingHashedBundleIsServedVerbatim() {
        String body = restTestClient
                .get()
                .uri("/assets/app-abc123.js")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.valueOf("text/javascript"))
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains(ASSET_MARKER).doesNotContain(INDEX_MARKER);
    }

    @Test
    void missingHashedBundleIsNotFoundAndNeverTheSpaIndex() {
        // The highest-value assertion in this class. After a redeploy, a browser still holding the
        // previous index.html requests bundle hashes that no longer exist. If the fallback
        // answered those with index.html (200, text/html), the browser would try to execute HTML
        // as a module script and report the baffling "Unexpected token '<'" / "strict MIME type
        // checking" error, hiding the real problem (a stale build). The resolver's dot guard (last
        // path segment contains '.') must turn this into an honest 404 instead — see
        // SpaFallbackResourceResolverTest.missingAssetIsNotAnsweredWithTheIndexBecauseOfTheDotGuard.
        String body = restTestClient
                .get()
                .uri("/assets/app-deadbeef.js")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).as("404 body for a missing bundle").doesNotContain(INDEX_MARKER);
    }

    @Test
    void unknownApiPathIsNotFoundAndNeverTheSpaIndex() {
        // /api is reserved (sequeless.api.base-path, see ApiPathPrefixConfiguration): an API client
        // hitting a wrong URL must get a 404 from the error handling, not a 200 HTML page it would
        // then fail to parse as JSON.
        String body = restTestClient
                .get()
                .uri("/api/nope")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).as("404 body for an unknown API path").doesNotContain(INDEX_MARKER);
    }

    @Test
    void actuatorHealthIsStillServedAsJsonNotTheSpaIndex() {
        // Actuator's health response uses its own vendor media type
        // (application/vnd.spring-boot.actuator.v3+json), which is not "compatible with"
        // application/json under MimeType's rules, so the body — not the header — is what proves
        // this is Actuator's JSON rather than the UI shell.
        String body = restTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"status\"").doesNotContain(INDEX_MARKER);
    }

    @Test
    void openApiDocumentIsStillServedAsJsonNotTheSpaIndex() {
        String body = restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"openapi\"").doesNotContain(INDEX_MARKER);
    }
}
