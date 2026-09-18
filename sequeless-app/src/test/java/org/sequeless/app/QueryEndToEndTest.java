package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port, wired to its shipped {@code
 * application.yaml} defaults, and proves every Phase 3 REST acceptance criterion end to end
 * against a real Postgres container: facet counts (including reference-facet display-label
 * resolution), a reference-property filter, a date-range filter, numeric (not lexical) sort,
 * {@code WorkItem} returning both {@code Task} and {@code Project}, free-text search ranking, and
 * the invalid-query 400 shape. {@code QueryContract} (spi-testkit) and the Postgres adapter's own
 * {@code EXPLAIN} index-usage test already prove the underlying {@code QueryPort} contract and
 * index usage directly against Postgres; this class's job is only to prove the same behaviour is
 * reachable through {@code GET /objects/{type}} end to end.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason every other {@code
 * @SpringBootTest} in this module does. {@code sequeless.ontology.reasoner=owl} is set explicitly
 * via {@code properties} — already {@code application.yaml}'s own default, so functionally a
 * no-op — purely so this class's {@code @SpringBootTest} signature differs from every other class
 * in this module and so does not reuse another class's (by-then-stopped) Testcontainers container
 * from Spring's test-context cache; see {@code ObjectsEndToEndTest}'s javadoc for the full
 * explanation.
 *
 * <p><b>The {@code ensureIndexes} gap:</b> the app loads its ontology at startup from {@code
 * sequeless.ontology.source} (a classpath resource), which never goes through {@code
 * DefaultOntologyAdministration#importTurtle} and so never calls {@code
 * QueryPort#ensureIndexes} on its own. Without that having run at least once, {@code
 * sq_searchable_property} is empty and the {@code search_vector} trigger writes empty tsvectors,
 * so free-text search would find nothing even though data exists. {@link #triggerEnsureIndexes()}
 * forces this by exporting the currently-active ontology and immediately re-importing the same
 * content — {@code ensureIndexes} is documented idempotent, so doing this once per test is cheap
 * and safe.
 *
 * <p>Every test builds its own fixture data and asserts on deltas or id-membership, never bare/
 * exact totals — this module's tests all share one Postgres container/tenant across the whole
 * test run (and, within this class, across every test method), so absolute counts are never
 * stable.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "sequeless.ontology.reasoner=owl")
class QueryEndToEndTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        triggerEnsureIndexes();
    }

    /**
     * Forces {@code QueryPort#ensureIndexes} to run by exporting the currently-active ontology
     * and re-importing that exact same content. See this class's javadoc for why this is
     * necessary before any browse/search/facet assertion.
     */
    private void triggerEnsureIndexes() {
        byte[] exported =
                restTestClient
                        .get()
                        .uri("/ontology")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(exported).isNotNull();
        String turtle = new String(exported, StandardCharsets.UTF_8);

        restTestClient
                .post()
                .uri("/ontology")
                .contentType(MediaType.valueOf("text/turtle"))
                .body(turtle)
                .exchange()
                .expectStatus()
                .isOk();
    }

    // --- fixture helpers -------------------------------------------------------------------

    private BusinessObjectResponse createPerson(String name) {
        return createObject("Person", Map.of("name", name));
    }

    private BusinessObjectResponse createTask(Map<String, Object> listProperties) {
        return createTask(listProperties, null);
    }

    private BusinessObjectResponse createTask(Map<String, Object> listProperties, UUID assignedTo) {
        Map<String, Object> properties = new HashMap<>(listProperties);
        if (assignedTo != null) {
            properties.put("assignedTo", assignedTo.toString());
        }
        return createObject("Task", properties);
    }

    private BusinessObjectResponse createProject(Map<String, Object> listProperties) {
        return createObject("Project", listProperties);
    }

    private BusinessObjectResponse createObject(String type, Map<String, Object> properties) {
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

    private JsonNode fetchAsJson(String uri) {
        byte[] responseBody =
                restTestClient
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

    private Map<String, Long> facetCounts(JsonNode result, String facetName) {
        Map<String, Long> counts = new HashMap<>();
        JsonNode facets = result.get("facets");
        JsonNode bucket = facets == null ? null : facets.get(facetName);
        if (bucket != null) {
            for (JsonNode entry : bucket) {
                counts.put(entry.get("value").asText(), entry.get("count").asLong());
            }
        }
        return counts;
    }

    private Set<String> idsOf(JsonNode result) {
        Set<String> ids = new HashSet<>();
        for (JsonNode item : result.get("items")) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }

    private JsonNode findItem(JsonNode result, String id) {
        for (JsonNode item : result.get("items")) {
            if (item.get("id").asText().equals(id)) {
                return item;
            }
        }
        return null;
    }

    /** The subset of {@code result}'s items whose id is in {@code idsOfInterest}, in response order. */
    private List<String> orderedIdsAmong(JsonNode result, Set<String> idsOfInterest) {
        List<String> ordered = new ArrayList<>();
        for (JsonNode item : result.get("items")) {
            String id = item.get("id").asText();
            if (idsOfInterest.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    // --- acceptance criteria -----------------------------------------------------------------

    @Test
    void facetCountsForStatusAndAssignedToMatchFixtureIncludingDisplayLabel() {
        JsonNode baseline = fetchAsJson("/objects/Task?facets=status,assignedTo&size=1");
        Map<String, Long> statusBaseline = facetCounts(baseline, "status");
        Map<String, Long> assignedToBaseline = facetCounts(baseline, "assignedTo");

        BusinessObjectResponse ada = createPerson("Ada Lovelace");
        createTask(Map.of("title", List.of("Facet fixture open assigned"), "status", "OPEN"), ada.id());
        createTask(Map.of("title", List.of("Facet fixture open unassigned"), "status", "OPEN"));
        createTask(Map.of("title", List.of("Facet fixture closed assigned"), "status", "CLOSED"), ada.id());

        JsonNode after = fetchAsJson("/objects/Task?facets=status,assignedTo&size=1");
        Map<String, Long> statusAfter = facetCounts(after, "status");
        Map<String, Long> assignedToAfter = facetCounts(after, "assignedTo");

        long openDelta = statusAfter.getOrDefault("OPEN", 0L) - statusBaseline.getOrDefault("OPEN", 0L);
        long closedDelta = statusAfter.getOrDefault("CLOSED", 0L) - statusBaseline.getOrDefault("CLOSED", 0L);
        assertThat(openDelta).as("OPEN status facet delta").isEqualTo(2L);
        assertThat(closedDelta).as("CLOSED status facet delta").isEqualTo(1L);

        // Reference facet bucket keyed by Person.name (sq:displayLabel), not by Ada's raw UUID:
        // both tasks assigned to her (the OPEN one and the CLOSED one) count toward this bucket,
        // the unassigned OPEN task does not.
        long adaDelta =
                assignedToAfter.getOrDefault("Ada Lovelace", 0L) - assignedToBaseline.getOrDefault("Ada Lovelace", 0L);
        assertThat(assignedToAfter.keySet()).as("assignedTo facet is label-keyed, not UUID-keyed")
                .doesNotContain(ada.id().toString());
        assertThat(adaDelta).as("Ada Lovelace assignedTo facet delta").isEqualTo(2L);
    }

    @Test
    void filteringOnReferencePropertyWorks() {
        BusinessObjectResponse person = createPerson("Grace Hopper");
        BusinessObjectResponse assigned = createTask(Map.of("title", List.of("Ref filter assigned")), person.id());
        BusinessObjectResponse unassigned = createTask(Map.of("title", List.of("Ref filter unassigned")));

        JsonNode result = fetchAsJson("/objects/Task?filter[assignedTo][eq]=" + person.id() + "&size=200");
        Set<String> ids = idsOf(result);
        assertThat(ids).contains(assigned.id().toString());
        assertThat(ids).doesNotContain(unassigned.id().toString());

        JsonNode assignedItem = findItem(result, assigned.id().toString());
        assertThat(assignedItem).isNotNull();
        assertThat(assignedItem.get("properties").get("assignedTo").asText()).isEqualTo(person.id().toString());
    }

    @Test
    void filteringOnDateRangeWorks() {
        BusinessObjectResponse early =
                createTask(Map.of("title", List.of("Date range early"), "dueDate", "2026-01-05"));
        BusinessObjectResponse mid =
                createTask(Map.of("title", List.of("Date range mid"), "dueDate", "2026-06-15"));
        BusinessObjectResponse late =
                createTask(Map.of("title", List.of("Date range late"), "dueDate", "2026-12-25"));

        JsonNode result =
                fetchAsJson(
                        "/objects/Task?filter[dueDate][gte]=2026-02-01&filter[dueDate][lte]=2026-09-01&size=200");
        Set<String> ids = idsOf(result);
        assertThat(ids).contains(mid.id().toString());
        assertThat(ids).doesNotContain(early.id().toString(), late.id().toString());
    }

    @Test
    void sortingOnNumericPropertyIsNumericNotLexical() {
        // 2, 3, 10: lexical ordering would put "10" before "2"/"3"; numeric ordering must not.
        BusinessObjectResponse p2 = createTask(Map.of("title", List.of("Numeric sort fixture A"), "priority", 2));
        BusinessObjectResponse p10 = createTask(Map.of("title", List.of("Numeric sort fixture B"), "priority", 10));
        BusinessObjectResponse p3 = createTask(Map.of("title", List.of("Numeric sort fixture C"), "priority", 3));
        Set<String> fixtureIds = Set.of(p2.id().toString(), p10.id().toString(), p3.id().toString());

        JsonNode ascResult = fetchAsJson("/objects/Task?sort=priority&size=200");
        List<String> ascOrder = orderedIdsAmong(ascResult, fixtureIds);
        assertThat(ascOrder)
                .as("ascending priority sort is numeric")
                .containsExactly(p2.id().toString(), p3.id().toString(), p10.id().toString());

        JsonNode descResult = fetchAsJson("/objects/Task?sort=-priority&size=200");
        List<String> descOrder = orderedIdsAmong(descResult, fixtureIds);
        assertThat(descOrder)
                .as("descending priority sort is numeric")
                .containsExactly(p10.id().toString(), p3.id().toString(), p2.id().toString());
    }

    @Test
    void queryingWorkItemReturnsBothTasksAndProjects() {
        String tag = "WorkItemFixture-" + UUID.randomUUID();
        BusinessObjectResponse task = createTask(Map.of("title", List.of(tag + " Task")));
        BusinessObjectResponse project = createProject(Map.of("title", List.of(tag + " Project")));

        JsonNode result = fetchAsJson("/objects/WorkItem?size=200");
        JsonNode taskItem = findItem(result, task.id().toString());
        JsonNode projectItem = findItem(result, project.id().toString());

        assertThat(taskItem).as("created Task appears when browsing WorkItem").isNotNull();
        assertThat(projectItem).as("created Project appears when browsing WorkItem").isNotNull();
        assertThat(taskItem.get("type").asText()).isEqualTo("Task");
        assertThat(projectItem.get("type").asText()).isEqualTo("Project");
    }

    @Test
    void textSearchRanksMatchingTaskFirst() {
        String token = "roadmap" + Long.toHexString(System.nanoTime());
        BusinessObjectResponse task =
                createTask(
                        Map.of(
                                "title", List.of("Roadmap task"),
                                "description", List.of("The quarterly " + token + " review meeting notes")));
        BusinessObjectResponse project =
                createProject(
                        Map.of(
                                "title", List.of("Other project"),
                                "description", List.of("Unrelated project description")));

        JsonNode result = fetchAsJson("/objects/WorkItem?q=" + token + "&size=200");
        List<String> order = orderedIdsAmong(result, Set.of(task.id().toString(), project.id().toString()));

        assertThat(order).as("the matching Task is present").contains(task.id().toString());
        assertThat(order.get(0))
                .as("the matching Task ranks first among this fixture's ids")
                .isEqualTo(task.id().toString());
    }

    @Test
    void invalidFilterPropertyReturns400WithViolations() {
        restTestClient
                .get()
                .uri("/objects/Task?filter[nonexistentProperty][eq]=x")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.violations[?(@.property == 'nonexistentProperty')]")
                .exists();
    }
}
