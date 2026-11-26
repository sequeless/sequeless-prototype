package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEvent;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.store.spi.AutomationFailure;
import org.sequeless.store.spi.AutomationFailureRecorder;
import org.sequeless.store.spi.OutboxPort;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-tests {@link OutboxRelay#pollOnce()} directly against hand-built test doubles — no Spring
 * context, no mocking library, mirroring {@code ApiExceptionAdviceTest}'s convention. {@link
 * InMemoryOutboxPort} is a trivial deque-backed {@link OutboxPort} whose {@code claimNext} pops the
 * first row matching the caller's requested kind set and mirrors the real contract's crucial
 * detail: a handler exception leaves the claimed row unremoved (as if the claiming transaction had
 * rolled back). {@link RecordingAutomationPort} records every {@link AutomationPort#ingest} call
 * and can be told to throw on a specific call.
 */
class OutboxRelayTest {

    /**
     * The recomputer for every test that ingests only non-lifecycle kinds. Such a row must never
     * reach the recompute, so this throws if it is ever called.
     */
    private static final DerivationRecomputer UNUSED_RECOMPUTER = (scope, changeEvent) -> {
        throw new UnsupportedOperationException("recompute must not run for a non-lifecycle row");
    };

    /** The recomputer for the object-lifecycle tests, which do not assert on the recompute itself. */
    private static final DerivationRecomputer NOOP_RECOMPUTER = (scope, changeEvent) -> {};

    /** A failure recorder provider with no bean: for the tests where no unstamped row is expected. */
    private static final ObjectProvider<AutomationFailureRecorder> NO_RECORDER = new ObjectProvider<>() {
        @Override
        public AutomationFailureRecorder getIfAvailable() {
            return null;
        }
    };

    @Test
    void multipleQueuedRowsAllDrainWithinOnePollOnceCall() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(automationPort.ingestedEvents).hasSize(3);
        assertThat(outboxPort.isEmpty()).isTrue();
    }

    @Test
    void ingestExceptionStopsDrainAndLeavesRemainingRowsUnclaimed() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        automationPort.throwOnCall(2); // the second ingested row fails

        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        // Only the first row was successfully ingested; pollOnce() must not rethrow.
        assertThat(automationPort.ingestedEvents).hasSize(1);
        // The second row (whose handler call threw) and the third (never reached) both remain
        // claimable — the queue still holds 2 rows.
        assertThat(outboxPort.remaining()).isEqualTo(2);
    }

    /**
     * Spec 15 D-2. The released core refuses a TransitionFired row with no provenance (the fake below does the same). Without
     * the relay's handling, the first refusal stops the drain and the stamped rows behind it wait. With the handling, the
     * unstamped row is recorded on the host, the relay claims it as done, and the rows behind it are ingested in the same
     * poll.
     */
    @Test
    void anUnstampedTransitionFiredRowIsRecordedAndTheRelayContinuesWithTheNextRow() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        OutboxEntry unstamped = unstampedTransitionFired("tenant-a", "alice");
        outboxPort.enqueue("tenant-a", unstamped);
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        RecordingFailureRecorder recorder = new RecordingFailureRecorder();
        OutboxRelay relay =
                new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, providerOf(recorder));

        relay.pollOnce();

        assertThat(automationPort.ingestedEvents).hasSize(2);
        assertThat(outboxPort.isEmpty()).isTrue();
        assertThat(recorder.records).hasSize(1);
        AutomationFailure record = recorder.records.get(0);
        assertThat(record.workflowId()).isEqualTo(unstamped.id().toString());
        assertThat(record.workflowType()).isEqualTo(OutboxRelay.RELAY_WORKFLOW_TYPE);
        assertThat(record.cause()).isEqualTo(OutboxRelay.UNSTAMPED_TRANSITION_CAUSE);
        assertThat(record.transition()).isNull();
    }

    @Test
    void pollOnceMakesNoOutboxCallsWhenNoAutomationPortIsAvailable() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", transitionFired("tenant-a", "alice"));
        OutboxRelay relay = new OutboxRelay(outboxPort, unavailableProvider(), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(outboxPort.claimAttempts()).isZero();
        assertThat(outboxPort.remaining()).isEqualTo(1);
    }

    @Test
    void scopeHandedToIngestReconstructsTenantAndPrincipalFromPayload() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("acme-tenant", transitionFired("acme-tenant", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(automationPort.ingestedScopes).hasSize(1);
        Scope scope = automationPort.ingestedScopes.get(0);
        assertThat(scope.tenantId().value()).isEqualTo("acme-tenant");
        assertThat(scope.principal().id()).isEqualTo("alice");
        assertThat(scope.principal().roles()).isEmpty();
    }

    @Test
    void transitionFiredRowIsClaimedAndHandedToAutomation() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        OutboxEntry row = transitionFired("tenant-a", "alice");
        outboxPort.enqueue("tenant-a", row);
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(automationPort.ingestedEvents).hasSize(1);
        StoreEvent event = automationPort.ingestedEvents.get(0);
        assertThat(event).isInstanceOf(TransitionFiredEvent.class);
        assertThat(event.id()).as("the event id is the row id").isEqualTo(row.id());
        assertThat(outboxPort.isEmpty()).isTrue();
    }

    /** An {@code ObjectCreated} row is ingested as a {@code CREATED} {@link ObjectChangedEvent}. */
    @Test
    void objectCreatedRowIsIngestedAsCreatedChange() {
        assertLifecycleRowIngestedAs(OutboxEntry.KIND_OBJECT_CREATED, ObjectChangedEvent.Change.CREATED);
    }

    /** An {@code ObjectUpdated} row is ingested as an {@code UPDATED} {@link ObjectChangedEvent}. */
    @Test
    void objectUpdatedRowIsIngestedAsUpdatedChange() {
        assertLifecycleRowIngestedAs(OutboxEntry.KIND_OBJECT_UPDATED, ObjectChangedEvent.Change.UPDATED);
    }

    /** An {@code ObjectDeleted} row is ingested as a {@code DELETED} {@link ObjectChangedEvent}. */
    @Test
    void objectDeletedRowIsIngestedAsDeletedChange() {
        assertLifecycleRowIngestedAs(OutboxEntry.KIND_OBJECT_DELETED, ObjectChangedEvent.Change.DELETED);
    }

    /**
     * The relay claims only its dispatchable kinds. An {@code ActionRequest} row is not one of them,
     * so it stays unprocessed and nothing is ingested for it.
     */
    @Test
    void relayDoesNotClaimActionRequestRows() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", actionRequest());
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), UNUSED_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(automationPort.ingestedEvents).isEmpty();
        assertThat(outboxPort.remaining()).isEqualTo(1);
    }

    /**
     * The recompute must run before the ingress for the same row: the automation adapter's change
     * handling reads the materialised values the recompute writes.
     */
    @Test
    void recomputeRunsBeforeTheIngressForAnObjectUpdatedRow() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", objectLifecycle(OutboxEntry.KIND_OBJECT_UPDATED));
        List<String> order = new ArrayList<>();
        DerivationRecomputer recomputer = (scope, changeEvent) -> order.add("recompute");
        AutomationPort automationPort = (scope, event) -> order.add("ingest");
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), recomputer, NO_RECORDER);

        relay.pollOnce();

        assertThat(order).containsExactly("recompute", "ingest");
        assertThat(outboxPort.isEmpty()).isTrue();
    }

    /**
     * Relays one object-lifecycle row of {@code kind} and asserts the single event the ingress receives
     * is an {@link ObjectChangedEvent} with {@code change}, carrying the row's id, payload and
     * {@code occurredAt}.
     */
    private static void assertLifecycleRowIngestedAs(String kind, ObjectChangedEvent.Change change) {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        OutboxEntry row = objectLifecycle(kind);
        outboxPort.enqueue("tenant-a", row);
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort), NOOP_RECOMPUTER, NO_RECORDER);

        relay.pollOnce();

        assertThat(automationPort.ingestedEvents).hasSize(1);
        StoreEvent event = automationPort.ingestedEvents.get(0);
        assertThat(event).isEqualTo(new ObjectChangedEvent(row.id(), change, row.payload(), row.occurredAt()));
        assertThat(event.id()).as("the event id is the row id").isEqualTo(row.id());
        assertThat(outboxPort.isEmpty()).isTrue();
    }

    /**
     * Builds an object-lifecycle {@link OutboxEntry} carrying the fields the recompute reads
     * ({@code objectId}, {@code typeIri}) plus {@code version}, matching the shape {@code
     * DefaultBusinessObjectService} writes.
     */
    private static OutboxEntry objectLifecycle(String kind) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("typeIri", "https://example.com/ns#Task");
        payload.put("version", 2);
        return new OutboxEntry(UUID.randomUUID(), kind, payload, Instant.now());
    }

    /** Builds a {@code TransitionFired}-kind {@link OutboxEntry} carrying the stamped Ontology version. */
    private static OutboxEntry transitionFired(String tenantId, String principalId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", tenantId);
        payload.put("principalId", principalId);
        payload.put("transitionName", "activate");
        payload.put("fromState", "https://example.com/ns#Draft");
        payload.put("toState", "https://example.com/ns#Active");
        payload.put("ontologyVersion", 1);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());
    }

    /**
     * Builds a legacy {@code ActionRequest}-kind {@link OutboxEntry}, which the relay must not claim.
     * The kind is the literal string: the constant it once named is gone.
     */
    private static OutboxEntry actionRequest() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", "tenant-a");
        payload.put("principalId", "alice");
        return new OutboxEntry(UUID.randomUUID(), "ActionRequest", payload, Instant.now());
    }

    /** Builds a {@code TransitionFired}-kind {@link OutboxEntry} with no {@code ontologyVersion} stamp (spec 15 D-2). */
    private static OutboxEntry unstampedTransitionFired(String tenantId, String principalId) {
        OutboxEntry stamped = transitionFired(tenantId, principalId);
        Map<String, Object> payload = new HashMap<>(stamped.payload());
        payload.remove("ontologyVersion");
        return new OutboxEntry(stamped.id(), stamped.kind(), payload, stamped.occurredAt());
    }

    private static <T> ObjectProvider<T> providerOf(T bean) {
        return new ObjectProvider<>() {
            @Override
            public T getIfAvailable() {
                return bean;
            }
        };
    }

    private static ObjectProvider<AutomationPort> unavailableProvider() {
        return new ObjectProvider<>() {
            @Override
            public AutomationPort getIfAvailable() {
                return null;
            }
        };
    }

    /**
     * A trivial deque-backed {@link OutboxPort}: {@link #claimNext} scans from the head for the
     * first row whose kind is in the caller's requested set (respecting insertion/{@code
     * occurredAt} order, mirroring the real port's {@code ORDER BY occurred_at}), calls {@code
     * handler}, and only removes that row once {@code handler} returns normally — mirroring the
     * real contract's guarantee that a handler exception leaves the row claimable again, as if the
     * claiming transaction had rolled back.
     */
    private static final class InMemoryOutboxPort implements OutboxPort {

        private final Deque<Map.Entry<String, OutboxEntry>> queue = new ArrayDeque<>();
        private int claimAttempts = 0;

        void enqueue(String tenantId, OutboxEntry entry) {
            queue.addLast(Map.entry(tenantId, entry));
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        int remaining() {
            return queue.size();
        }

        int claimAttempts() {
            return claimAttempts;
        }

        @Override
        public <T> Optional<T> claimNext(Set<String> kinds, BiFunction<String, OutboxEntry, T> handler) {
            Objects.requireNonNull(kinds, "kinds must not be null");
            if (kinds.isEmpty()) {
                throw new IllegalArgumentException("kinds must not be empty");
            }
            Objects.requireNonNull(handler, "handler must not be null");

            Iterator<Map.Entry<String, OutboxEntry>> iterator = queue.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, OutboxEntry> candidate = iterator.next();
                if (kinds.contains(candidate.getValue().kind())) {
                    claimAttempts++;
                    T result = handler.apply(candidate.getKey(), candidate.getValue());
                    iterator.remove();
                    return Optional.ofNullable(result);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Records every {@link #ingest} call; can be configured to throw on the Nth call. It also refuses a TransitionFired
     * event with no {@code ontologyVersion}, as the released core does (spec 15 D-2).
     */
    private static final class RecordingAutomationPort implements AutomationPort {

        final List<StoreEvent> ingestedEvents = new ArrayList<>();
        final List<Scope> ingestedScopes = new ArrayList<>();
        private int callCount = 0;
        private int throwOnCall = -1;

        void throwOnCall(int n) {
            this.throwOnCall = n;
        }

        @Override
        public void ingest(Scope scope, StoreEvent event) {
            callCount++;
            if (callCount == throwOnCall) {
                throw new RuntimeException("simulated ingest failure on call " + callCount);
            }
            if (event instanceof TransitionFiredEvent fired && fired.payload().get("ontologyVersion") == null) {
                throw new IllegalStateException("TransitionFired " + fired.id() + " has no ontologyVersion stamp");
            }
            ingestedScopes.add(scope);
            ingestedEvents.add(event);
        }
    }

    /** Records every failure the relay hands the host recorder. */
    private static final class RecordingFailureRecorder implements AutomationFailureRecorder {

        final List<AutomationFailure> records = new ArrayList<>();

        @Override
        public void record(AutomationFailure failure) {
            records.add(failure);
        }
    }
}
