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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.rest.ErrorResponse;
import org.sequeless.app.rest.OntologyReportResponse;
import org.sequeless.app.rest.TransitionResponse;
import org.sequeless.app.rest.TypeSummaryResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.testkit.Fixtures;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port, wired to its shipped {@code
 * application.yaml} defaults ({@code sequeless.ontology.adapter=jena}, {@code reasoner=owl}, {@code
 * source=classpath:ontology/reference.ttl}), and proves the Ontology phase's headline
 * acceptance criterion end to end: {@code GET /api/types}, {@code GET /api/types/{nameOrIri}}, and
 * {@code POST /api/types/reload} render the reference ontology correctly over HTTP.
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
 * Postgres, even a context that never touches {@code /api/objects}. See that class's javadoc.
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
                        .uri("/api/types")
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
        JsonNode task = fetchAsJson("/api/types/Task");

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
        JsonNode project = fetchAsJson("/api/types/Project");

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

    /**
     * The type-level state-machine descriptor lists every transition {@code ex:ProjectLifecycle}
     * declares in {@code reference.ttl} — all seven, regardless of trigger kind: the four {@code
     * sq:UserAction} ones ({@code activate}, {@code hold}, {@code resume}, {@code close}) plus
     * {@code autoClose} ({@code sq:OnChange}), {@code expireHold} ({@code sq:Timer}) and {@code
     * reopen} ({@code sq:ExternalSignal}).
     *
     * <p>This used to be {@code hasSize(4)}: {@code TypeResponseMapper#toStateMachineResponse} once
     * filtered the descriptor down to {@code USER_ACTION} transitions, mirroring {@code GET
     * .../transitions}. That filter was dropped because this endpoint feeds the type-level lifecycle
     * diagram, which has to draw every edge an object can actually travel — including the automated
     * ones — or a Project would appear stuck in {@code OnHold}/{@code Closed}. The per-object action
     * list keeps its own filter; {@link #perObjectTransitionsStillOfferOnlyUserActionTransitions}
     * pins that side of the asymmetry.
     */
    @Test
    void projectShowsStateMachineWithStatesAndTransitions() throws Exception {
        JsonNode project = fetchAsJson("/api/types/Project");

        JsonNode stateMachine = project.get("stateMachine");
        assertThat(stateMachine).as("Project has a stateMachine block").isNotNull();

        JsonNode states = stateMachine.get("states");
        assertThat(states).hasSize(4);
        assertThat(namesOf(states)).containsExactly("Draft", "Active", "OnHold", "Closed");

        assertThat(stateMachine.get("initialState").asText()).isEqualTo("Draft");

        JsonNode transitions = stateMachine.get("transitions");
        assertThat(transitions).hasSize(7);
        assertThat(namesOf(transitions))
                .containsExactlyInAnyOrder(
                        "activate", "hold", "resume", "close", "autoClose", "expireHold", "reopen");
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
        assertThat(findTransition(transitions, "autoClose").get("hasGuard").asBoolean())
                .as("autoClose hasGuard")
                .isTrue();
        assertThat(findTransition(transitions, "expireHold").get("hasGuard").asBoolean())
                .as("expireHold hasGuard")
                .isFalse();
        assertThat(findTransition(transitions, "reopen").get("hasGuard").asBoolean())
                .as("reopen hasGuard")
                .isFalse();
    }

    /**
     * Each automated transition renders its trigger-specific fields exactly as {@code
     * reference.ttl} declares them, and a {@code USER_ACTION} transition renders none of them — the
     * wire shape {@code TransitionSummaryResponse} documents against {@code
     * docs/architecture/sq-vocabulary.md}'s trigger vocabulary:
     *
     * <ul>
     *   <li>{@code watch} carries short names, not IRIs — {@code TypeResponseMapper#triggerWatch}
     *       maps each {@code sq:watch} IRI through {@code shortName}, so {@code
     *       ex:belongsToProject} renders as {@code "belongsToProject"}. It is an empty array, never
     *       {@code null}, for every non-{@code ON_CHANGE} trigger.
     *   <li>{@code after} is {@code Duration#toString()} of {@code sq:after}; {@code PT72H}
     *       round-trips unchanged (no normalisation to days).
     *   <li>{@code after}/{@code signalName} are JSON {@code null} for every trigger kind that does
     *       not carry them.
     * </ul>
     *
     * <p>These fields were always on the wire but were unobservable here while the descriptor was
     * filtered to {@code USER_ACTION} (see {@link #projectShowsStateMachineWithStatesAndTransitions}).
     */
    @Test
    void projectStateMachineRendersTriggerKindAndTriggerSpecificFieldsForEveryTransition()
            throws Exception {
        JsonNode transitions = fetchAsJson("/api/types/Project").get("stateMachine").get("transitions");

        JsonNode autoClose = findTransition(transitions, "autoClose");
        assertThat(autoClose.get("trigger").asText()).as("autoClose trigger").isEqualTo("ON_CHANGE");
        assertThat(autoClose.get("from").asText()).as("autoClose from").isEqualTo("Active");
        assertThat(autoClose.get("to").asText()).as("autoClose to").isEqualTo("Closed");
        assertThat(toList(autoClose.get("watch"))).as("autoClose watch").containsExactly("belongsToProject");
        assertThat(autoClose.get("after").isNull()).as("autoClose after is null").isTrue();
        assertThat(autoClose.get("signalName").isNull()).as("autoClose signalName is null").isTrue();

        JsonNode expireHold = findTransition(transitions, "expireHold");
        assertThat(expireHold.get("trigger").asText()).as("expireHold trigger").isEqualTo("TIMER");
        assertThat(expireHold.get("from").asText()).as("expireHold from").isEqualTo("OnHold");
        assertThat(expireHold.get("to").asText()).as("expireHold to").isEqualTo("Closed");
        assertThat(expireHold.get("after").asText()).as("expireHold after").isEqualTo("PT72H");
        assertThat(expireHold.get("signalName").isNull()).as("expireHold signalName is null").isTrue();
        assertThat(expireHold.get("watch")).as("expireHold watch").isEmpty();

        JsonNode reopen = findTransition(transitions, "reopen");
        assertThat(reopen.get("trigger").asText()).as("reopen trigger").isEqualTo("EXTERNAL_SIGNAL");
        assertThat(reopen.get("from").asText()).as("reopen from").isEqualTo("Closed");
        assertThat(reopen.get("to").asText()).as("reopen to").isEqualTo("Active");
        assertThat(reopen.get("signalName").asText()).as("reopen signalName").isEqualTo("reopen");
        assertThat(reopen.get("after").isNull()).as("reopen after is null").isTrue();
        assertThat(reopen.get("watch")).as("reopen watch").isEmpty();

        JsonNode activate = findTransition(transitions, "activate");
        assertThat(activate.get("trigger").asText()).as("activate trigger").isEqualTo("USER_ACTION");
        assertThat(activate.get("after").isNull()).as("activate after is null").isTrue();
        assertThat(activate.get("signalName").isNull()).as("activate signalName is null").isTrue();
        assertThat(activate.get("watch")).as("activate watch").isEmpty();
    }

    /**
     * The deliberate asymmetry with {@link #projectShowsStateMachineWithStatesAndTransitions}: the
     * type-level descriptor now lists all seven transitions, but {@code GET
     * /api/objects/Project/{id}/transitions} ({@code ObjectsController#transitions}) must still
     * offer only {@code USER_ACTION} transitions. That list drives the per-object action buttons,
     * and {@code TransitionService#fire} rejects a {@code POST .../transitions/{name}} against an
     * {@code ON_CHANGE}/{@code TIMER}/{@code EXTERNAL_SIGNAL} transition (409, not user-fireable) —
     * offering one would be a button that can only fail. The diagram needs every trigger kind; the
     * action list needs only what a caller can actually invoke.
     *
     * <p>Every automated transition in {@code reference.ttl} departs from a non-initial state
     * ({@code autoClose} from {@code Active}, {@code expireHold} from {@code OnHold}, {@code reopen}
     * from {@code Closed}), so checking a fresh {@code Draft} Project alone would pass vacuously.
     * The test therefore walks one Project through each of those states via user-action transitions
     * and checks the offered names in each. The {@code activate} guard ({@code self.owner != null})
     * needs an owner first, set the same way {@code TransitionsEndToEndTest} does.
     *
     * <p>This class's context configures no {@code sequeless.automation.adapter}, so {@code
     * OutboxRelay} relays nothing and no automated transition can fire in the background (e.g.
     * {@code autoClose} on the zero-open-task Project) and move the object out from under an
     * assertion.
     */
    @Test
    void perObjectTransitionsStillOfferOnlyUserActionTransitions() {
        String title = "Asymmetry check";
        BusinessObjectResponse project = createProject(title);
        assertThat(transitionNamesOf(project.id())).as("Draft").containsExactly("activate");

        BusinessObjectResponse owner = createPerson("Barbara Liskov");
        BusinessObjectResponse withOwner =
                restTestClient
                        .put()
                        .uri("/api/objects/Project/" + project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        "version",
                                        project.version(),
                                        "properties",
                                        Map.of("title", List.of(title), "owner", owner.id().toString())))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(withOwner).isNotNull();

        BusinessObjectResponse active = fireTransition(project.id(), "activate", withOwner.version());
        assertThat(active.state()).isEqualTo("Active");
        assertThat(transitionNamesOf(project.id()))
                .as("Active offers hold/close, never autoClose")
                .containsExactlyInAnyOrder("hold", "close")
                .doesNotContain("autoClose");

        BusinessObjectResponse onHold = fireTransition(project.id(), "hold", active.version());
        assertThat(onHold.state()).isEqualTo("OnHold");
        assertThat(transitionNamesOf(project.id()))
                .as("OnHold offers resume, never expireHold")
                .containsExactly("resume")
                .doesNotContain("expireHold");

        BusinessObjectResponse resumed = fireTransition(project.id(), "resume", onHold.version());
        BusinessObjectResponse closed = fireTransition(project.id(), "close", resumed.version());
        assertThat(closed.state()).isEqualTo("Closed");
        assertThat(transitionNamesOf(project.id()))
                .as("Closed offers nothing, never reopen")
                .isEmpty();
    }

    @Test
    void taskShowsNoStateMachine() throws Exception {
        JsonNode task = fetchAsJson("/api/types/Task");
        assertThat(task.get("stateMachine").isNull()).as("Task has no stateMachine").isTrue();
    }

    @Test
    void resolvesByEncodedIri() throws Exception {
        JsonNode byName = fetchAsJson("/api/types/Task");
        String encodedIri = URLEncoder.encode(Fixtures.TASK_IRI, StandardCharsets.UTF_8);
        // Pre-built java.net.URI, not a "/api/types/{...}" template string: RestTestClient's
        // uri(String, Object...) overload runs the string through UriComponentsBuilder's own
        // encoding pass, which would re-encode the '%' this test just produced (turning
        // "%23Task" into "%2523Task") and defeat the whole point of the encoded-IRI branch.
        // uri(URI) sends the given URI exactly as built, with no second encoding pass.
        URI encodedIriUri = URI.create("http://localhost:" + port + "/api/types/" + encodedIri);
        JsonNode byIri = fetchAsJson(encodedIriUri);

        assertThat(byIri).isEqualTo(byName);
        assertThat(byIri.get("iri").asText()).isEqualTo(Fixtures.TASK_IRI);
    }

    @Test
    void unknownNameIs404() {
        restTestClient
                .get()
                .uri("/api/types/DoesNotExist")
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
                .uri("/api/types/reload")
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

    private BusinessObjectResponse createProject(String title) {
        return createObject("Project", Map.<String, Object>of("title", List.of(title)));
    }

    private BusinessObjectResponse createPerson(String name) {
        return createObject("Person", Map.<String, Object>of("name", name));
    }

    private BusinessObjectResponse createObject(String type, Map<String, Object> properties) {
        BusinessObjectResponse created =
                restTestClient
                        .post()
                        .uri("/api/objects/" + type)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("properties", properties))
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(created).isNotNull();
        return created;
    }

    private BusinessObjectResponse fireTransition(UUID projectId, String name, long expectedVersion) {
        BusinessObjectResponse fired =
                restTestClient
                        .post()
                        .uri("/api/objects/Project/" + projectId + "/transitions/" + name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("expectedVersion", expectedVersion))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(fired).isNotNull();
        return fired;
    }

    private List<String> transitionNamesOf(UUID projectId) {
        List<TransitionResponse> transitions =
                restTestClient
                        .get()
                        .uri("/api/objects/Project/" + projectId + "/transitions")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<List<TransitionResponse>>() {})
                        .returnResult()
                        .getResponseBody();
        assertThat(transitions).isNotNull();
        return transitions.stream().map(TransitionResponse::name).toList();
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
