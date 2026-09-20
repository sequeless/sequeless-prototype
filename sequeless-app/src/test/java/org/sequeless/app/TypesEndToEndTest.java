package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.ErrorResponse;
import org.sequeless.app.rest.OntologyReportResponse;
import org.sequeless.app.rest.TypeSummaryResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.testkit.Fixtures;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port, wired to its shipped {@code
 * application.yaml} defaults ({@code sequeless.ontology.adapter=jena}, {@code reasoner=owl}, {@code
 * source=classpath:ontology/reference.ttl}), and proves the Ontology phase's headline
 * acceptance criterion end to end: {@code GET /types}, {@code GET /types/{nameOrIri}}, and {@code
 * POST /types/reload} render the reference ontology correctly over HTTP.
 *
 * <p>{@link TypeSummaryResponse} and {@link OntologyReportResponse} are plain records with no
 * sealed-interface-typed component, so {@link RestTestClient} can deserialize them directly. {@code
 * TypeDetailResponse}, by contrast, carries a {@code List<PropertyResponse>} — {@code
 * PropertyResponse} is sealed with no {@code @JsonTypeInfo} (T11's javadoc explains why: these are
 * write-only DTOs, never meant to be deserialized back), so Jackson has no way to pick which of
 * {@code AttributePropertyResponse} / {@code RelationshipPropertyResponse} to construct for each
 * element. Tests that need to inspect a type's properties therefore fetch the raw body and walk it
 * as a {@link JsonNode} tree instead of deserializing into {@code TypeDetailResponse}.
 *
 * <p>Extends {@link PostgresTestcontainersSupport}: {@code sequeless.persistence.adapter=postgres}
 * is baked into {@code application.yaml}, so every context this module boots needs a reachable
 * Postgres, even a context that never touches {@code /objects}. See that class's javadoc.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TypesEndToEndTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void listsAllFiveTypes() {
        List<TypeSummaryResponse> types =
                restTestClient
                        .get()
                        .uri("/types")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<List<TypeSummaryResponse>>() {})
                        .returnResult()
                        .getResponseBody();

        assertThat(types).isNotNull();
        assertThat(types.stream().map(TypeSummaryResponse::name))
                .containsExactlyInAnyOrder("Deliverable", "Person", "Project", "Task", "WorkItem");
    }

    @Test
    void taskShowsPropertiesInOrderWithFlags() throws Exception {
        JsonNode task = fetchAsJson("/types/Task");

        assertThat(task.get("name").asText()).isEqualTo("Task");
        assertThat(toList(task.get("superTypes"))).containsExactlyInAnyOrder("Deliverable", "WorkItem");

        JsonNode properties = task.get("properties");
        assertThat(properties).hasSize(9);

        assertProperty(properties.get(0), "attribute", "title", 1, false, false, true, false, false);
        assertProperty(properties.get(1), "attribute", "status", 2, true, true, false, false, false);
        assertProperty(properties.get(2), "attribute", "priority", 3, false, false, false, false, false);
        assertProperty(
                properties.get(3), "relationship", "assignedTo", 4, true, false, false, false, false);
        assertProperty(
                properties.get(4), "relationship", "belongsToProject", 5, false, false, false, false,
                false);
        assertProperty(
                properties.get(5), "attribute", "createdAt", 6, false, false, false, true, true);
        assertProperty(
                properties.get(6), "attribute", "estimatedHours", 7, false, false, false, false, false);
        assertProperty(
                properties.get(7), "attribute", "dueDate", 8, false, false, false, false, false);
        assertProperty(
                properties.get(8), "attribute", "description", 9, false, false, true, false, false);
    }

    @Test
    void projectShowsDerivedPropertiesWithReadOnlyAndSummary() throws Exception {
        JsonNode project = fetchAsJson("/types/Project");

        JsonNode properties = project.get("properties");
        JsonNode openTaskCount = findProperty(properties, "openTaskCount");
        assertThat(openTaskCount.get("readOnly").asBoolean()).as("openTaskCount readOnly").isTrue();
        JsonNode openTaskCountDerivation = openTaskCount.get("derivation");
        assertThat(openTaskCountDerivation.get("kind").asText()).isEqualTo("rollup");
        assertThat(openTaskCountDerivation.get("summary").asText())
                .isEqualTo("count(Task via belongsToProject where status ne 'done')");

        JsonNode totalEstimatedHours = findProperty(properties, "totalEstimatedHours");
        assertThat(totalEstimatedHours.get("readOnly").asBoolean())
                .as("totalEstimatedHours readOnly")
                .isTrue();
        JsonNode totalEstimatedHoursDerivation = totalEstimatedHours.get("derivation");
        assertThat(totalEstimatedHoursDerivation.get("kind").asText()).isEqualTo("rollup");
        assertThat(totalEstimatedHoursDerivation.get("summary").asText())
                .isEqualTo("sum(Task via belongsToProject of estimatedHours)");

        JsonNode title = findProperty(properties, "title");
        assertThat(title.get("derivation").isNull()).as("title derivation absent").isTrue();
    }

    @Test
    void projectShowsStateMachineWithStatesAndTransitions() throws Exception {
        JsonNode project = fetchAsJson("/types/Project");

        JsonNode stateMachine = project.get("stateMachine");
        assertThat(stateMachine).as("Project has a stateMachine block").isNotNull();

        JsonNode states = stateMachine.get("states");
        assertThat(states).hasSize(4);
        assertThat(namesOf(states)).containsExactly("Draft", "Active", "OnHold", "Closed");

        assertThat(stateMachine.get("initialState").asText()).isEqualTo("Draft");

        JsonNode transitions = stateMachine.get("transitions");
        assertThat(transitions).hasSize(4);
        assertThat(findTransition(transitions, "activate").get("hasGuard").asBoolean())
                .as("activate hasGuard")
                .isTrue();
        assertThat(findTransition(transitions, "hold").get("hasGuard").asBoolean())
                .as("hold hasGuard")
                .isFalse();
        assertThat(findTransition(transitions, "resume").get("hasGuard").asBoolean())
                .as("resume hasGuard")
                .isFalse();
        assertThat(findTransition(transitions, "close").get("hasGuard").asBoolean())
                .as("close hasGuard")
                .isFalse();
    }

    @Test
    void taskShowsNoStateMachine() throws Exception {
        JsonNode task = fetchAsJson("/types/Task");
        assertThat(task.get("stateMachine").isNull()).as("Task has no stateMachine").isTrue();
    }

    @Test
    void resolvesByEncodedIri() throws Exception {
        JsonNode byName = fetchAsJson("/types/Task");
        String encodedIri = URLEncoder.encode(Fixtures.TASK_IRI, StandardCharsets.UTF_8);
        // Pre-built java.net.URI, not a "/types/{...}" template string: RestTestClient's
        // uri(String, Object...) overload runs the string through UriComponentsBuilder's own
        // encoding pass, which would re-encode the '%' this test just produced (turning
        // "%23Task" into "%2523Task") and defeat the whole point of the encoded-IRI branch.
        // uri(URI) sends the given URI exactly as built, with no second encoding pass.
        URI encodedIriUri = URI.create("http://localhost:" + port + "/types/" + encodedIri);
        JsonNode byIri = fetchAsJson(encodedIriUri);

        assertThat(byIri).isEqualTo(byName);
        assertThat(byIri.get("iri").asText()).isEqualTo(Fixtures.TASK_IRI);
    }

    @Test
    void unknownNameIs404() {
        restTestClient
                .get()
                .uri("/types/DoesNotExist")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ErrorResponse.class)
                .value(response -> assertThat(response.message()).isEqualTo("No type found for 'DoesNotExist'"));
    }

    @Test
    void reloadSucceeds() {
        restTestClient
                .post()
                .uri("/types/reload")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OntologyReportResponse.class)
                .value(
                        response -> {
                            assertThat(response.consistent()).isTrue();
                            assertThat(response.issues()).isEmpty();
                        });
    }

    private JsonNode fetchAsJson(String uri) throws Exception {
        return parse(restTestClient.get().uri(uri));
    }

    private JsonNode fetchAsJson(URI uri) throws Exception {
        return parse(restTestClient.get().uri(uri));
    }

    private JsonNode parse(RestTestClient.RequestHeadersSpec<?> request) throws Exception {
        byte[] body =
                request
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        return objectMapper.readTree(body);
    }

    private static JsonNode findProperty(JsonNode properties, String name) {
        for (JsonNode property : properties) {
            if (property.get("name").asText().equals(name)) {
                return property;
            }
        }
        throw new AssertionError("No property named '" + name + "' found");
    }

    private static List<String> namesOf(JsonNode states) {
        List<String> names = new ArrayList<>();
        for (JsonNode state : states) {
            names.add(state.get("name").asText());
        }
        return names;
    }

    private static JsonNode findTransition(JsonNode transitions, String name) {
        for (JsonNode transition : transitions) {
            if (transition.get("name").asText().equals(name)) {
                return transition;
            }
        }
        throw new AssertionError("No transition named '" + name + "' found");
    }

    private static List<String> toList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(element -> values.add(element.asText()));
        return values;
    }

    private static void assertProperty(
            JsonNode property,
            String kind,
            String name,
            int order,
            boolean facet,
            boolean indexed,
            boolean searchable,
            boolean readOnly,
            boolean hidden) {
        assertThat(property.get("kind").asText()).as("%s kind", name).isEqualTo(kind);
        assertThat(property.get("name").asText()).as("property name").isEqualTo(name);
        assertThat(property.get("order").asInt()).as("%s order", name).isEqualTo(order);
        assertThat(property.get("facet").asBoolean()).as("%s facet", name).isEqualTo(facet);
        assertThat(property.get("indexed").asBoolean()).as("%s indexed", name).isEqualTo(indexed);
        assertThat(property.get("searchable").asBoolean()).as("%s searchable", name).isEqualTo(searchable);
        assertThat(property.get("readOnly").asBoolean()).as("%s readOnly", name).isEqualTo(readOnly);
        assertThat(property.get("hidden").asBoolean()).as("%s hidden", name).isEqualTo(hidden);
    }
}
