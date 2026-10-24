package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Test-first unit specification of {@code SpaFallbackResourceResolver}: the {@link
 * org.springframework.web.servlet.resource.PathResourceResolver} subclass that {@code
 * SpaWebConfiguration} installs on its {@code /**} resource handler so that the Angular UI's
 * client-side routes (deep links such as {@code /ontology/classes/Project}) are answered with the
 * SPA's {@code index.html} instead of a 404, while every real file, every reserved server prefix
 * ({@code /api}, {@code /actuator}, {@code /swagger-ui}, {@code /v3/api-docs}, {@code /error},
 * {@code /webjars}) and every missing asset still resolve exactly as they would without the
 * fallback.
 *
 * <p>These tests currently FAIL (they do not compile): the resolver does not exist yet. They are
 * written test-first so the decision table below is executable the moment it lands. Do not "fix"
 * them by weakening the assertions; implement the behaviour. The contract, in evaluation order, is:
 *
 * <ol>
 *   <li>a real file found by {@code super.getResource} is returned untouched;
 *   <li>when the fallback is disabled, nothing else is ever returned;
 *   <li>a path equal to a reserved prefix, or nested beneath one ({@code prefix + "/"}), is never
 *       answered with the index — so {@code /api/nope} still 404s as JSON rather than rendering the
 *       UI shell, and {@code /apifoo} (a sibling, not a child) is NOT treated as reserved;
 *   <li>a path whose last segment contains a {@code .} is treated as an asset request and never
 *       answered with the index (the "dot guard", see {@link
 *       #missingAssetIsNotAnsweredWithTheIndexBecauseOfTheDotGuard});
 *   <li>otherwise the index is returned, provided it exists.
 * </ol>
 *
 * <p>Deliberately a plain unit test: no Spring context, no Testcontainers, no mocks (none exist in
 * this repo). It drives the resolver with real {@link FileSystemResource}s over a JUnit {@link
 * TempDir}, which exercises the genuine {@code PathResourceResolver} lookup — including its
 * {@code createRelative}, readability and "is the resource under the location" checks — rather
 * than a stand-in. {@code getResource(String, Resource)} is {@code protected} in {@code
 * PathResourceResolver}, which is why this class lives in {@code org.sequeless.app.config}, the
 * resolver's own package. The end-to-end counterparts are {@code
 * org.sequeless.app.SpaFallbackWithAssetsTest} and {@code
 * org.sequeless.app.SpaFallbackWithoutAssetsTest}.
 */
class SpaFallbackResourceResolverTest {

    private static final String INDEX_MARKER = "SEQUELESS-UNIT-SPA-INDEX";

    private static final String ASSET_CONTENT = "export const unitMarker = 'SEQUELESS-UNIT-SPA-ASSET';";

    /**
     * Mirrors the reserved set {@code SpaWebConfiguration} is specified to pass: the API base path
     * (default {@code /api}, see {@code sequeless.api.base-path} and {@code
     * ApiPathPrefixConfiguration}) plus the unprefixed server-owned paths springdoc, Actuator,
     * Boot's error controller and webjars live under.
     */
    private static final List<String> RESERVED_PREFIXES =
            List.of("/api", "/actuator", "/swagger-ui", "/v3/api-docs", "/error", "/webjars");

    @TempDir Path webRoot;

    /**
     * The resolver's {@code location}. The trailing slash is load-bearing: {@code
     * FileSystemResource.createRelative} applies the relative path against the parent of a
     * slash-less path, so without it every lookup would resolve one directory too high.
     */
    private Resource location;

    private Resource index;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(webRoot.resolve("index.html"), "<app-root>" + INDEX_MARKER + "</app-root>");
        Files.createDirectories(webRoot.resolve("assets"));
        Files.writeString(webRoot.resolve("assets/app-abc123.js"), ASSET_CONTENT);
        location = new FileSystemResource(webRoot.toString() + "/");
        index = new FileSystemResource(webRoot.resolve("index.html"));
    }

    private static SpaFallbackResourceResolver resolver(Resource index, boolean enabled) {
        return new SpaFallbackResourceResolver(index, RESERVED_PREFIXES, enabled);
    }

    @Test
    void existingFileIsReturnedAsIsRatherThanTheIndex() throws IOException {
        Resource resolved = resolver(index, true).getResource("assets/app-abc123.js", location);

        // Rule 1: the fallback must never shadow a real file — hashed bundles, favicon, etc. are
        // served verbatim.
        assertThat(resolved).isNotNull();
        assertThat(resolved.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(ASSET_CONTENT);
    }

    @Test
    void existingFileIsStillReturnedWhenTheFallbackIsDisabled() throws IOException {
        Resource resolved = resolver(index, false).getResource("assets/app-abc123.js", location);

        // Disabling the fallback turns the resolver back into a plain PathResourceResolver; it must
        // not stop static files from being served altogether.
        assertThat(resolved).isNotNull();
        assertThat(resolved.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(ASSET_CONTENT);
    }

    @Test
    void missingExtensionlessDeepLinkFallsBackToTheIndex() throws IOException {
        Resource resolved = resolver(index, true).getResource("ontology/classes/Project", location);

        // Rule 5: an Angular client-side route reloaded in the browser (or opened from a bookmark)
        // reaches the server as a plain path; answering it with index.html lets the Angular router
        // take over instead of the user seeing a 404.
        assertThat(resolved).isNotNull();
        assertThat(resolved.getContentAsString(StandardCharsets.UTF_8)).contains(INDEX_MARKER);
    }

    @Test
    void reservedPrefixExactMatchIsNotAnsweredWithTheIndex() throws IOException {
        // Rule 3, exact match: "/" + "api" equals the reserved "/api".
        assertThat(resolver(index, true).getResource("api", location)).isNull();
    }

    @Test
    void pathNestedBeneathTheApiPrefixIsNotAnsweredWithTheIndex() throws IOException {
        // Rule 3, nested: an unknown or mistyped API URL must 404 (as problem JSON from the error
        // controller), never return 200 text/html — an API client receiving the UI shell would
        // fail with a confusing JSON parse error instead of a clear 404.
        assertThat(resolver(index, true).getResource("api/whoami", location)).isNull();
    }

    @Test
    void siblingOfReservedPrefixWithoutSlashBoundaryIsNotReserved() throws IOException {
        Resource resolved = resolver(index, true).getResource("apifoo", location);

        // Rule 3 matches on a segment boundary (prefix + "/"), not a raw string prefix: "/apifoo"
        // is a legitimate client-side route, not part of the API, so it must get the index.
        assertThat(resolved).isNotNull();
        assertThat(resolved.getContentAsString(StandardCharsets.UTF_8)).contains(INDEX_MARKER);
    }

    @Test
    void actuatorPathIsNotAnsweredWithTheIndex() throws IOException {
        // Actuator is unprefixed (see ApiPathPrefixTest.swaggerUiAndActuatorAreNotPrefixed), so it
        // needs its own reserved entry; an unexposed endpoint must stay a 404 for probes/tooling.
        assertThat(resolver(index, true).getResource("actuator/health", location)).isNull();
    }

    @Test
    void missingAssetIsNotAnsweredWithTheIndexBecauseOfTheDotGuard() throws IOException {
        // Rule 4, the dot guard: after a redeploy, a browser holding a stale index.html asks for a
        // bundle hash that no longer exists. If the fallback answered that with index.html, the
        // browser would get HTML where it expected JavaScript and surface the baffling
        // "Unexpected token '<'" / strict-MIME-type error instead of an honest 404.
        assertThat(resolver(index, true).getResource("assets/app-deadbeef.js", location)).isNull();
    }

    @Test
    void deepLinkIsNotAnsweredWithTheIndexWhenTheFallbackIsDisabled() throws IOException {
        // Rule 2: sequeless.web.spa.enabled=false must switch the fallback off completely.
        assertThat(resolver(index, false).getResource("ontology/classes/Project", location)).isNull();
    }

    @Test
    void apiClientAskingForJsonGetsNoIndexForADeepLink() throws IOException {
        // The Accept rule: a client that names neither text/html nor */* is an API client and
        // must get the 404, not the UI shell, even for a path the fallback would otherwise answer.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        assertThat(resolver(index, true)
                        .resolveResource(request, "ontology/classes/Project", List.of(location), null))
                .isNull();
    }

    @Test
    void browserRequestStillGetsTheIndexForADeepLink() throws IOException {
        // A browser navigation names text/html; a request with no Accept header at all is treated
        // as a browser too, so callers that send none keep getting the index.
        MockHttpServletRequest browser = new MockHttpServletRequest();
        browser.addHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,*/*;q=0.8");
        MockHttpServletRequest noAccept = new MockHttpServletRequest();

        for (MockHttpServletRequest request : List.of(browser, noAccept)) {
            Resource resolved =
                    resolver(index, true)
                            .resolveResource(request, "ontology/classes/Project", List.of(location), null);

            assertThat(resolved).isNotNull();
            assertThat(resolved.getContentAsString(StandardCharsets.UTF_8)).contains(INDEX_MARKER);
        }
    }

    @Test
    void deepLinkResolvesToNothingWhenTheIndexDoesNotExist() throws IOException {
        // A FileSystemResource over a path that was never written: exists() is false. (A
        // ByteArrayResource would not do here — its exists() is always true.) This is the
        // "no UI built" situation SpaFallbackWithoutAssetsTest covers end to end: the resolver
        // must return null so the request 404s, not hand Spring a resource it cannot read.
        Resource absentIndex = new FileSystemResource(webRoot.resolve("does-not-exist/index.html"));

        assertThat(resolver(absentIndex, true).getResource("ontology/classes/Project", location))
                .isNull();
    }
}
