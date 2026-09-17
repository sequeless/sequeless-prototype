package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.WhoAmIResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port and proves the Application phase's
 * headline acceptance criterion end to end: a correctly configured app serves {@code /whoami} and
 * reports itself healthy over Actuator.
 *
 * <p>{@code sequeless.authz.adapter=permit-all} is set explicitly via {@code properties} rather
 * than left to {@code application.yaml}'s own default (which happens to already be {@code
 * permit-all}). Setting it here is the point: it demonstrates that this property is what selects
 * the adapter, not a coincidence of the shipped default.
 *
 * <p>Uses {@link RestTestClient} bound to the running server via {@link LocalServerPort}, rather
 * than {@code TestRestTemplate}: this Spring Boot 4 / Spring Framework 7 stack no longer ships
 * {@code TestRestTemplate} at all, and {@code RestTestClient} is its {@code WebTestClient}-style
 * replacement.
 *
 * <p>Extends {@link PostgresTestcontainersSupport}: {@code sequeless.persistence.adapter=postgres}
 * is baked into {@code application.yaml}, so every context this module boots needs a reachable
 * Postgres, even a context that never touches {@code /objects}. See that class's javadoc.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "sequeless.authz.adapter=permit-all")
class WhoAmIEndToEndTest extends PostgresTestcontainersSupport {

    @LocalServerPort private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void whoAmiReportsDefaultTenantAndAnonymousPrincipal() {
        restTestClient
            .get()
            .uri("/whoami")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WhoAmIResponse.class)
            .value(
                response -> {
                    assertThat(response.tenant()).isEqualTo("default");
                    assertThat(response.principal()).isEqualTo("anonymous");
                    assertThat(response.displayName()).isEqualTo("Anonymous");
                    assertThat(response.decision().allowed()).isTrue();
                });
    }

    @Test
    void actuatorHealthReportsUp() {
        restTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("\"status\":\"UP\""));
    }
}
