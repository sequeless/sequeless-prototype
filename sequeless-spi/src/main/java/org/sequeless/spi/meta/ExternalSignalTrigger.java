package org.sequeless.spi.meta;

/**
 * A {@link TriggerSpec} that fires a {@link Transition} when a named external signal arrives for
 * the object ({@code sq:ExternalSignal}, {@code sq:signalName}), exactly as {@code reopen} moves an
 * {@code ex:Project} from {@code ex:Closed} back to {@code ex:Active} in response to a {@code POST
 * /objects/Project/{id}/signals/reopen} call. {@link #signalName()} is matched against the {@code
 * signalName} field of the {@code SignalReceived} outbox entry the signals endpoint writes — see
 * {@code OutboxEntry.KIND_SIGNAL_RECEIVED} — not against the object's own state or properties, so
 * the same signal name can be reused by different transitions on different types.
 *
 * <p>A signal's request body is recorded in the {@code SignalReceived} payload for audit but is not
 * bound into the guard's {@code ExpressionContext} this phase — see {@code
 * docs/architecture/automation.md} for that explicitly stated limitation.
 *
 * @param signalName the signal name to match ({@code sq:signalName}); must not be blank
 */
public record ExternalSignalTrigger(String signalName) implements TriggerSpec {

    public ExternalSignalTrigger {
        if (signalName == null || signalName.isBlank()) {
            throw new IllegalArgumentException("ExternalSignalTrigger signalName must not be blank");
        }
    }

    @Override
    public TriggerKind kind() {
        return TriggerKind.EXTERNAL_SIGNAL;
    }
}
