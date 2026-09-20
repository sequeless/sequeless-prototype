package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-tests {@link OutboxRelay#pollOnce()} directly against hand-built test doubles — no Spring
 * context, no mocking library, mirroring {@code ApiExceptionAdviceTest}'s convention. {@link
 * InMemoryOutboxPort} is a trivial deque-backed {@link OutboxPort} whose {@code
 * claimNextActionRequest} pops one row at a time and mirrors the real contract's crucial detail: a
 * handler exception leaves the claimed row unremoved (as if the claiming transaction had rolled
 * back). {@link RecordingAutomationPort} records every {@link AutomationPort#dispatch} call and can
 * be told to throw on a specific call.
 */
class OutboxRelayTest {

    @Test
    void multipleQueuedRowsAllDrainWithinOnePollOnceCall() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort));

        relay.pollOnce();

        assertThat(automationPort.dispatchedEntries).hasSize(3);
        assertThat(outboxPort.isEmpty()).isTrue();
    }

    @Test
    void dispatchExceptionStopsDrainAndLeavesRemainingRowsUnclaimed() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        automationPort.throwOnCall(2); // the second dispatched row fails

        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort));

        relay.pollOnce();

        // Only the first row was successfully dispatched; pollOnce() must not rethrow.
        assertThat(automationPort.dispatchedEntries).hasSize(1);
        // The second row (whose handler call threw) and the third (never reached) both remain
        // claimable — the queue still holds 2 rows.
        assertThat(outboxPort.remaining()).isEqualTo(2);
    }

    @Test
    void pollOnceMakesNoOutboxCallsWhenNoAutomationPortIsAvailable() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("tenant-a", actionRequest("tenant-a", "alice"));
        OutboxRelay relay = new OutboxRelay(outboxPort, unavailableProvider());

        relay.pollOnce();

        assertThat(outboxPort.claimAttempts()).isZero();
        assertThat(outboxPort.remaining()).isEqualTo(1);
    }

    @Test
    void scopeHandedToDispatchReconstructsTenantAndPrincipalFromPayload() {
        InMemoryOutboxPort outboxPort = new InMemoryOutboxPort();
        outboxPort.enqueue("acme-tenant", actionRequest("acme-tenant", "alice"));
        RecordingAutomationPort automationPort = new RecordingAutomationPort();
        OutboxRelay relay = new OutboxRelay(outboxPort, providerOf(automationPort));

        relay.pollOnce();

        assertThat(automationPort.dispatchedScopes).hasSize(1);
        Scope scope = automationPort.dispatchedScopes.get(0);
        assertThat(scope.tenantId().value()).isEqualTo("acme-tenant");
        assertThat(scope.principal().id()).isEqualTo("alice");
        assertThat(scope.principal().displayName()).isEqualTo("alice");
        assertThat(scope.principal().roles()).isEmpty();
    }

    /**
     * Builds an {@code ActionRequest}-kind {@link OutboxEntry} matching the payload shape {@code
     * DefaultTransitionService#actionRequestPayload} produces for a {@code Log} action — the
     * simplest of the four action kinds, sufficient here since this test only exercises {@link
     * OutboxRelay}'s own {@code buildScope} logic, never actually resolving the payload through an
     * {@link org.sequeless.spi.automation.ActionExecutor}.
     */
    private static OutboxEntry actionRequest(String tenantId, String principalId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", tenantId);
        payload.put("principalId", principalId);
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", "https://example.com/ns#Project");
        payload.put("state", "https://example.com/ns#Active");
        payload.put("self", Map.of());
        payload.put("actionKind", "Log");
        payload.put("message", "project activated");
        return new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, Instant.now());
    }

    private static ObjectProvider<AutomationPort> providerOf(AutomationPort port) {
        return new ObjectProvider<>() {
            @Override
            public AutomationPort getIfAvailable() {
                return port;
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
     * A trivial deque-backed {@link OutboxPort}: {@link #claimNextActionRequest} peeks (does not
     * remove) the head row, calls {@code handler}, and only removes the row once {@code handler}
     * returns normally — mirroring the real contract's guarantee that a handler exception leaves
     * the row claimable again, as if the claiming transaction had rolled back.
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
        public <T> Optional<T> claimNextActionRequest(BiFunction<String, OutboxEntry, T> handler) {
            Objects.requireNonNull(handler, "handler must not be null");
            Map.Entry<String, OutboxEntry> head = queue.peekFirst();
            if (head == null) {
                return Optional.empty();
            }
            claimAttempts++;
            T result = handler.apply(head.getKey(), head.getValue());
            queue.removeFirst();
            return Optional.ofNullable(result);
        }
    }

    /** Records every {@link #dispatch} call; can be configured to throw on the Nth call. */
    private static final class RecordingAutomationPort implements AutomationPort {

        final List<OutboxEntry> dispatchedEntries = new ArrayList<>();
        final List<Scope> dispatchedScopes = new ArrayList<>();
        private int callCount = 0;
        private int throwOnCall = -1;

        void throwOnCall(int n) {
            this.throwOnCall = n;
        }

        @Override
        public void dispatch(Scope scope, OutboxEntry entry) {
            callCount++;
            if (callCount == throwOnCall) {
                throw new RuntimeException("simulated dispatch failure on call " + callCount);
            }
            dispatchedScopes.add(scope);
            dispatchedEntries.add(entry);
        }
    }
}
