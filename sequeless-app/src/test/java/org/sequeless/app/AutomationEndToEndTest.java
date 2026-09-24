package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end acceptance coverage (T12) for Phase 6's three automated trigger kinds, all against the
 * real {@code ex:ProjectLifecycle} state machine in {@code reference.ttl}: {@code autoClose}
 * (OnChange, watching {@code ex:belongsToProject}), {@code expireHold} (Timer, {@code PT72H}), and
 * {@code reopen} (ExternalSignal). Boots the real application on a random port against a real
 * Postgres Testcontainer with the in-process automation adapter draining the outbox, mirroring
 * {@link TransitionsEndToEndTest}'s idiom exactly (same {@code RestTestClient}, same hand-rolled
 * deadline-loop polling style).
 *
 * <p><b>Fake clock seam.</b> A real {@code PT72H} timer cannot be waited out in a test, so this
 * class registers its own {@code @Primary} {@link Clock} bean — a small mutable fake — overriding
 * {@code InProcessAutomationAutoConfiguration}'s own {@code @ConditionalOnMissingBean} {@link
 * Clock} bean (which stays real {@code Clock.systemUTC()} in production and every other test
 * class). The production {@code timer-tick-ms} default (100ms) is left untouched: {@link
 * InProcessTimerScheduler#sweep()} is a pure in-memory scan with no DB access, so it already
 * notices an advanced clock quickly; the one thing that genuinely needs synchronizing is waiting
 * for the {@code TimerScheduled}/{@code TimerCancelled} outbox rows themselves to reach the
 * scheduler before advancing the clock at all — see {@link #awaitOutboxProcessed} and the timer
 * tests' own javadoc for why the naive "fire, then immediately advance" ordering races the relay.
 *
 * <p>The {@code properties} array below is deliberately not byte-identical to any other {@code
 * @SpringBootTest} class in this module (see {@code TransitionsEndToEndTest}'s own javadoc on this
 * point, and {@code automation.md} §8) — {@code sequeless.automation.recompute.mode=retry-only} is
 * the one extra key that both guarantees this class gets its own Spring test context (rather than
 * reusing another class's already-stopped Testcontainers Postgres container) and gives the
 * on-change test the tighter same-dispatch recompute-then-onChange ordering documented on {@code
 * InProcessRecomputeProperties.Mode#RETRY_ONLY} (the production default, {@code COALESCE}, still
 * converges, just possibly one extra outbox hop later).
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=inprocess",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.relay.poll-interval-ms=100",
            "sequeless.automation.recompute.mode=retry-only"
        })
class AutomationEndToEndTest extends PostgresTestcontainersSupport {

    /**
     * Registers the mutable fake {@link Clock} this class's timer tests advance directly, as a
     * {@code @Primary} bean overriding {@code InProcessAutomationAutoConfiguration}'s own {@code
     * @ConditionalOnMissingBean} {@code Clock.systemUTC()} bean. Spring Boot auto-detects a single
     * static {@code @TestConfiguration}-annotated nested class inside a {@code @SpringBootTest}
     * class and imports it automatically, no explicit {@code @Import} needed.
     *
     * <p>{@link #CLOCK} is exposed as a {@code static final} field, not retrieved back out of the
     * Spring context, so test methods can call {@link MutableClock#advance(Duration)} directly on
     * the exact same instance the {@code @Primary} bean method returns.
     */
    @TestConfiguration
    static class ClockTestConfiguration {

        static final MutableClock CLOCK = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

        @Bean
        @Primary
        Clock clock() {
            return CLOCK;
        }
    }

    /**
     * A {@link Clock} whose {@link #instant()} is an {@link AtomicReference} a test can jump
     * forward with {@link #advance(Duration)} — no sleeping, no polling on the clock's own value.
     * {@link InProcessTimerScheduler}'s {@code schedule()} always computes a timer's due instant
     * relative to this clock's value *at schedule time*, so advancing this clock by more than
     * {@code PT72H} in one jump is equivalent to real time having passed, regardless of this
     * clock's absolute starting value or how many earlier tests already advanced it.
     */
    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        void advance(Duration by) {
            now.updateAndGet(instant -> instant.plus(by));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("this fake clock is always UTC");
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @LocalServerPort private int port;

    @Autowired private DataSource sequelessPersistenceDataSource;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void closingLastOpenTaskAutoClosesProjectThroughANormalTransitionFire() {
        RestTestClient client = restTestClient();
        String title = "Auto-close project";
        BusinessObjectResponse project = createProject(client, title);
        BusinessObjectResponse owner = createPerson(client, "Ada Lovelace");
        setOwnerAndActivate(client, project, title, owner);

        BusinessObjectResponse task =
                createTask(client, "Only task", project.id().toString(), "open");

        // openTaskCount is 1 right after creation (the Task's own status "open" is != "done", so
        // it counts) -- autoClose's guard ("self.openTaskCount == 0") cannot pass yet. Marking the
        // Task "done" via a full PUT (replacing every property, per UpdateObjectRequest's shape) is
        // the ObjectUpdated event that recomputes openTaskCount back to 0 and re-evaluates onChange.
        BusinessObjectResponse doneTask =
                client.put()
                        .uri("/objects/Task/" + task.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        "version",
                                        1,
                                        "properties",
                                        Map.of(
                                                "title", List.of("Only task"),
                                                "belongsToProject", project.id().toString(),
                                                "status", "done",
                                                "estimatedHours", 1)))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(doneTask).isNotNull();

        awaitState(client, project.id(), "Closed", Duration.ofSeconds(6));

        assertTransitionFiredRow(project.id(), "autoClose");

        // Sanity: the Task itself is untouched by autoClose (it only moves the Project).
        assertThat(doneTask.state()).isNull();
    }

    @Test
    void onHoldProjectAutoClosesAfterTheFakeClockAdvancesPastTheHoldDuration() {
        RestTestClient client = restTestClient();
        String title = "Timer fires project";
        BusinessObjectResponse project = createProject(client, title);
        BusinessObjectResponse owner = createPerson(client, "Grace Hopper");
        BusinessObjectResponse activated = setOwnerAndActivate(client, project, title, owner);

        fireTransition(client, project.id(), "hold", activated.version());

        // InProcessTimerScheduler.schedule() computes its due instant as *its own clock's current
        // value* plus the transition's `after` duration, at the moment the relay actually drains
        // this TimerScheduled outbox row -- not at the moment "hold" wrote it. Advancing the fake
        // clock before that dispatch has happened would make schedule() compute a due instant
        // relative to the already-advanced "now", pushing it another 72h out and making the timer
        // never fire within this test. Wait for the row to be durably dispatched first.
        awaitOutboxProcessed(project.id(), "TimerScheduled");

        ClockTestConfiguration.CLOCK.advance(Duration.ofHours(73));

        awaitState(client, project.id(), "Closed", Duration.ofSeconds(4));
    }

    /**
     * Polls {@code sq_outbox} until a row of the given {@code kind} for {@code objectId} has been
     * claimed by {@link org.sequeless.app.automation.OutboxRelay} (its {@code processed_at} is set)
     * -- the only way an e2e test can know a durably-recorded, async-relayed side effect (like
     * {@link InProcessTimerScheduler}'s bookkeeping for a {@code TimerScheduled} row) has actually
     * reached the in-process adapter before racing it with the next step.
     */
    private void awaitOutboxProcessed(UUID objectId, String kind) {
        JdbcClient jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            long processedCount =
                    jdbcClient
                            .sql(
                                    "SELECT count(*) FROM sq_outbox WHERE object_id = :objectId AND"
                                            + " kind = :kind AND processed_at IS NOT NULL")
                            .param("objectId", objectId)
                            .param("kind", kind)
                            .query(Long.class)
                            .single();
            if (processedCount > 0) {
                return;
            }
            sleep(Duration.ofMillis(50));
        }
        throw new AssertionError(
                "outbox row of kind '" + kind + "' for object " + objectId + " was never processed");
    }

    @Test
    void onHoldProjectThatResumesFirstDoesNotAutoCloseAfterTheSameAdvance() {
        RestTestClient client = restTestClient();
        String title = "Timer cancelled project";
        BusinessObjectResponse project = createProject(client, title);
        BusinessObjectResponse owner = createPerson(client, "Katherine Johnson");
        BusinessObjectResponse activated = setOwnerAndActivate(client, project, title, owner);

        fireTransition(client, project.id(), "hold", activated.version());
        fireTransition(client, project.id(), "resume", activated.version() + 1);

        // Both the TimerScheduled (from "hold") and the TimerCancelled (from "resume") must have
        // reached InProcessTimerScheduler before advancing the clock -- see the sibling positive
        // test's javadoc for why scheduling relative to an already-advanced clock would otherwise
        // race. Here the race would be benign for the schedule half (it would just push the timer
        // further out), but waiting for the cancel too makes the assertion below meaningful rather
        // than incidentally true.
        awaitOutboxProcessed(project.id(), "TimerScheduled");
        awaitOutboxProcessed(project.id(), "TimerCancelled");

        ClockTestConfiguration.CLOCK.advance(Duration.ofHours(73));

        assertStaysAtState(client, project.id(), "Active", Duration.ofSeconds(1));
    }

    @Test
    void reopenSignalMovesAClosedProjectBackToActive() {
        RestTestClient client = restTestClient();
        String title = "Signal reopen project";
        BusinessObjectResponse project = createProject(client, title);
        BusinessObjectResponse owner = createPerson(client, "Margaret Hamilton");
        BusinessObjectResponse activated = setOwnerAndActivate(client, project, title, owner);

        fireTransition(client, project.id(), "close", activated.version());

        client.post()
                .uri("/objects/Project/" + project.id() + "/signals/reopen")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.ACCEPTED)
                .expectBody()
                .isEmpty();

        awaitState(client, project.id(), "Active", Duration.ofSeconds(6));
    }

    private BusinessObjectResponse setOwnerAndActivate(
            RestTestClient client,
            BusinessObjectResponse project,
            String title,
            BusinessObjectResponse owner) {
        client.put()
                .uri("/objects/Project/" + project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "version",
                                1,
                                "properties",
                                Map.of(
                                        "title", List.of(title),
                                        "owner", owner.id().toString())))
                .exchange()
                .expectStatus()
                .isOk();

        return fireTransition(client, project.id(), "activate", 2);
    }

    private BusinessObjectResponse fireTransition(
            RestTestClient client, UUID objectId, String name, long expectedVersion) {
        return client.post()
                .uri("/objects/Project/" + objectId + "/transitions/" + name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedVersion", expectedVersion))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createProject(RestTestClient client, String title) {
        return client.post()
                .uri("/objects/Project")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("title", List.of(title))))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createPerson(RestTestClient client, String name) {
        return client.post()
                .uri("/objects/Person")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("name", name)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createTask(
            RestTestClient client, String title, String belongsToProjectId, String status) {
        return client.post()
                .uri("/objects/Task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "properties",
                                Map.of(
                                        "title", List.of(title),
                                        "belongsToProject", belongsToProjectId,
                                        "status", status,
                                        // Every Task in this ontology feeds ex:totalEstimatedHours
                                        // too (a sq:sum Rollup over ex:estimatedHours). Postgres's
                                        // SUM() of an all-NULL group returns SQL NULL, and
                                        // DecimalValue's constructor rejects a null value -- so a
                                        // Task with no estimatedHours crashes the Project's own
                                        // GET (a pre-existing latent bug, not something T12 owns).
                                        // Every other passing test in this module always sets this
                                        // property for exactly this reason; do the same here.
                                        "estimatedHours", 1)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void awaitState(
            RestTestClient client, UUID objectId, String expectedState, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        String lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = currentState(client, objectId);
            if (expectedState.equals(lastSeen)) {
                return;
            }
            sleep(Duration.ofMillis(200));
        }
        assertThat(lastSeen)
                .as("Project %s eventually reaches state '%s'", objectId, expectedState)
                .isEqualTo(expectedState);
    }

    private void assertStaysAtState(
            RestTestClient client, UUID objectId, String expectedState, Duration window) {
        Instant deadline = Instant.now().plus(window);
        while (Instant.now().isBefore(deadline)) {
            String actual = currentState(client, objectId);
            assertThat(actual)
                    .as("Project %s stays at state '%s' (timer must not have fired)", objectId, expectedState)
                    .isEqualTo(expectedState);
            sleep(Duration.ofMillis(200));
        }
    }

    private String currentState(RestTestClient client, UUID objectId) {
        BusinessObjectResponse response =
                client.get()
                        .uri("/objects/Project/" + objectId)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(response).isNotNull();
        return response.state();
    }

    private void assertTransitionFiredRow(UUID objectId, String transitionName) {
        JdbcClient jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
        long count =
                jdbcClient
                        .sql(
                                "SELECT count(*) FROM sq_outbox WHERE kind = :kind AND object_id ="
                                        + " :objectId AND payload ->> 'transitionName' ="
                                        + " :transitionName")
                        .param("kind", "TransitionFired")
                        .param("objectId", objectId)
                        .param("transitionName", transitionName)
                        .query(Long.class)
                        .single();
        assertThat(count)
                .as(
                        "a TransitionFired outbox row exists for object %s and transition '%s'",
                        objectId, transitionName)
                .isGreaterThanOrEqualTo(1L);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
