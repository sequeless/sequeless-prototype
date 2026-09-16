package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Proves the Ontology phase's reasoner-setting acceptance criterion end to end (criterion 3): with
 * {@code sequeless.ontology.reasoner=none} overriding {@code application.yaml}'s {@code owl}
 * default, {@code GET /types/Task} reports only {@code Task}'s <em>directly asserted</em> supertype
 * ({@code WorkItem}); the transitively inherited {@code Deliverable} — visible under the shipped
 * {@code owl} default, per {@link TypesEndToEndTest#taskShowsPropertiesInOrderWithFlags()} — is
 * absent because no reasoner ran to compute superclass closure.
 *
 * <p>{@code TypeDetailResponse} is not deserialized directly for the same reason {@link
 * TypesEndToEndTest} does not: its {@code properties} field is a sealed {@code PropertyResponse}
 * list with no {@code @JsonTypeInfo}. This test only needs {@code superTypes}, a plain {@code
 * List<String>}, so it walks the raw body as a {@link JsonNode} rather than pull in that whole
 * machinery.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "sequeless.ontology.reasoner=none")
class ReasonerSwitchEndToEndTest {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void taskSuperTypesOmitDeliverableButContainWorkItem() throws Exception {
        byte[] body =
                restTestClient
                        .get()
                        .uri("/types/Task")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        JsonNode task = objectMapper.readTree(body);

        List<String> superTypes = new ArrayList<>();
        task.get("superTypes").forEach(element -> superTypes.add(element.asText()));

        assertThat(superTypes).contains("WorkItem").doesNotContain("Deliverable");
    }
}
