package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.testkit.Fixtures;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Test-first specification of the API path-prefix behaviour: all four controllers in {@code
 * org.sequeless.app.rest} ({@code OntologyController}, {@code TypesController}, {@code
 * ObjectsController}, {@code WhoAmIController}) are served under a configurable path prefix whose
 * default is {@code /api} — {@code GET /api/whoami} instead of {@code GET /whoami} — while
 * springdoc ({@code /v3/api-docs}, {@code /swagger-ui.html}) and Actuator ({@code
 * /actuator/health}) deliberately stay unprefixed, because the prefix is applied by a
 * package-scoped predicate over {@code org.sequeless.app.rest} rather than to every {@code
 * @RestController} in the application.
 *
 * <p>These tests currently FAIL: the prefixing behaviour they assert is not implemented yet. They
 * are written test-first on purpose so that when the later implementation lands, the full contract
 * (prefixed endpoints resolve; legacy unprefixed paths are gone; encoded-IRI, bracket-filter,
 * springdoc, and actuator behaviour are preserved under the prefix) is already executable and
 * enforced. Do not "fix" these tests by weakening the assertions; implement the behaviour.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason every other {@code
 * @SpringBootTest} in this module does: {@code sequeless.persistence.adapter=postgres} is baked
 * into {@code application.yaml}, so this context needs a reachable Postgres regardless of which
 * endpoint a given test exercises. See that class's javadoc.
 *
 * <p><b>Why this class's {@code @SpringBootTest} carries a distinguishing property.</b> Two
 * {@code @SpringBootTest} classes extending {@code PostgresTestcontainersSupport} must NOT have
 * byte-identical {@code @SpringBootTest} signatures (same {@code webEnvironment} + same
 * {@code properties} array), or Spring's test-context cache reuses a context whose {@code
 * DataSource} points at a Testcontainers container that was already destroyed, producing spurious
 * connection failures. Every sibling class adds a distinguishing property for exactly this reason
 * (see {@code AcceptanceCriteriaIT}'s and {@code OntologyEndToEndTest}'s javadoc on the same
 * point). This class therefore pins {@code sequeless.expression.adapter=jexl} — already
 * {@code application.yaml}'s own default, so functionally a no-op — as its single distinguishing
 * property. That single-property signature is unique in this module: verified by grepping every
 * {@code @SpringBootTest} under {@code sequeless-app/src/test}, the only other classes naming
 * {@code sequeless.expression.adapter=jexl} do so inside a four-property array together with
 * {@code sequeless.persistence.adapter=postgres}, {@code sequeless.automation.adapter=inprocess},
 * and a relay poll-interval override, so none of them collides with this signature.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "sequeless.expression.adapter=jexl")
class ApiPathPrefixTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void whoAmiIsServedUnderTheApiPrefix() throws Exception {
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

        // WhoAmIController is single-tenant in Phase 0: tenant is always TenantId.DEFAULT ("default"),
        // as WhoAmIEndToEndTest asserts for the unprefixed URL this test replaces.
        // Read as String and parse with a Jackson 2 ObjectMapper, as the sibling end-to-end tests do:
        // Boot 4's RestTestClient converts bodies with Jackson 3 (tools.jackson), which cannot
        // construct the Jackson 2 JsonNode type this module's tests use.
        JsonNode body = new ObjectMapper().readTree(json);
        assertThat(body.get("tenant").asText()).isEqualTo("default");
    }

    @Test
    void legacyUnprefixedPathsAreNoLongerServed() {
        // The prefix must be a move, not an addition: the old URLs must 404, otherwise clients
        // (and the existing tests' URLs) keep working invisibly against the pre-prefix layout.
        // The request asks for JSON like an API client would: with the UI packaged, a browser
        // request to an unknown path gets the SPA shell, which is deliberately not a 404.
        restTestClient
                .get()
                .uri("/whoami")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound();

        restTestClient
                .get()
                .uri("/types")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void fullTypeIriWithEncodedSlashesStillResolvesUnderThePrefix() {
        // This assertion exists to prove TomcatEncodedSlashConfiguration still works when the
        // mapping carries a path prefix: /api/types/{...} must tolerate a fully-qualified IRI
        // containing encoded '/' the same way the unprefixed /types/{...} did (see
        // TypesEndToEndTest.resolvesByEncodedIri for the unprefixed contract).
        String encodedIri = URLEncoder.encode(Fixtures.TASK_IRI, StandardCharsets.UTF_8);
        // Pre-built java.net.URI, not a "/api/types/{...}" template string: RestTestClient's
        // uri(String, Object...) overload runs the string through UriComponentsBuilder's own
        // encoding pass, which would re-encode the '%' this test just produced (turning
        // "%23Task" into "%2523Task") and defeat the whole point of the encoded-IRI branch.
        // uri(URI) sends the given URI exactly as built, with no second encoding pass.
        URI encodedIriUri = URI.create("http://localhost:" + port + "/api/types/" + encodedIri);

        restTestClient
                .get()
                .uri(encodedIriUri)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void bracketFilterParametersSurvivePercentEncoding() {
        // Proves Angular's default percent-encoding of '[' and ']' in query keys still reaches
        // ObjectsController's FILTER_KEY regex intact under the prefix: the server must decode the
        // %5B/%5D back into filter[openTaskCount][gte] keys before the regex runs. If decoding or
        // mapping broke under the prefix, ObjectsController would answer 400 with an
        // 'invalid-query' problem+json (its filter-key parse failure), not 200 — so this method
        // deliberately expects 200, not 400, to pin down exactly that distinction.
        // Pre-built java.net.URI for the same reason as resolvesByEncodedIri: uri(String, Object...)
        // would run the %5B/%5D through UriComponentsBuilder's own encoding pass and double-encode
        // them, defeating what this test exists to verify.
        URI filteredBrowseUri =
                URI.create("http://localhost:" + port + "/api/objects/Project"
                        + "?filter%5BopenTaskCount%5D%5Bgte%5D=0");

        restTestClient
                .get()
                .uri(filteredBrowseUri)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void generatedOpenApiDocumentUsesThePrefixedPaths() {
        String body =
                restTestClient
                        .get()
                        .uri("/v3/api-docs")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        // springdoc must advertise the endpoints where the implementation will actually serve
        // them: every path key in the generated document carries the /api prefix.
        assertThat(body).contains("/api/types/{nameOrIri}");

        // The unprefixed path must not survive as a key. Checking !contains("/types/{nameOrIri}")
        // would false-positive on the prefixed form itself, so anchor on the JSON quote that
        // precedes a path key: "\"/types/{nameOrIri}" is a key starting immediately after a quote,
        // whereas the prefixed document only contains "\"/api/types/{nameOrIri}" — where the quote
        // is followed by "/api", never directly by "/types".
        assertThat(body).doesNotContain("\"/types/{nameOrIri}");
    }

    @Test
    void swaggerUiAndActuatorAreNotPrefixed() {
        // These must stay where they are: the prefix is applied by a package-scoped predicate over
        // org.sequeless.app.rest, not to every @RestController — springdoc and Actuator's
        // controllers live elsewhere and must keep their conventional unprefixed locations, so
        // tooling that hardcodes /v3/api-docs and /actuator/health keeps working. (Actuator
        // exposes only 'health'.)
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk();

        restTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
