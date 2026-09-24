package org.sequeless.testkit.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.object.OutboxEntry;

/**
 * A hand-rolled {@link DerivationRecomputer} test double (this codebase uses no Mockito),
 * mirroring {@link RecordingActionExecutor}'s exact idiom: it records every call it receives
 * instead of doing anything else, so a contract test — or an adapter's own test — can assert that
 * {@link org.sequeless.spi.automation.AutomationPort#dispatch} routed an object-lifecycle outbox
 * entry to {@link #recompute}, and how many times.
 *
 * <p>Each recorded {@link Call} additionally carries {@link Call#sequenceNanos()} — see {@link
 * RecordingTriggerEvaluator}'s javadoc for why: it lets {@link AutomationContract} prove that a
 * recompute call for a given change event happened before the corresponding {@code onChange} call
 * on a separately constructed {@link RecordingTriggerEvaluator}, with no shared state between the
 * two doubles.
 *
 * <p>Backed by a thread-safe list: a future Temporal activity implementation may call back into
 * this recomputer off the dispatching thread, so {@link #calls()} must remain safe to read and
 * write concurrently.
 */
public final class RecordingDerivationRecomputer implements DerivationRecomputer {

    /**
     * One recorded {@link DerivationRecomputer#recompute} call.
     *
     * @param entryId the {@link OutboxEntry#id()} the call was made with
     * @param sequenceNanos a {@link System#nanoTime()} reading taken when the call was recorded,
     *     usable only for relative ordering within a single JVM run, never as a wall-clock value
     */
    public record Call(UUID entryId, long sequenceNanos) {}

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recompute(Scope scope, OutboxEntry changeEvent) {
        calls.add(new Call(changeEvent.id(), System.nanoTime()));
    }

    /** @return every call recorded so far, in invocation order; a snapshot, not a live view */
    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }
}
