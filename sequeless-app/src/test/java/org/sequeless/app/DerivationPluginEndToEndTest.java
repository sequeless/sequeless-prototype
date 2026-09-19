package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Proves, against a real Spring Boot application context, that a {@code sq:Plugin}-derived
 * property is discovered by name via {@code ServiceLoader} and actually invoked end to end — the
 * one thing T5's Jena-adapter-level tests could not prove (see finding F31): they exercise {@code
 * DerivationPluginRegistry.fromServiceLoader()} directly, never inside a booted application.
 *
 * <p>{@code sequeless-spi-testkit} (a test-scope dependency of this module, per its {@code pom.xml})
 * already puts both {@code ontology/reference-plugin.ttl} and {@code WorkloadDerivationPlugin}'s
 * {@code META-INF/services/org.sequeless.spi.derivation.DerivationPlugin} registration on this
 * module's test classpath for every test in the module — that classpath wiring is inert but present
 * everywhere else (per prior findings), so this test is the first one that actually points {@code
 * sequeless.ontology.source} at the plug-in-bearing fixture and reads the computed value back,
 * proving the wiring is not just present but functional.
 *
 * <p>{@code sequeless.ontology.source} is overridden via {@code properties} (not a command-line
 * argument, unlike the manually driven {@code SpringApplicationBuilder} tests in this package) —
 * this is a normal {@code @SpringBootTest}, which goes through the Spring test context framework
 * and its usual property-source precedence, so {@code @SpringBootTest(properties = ...)} already
 * wins over {@code application.yaml}'s default. This override also doubles as the distinguishing
 * {@code @SpringBootTest} property every class in this module needs (see {@code
 * ObjectsEndToEndTest}'s javadoc) to avoid Spring's test-context cache reusing another class's
 * now-stopped Testcontainers container.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} for the same reason every other {@code
 * @SpringBootTest} in this module does: {@code sequeless.persistence.adapter=postgres} is baked
 * into {@code application.yaml}.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "sequeless.ontology.source=classpath:ontology/reference-plugin.ttl")
class DerivationPluginEndToEndTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void personWorkloadIsTheSumOfAssignedTasksEstimatedHours() {
        BusinessObjectResponse person = create("Person", Map.of("name", "Ada Lovelace"));
        String personId = person.id().toString();

        // WorkloadDerivationPlugin sums ex:estimatedHours over every ex:Task assigned to the
        // Person via ex:assignedTo (F29): 4 + 6 = 10.
        create(
                "Task",
                Map.of(
                        "title", List.of("First assignment"),
                        "estimatedHours", 4,
                        "assignedTo", personId));
        create(
                "Task",
                Map.of(
                        "title", List.of("Second assignment"),
                        "estimatedHours", 6,
                        "assignedTo", personId));

        JsonNode read = fetchAsJson("/objects/Person/" + personId);
        assertThat(read.get("properties").get("workload").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    private BusinessObjectResponse create(String type, Map<String, Object> properties) {
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
}
