package org.sequeless.spi.automation;

import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The inbound port an automation adapter calls back into to evaluate the state machine against a
 * change, a timer, or a signal, and fire at most one available transition per candidate object —
 * exactly the same three ways {@code ex:ProjectLifecycle} reacts without a user's REST request:
 * {@code autoClose} on a {@code ex:Task} change, {@code expireHold} on an elapsed timer, {@code
 * reopen} on a {@code "reopen"} signal. See this package's {@code package-info.java} for why this
 * port's dependency direction is the mirror image of every outbound port in this SPI: {@code
 * sequeless-core} implements this interface ({@code
 * org.sequeless.core.usecase.DefaultTriggerEvaluator}), and an automation adapter (the in-process
 * relay, or a Temporal activity) calls it, rather than the other way around. All candidate
 * resolution, guard evaluation, and firing happen inside the implementation, using the same
 * private commit path {@code sequeless-core} already uses for a user-triggered {@code fire} — the
 * adapters calling these methods stay deliberately dumb.
 *
 * <p>Every method's {@code entry} payload carries {@code objectId}, {@code tenantId}, and {@code
 * principalId} in every case, reconstructing the {@link Scope} the evaluation runs under exactly
 * as {@link ActionExecutor}'s methods do. A stale trigger — the event that prompted the call no
 * longer describes a transition that is actually available — is always a silent no-op, never an
 * exception: the object may have moved on, been deleted, or already left the state a timer was
 * scheduled against by the time the adapter gets around to calling back in.
 */
public interface TriggerEvaluator {

    /**
     * Evaluates every {@code OnChange}-triggered transition that could react to the object named
     * by {@code changeEvent}'s {@code objectId} having just been created, updated, or deleted.
     * Candidates are resolved two ways: the changed object itself, if its own type declares an
     * {@code OnChange} transition departing its current state; and, for every {@code OnChange}
     * transition in the snapshot whose {@code sq:watch} names a property declared on the changed
     * object's type, every id that property currently holds on the changed object — exactly how
     * closing the last {@code ex:Task} on a {@code ex:Project} resolves that {@code ex:Project} as
     * a candidate via {@code ex:belongsToProject}. A soft-deleted object is still readable for this
     * resolution, so deleting a {@code ex:Task} still correctly retriggers its {@code ex:Project}.
     * For each candidate, at most one available {@code OnChange} transition fires, in declaration
     * order; a candidate with no available {@code OnChange} transition is a silent no-op.
     *
     * @param scope the tenant and principal to evaluate and fire on behalf of; must not be {@code
     *     null}
     * @param changeEvent the {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}-kind
     *     outbox entry describing the change; must not be {@code null}
     */
    void onChange(Scope scope, OutboxEntry changeEvent);

    /**
     * Fires the timer transition named by a previously scheduled {@code TimerScheduled} entry,
     * unless the object's current state no longer matches the payload's {@code state} — meaning
     * the object left that state and a corresponding {@code TimerCancelled} either raced this call
     * or was missed entirely. That staleness check, not cancellation, is the real safety net
     * against a timer firing twice or against the wrong state; cancellation (see {@link
     * org.sequeless.spi.object.OutboxEntry#KIND_TIMER_CANCELLED}) is purely the optimisation that
     * avoids waking a durable timer workflow that both sides already know is moot.
     *
     * <p>Kind-specific payload fields, beyond {@code objectId}/{@code tenantId}/{@code
     * principalId}: {@code typeIri}, {@code state} (the state the object was in when the timer was
     * scheduled — compared against the object's live current state), {@code transitionName}, and
     * {@code timerKey}.
     *
     * @param scope the tenant and principal to fire on behalf of; must not be {@code null}
     * @param timerScheduled the {@code TimerScheduled}-kind outbox entry describing the elapsed
     *     timer; must not be {@code null}
     */
    void onTimerElapsed(Scope scope, OutboxEntry timerScheduled);

    /**
     * Fires the transition departing the object's current state whose {@code
     * ExternalSignalTrigger.signalName()} matches the payload's {@code signalName} — exactly as a
     * {@code "reopen"} signal fires {@code reopen} on a {@code ex:Closed} {@code ex:Project}. A
     * signal naming no matching transition from the object's current state is a silent no-op.
     *
     * <p>Kind-specific payload field, beyond {@code objectId}/{@code tenantId}/{@code
     * principalId}: {@code signalName}.
     *
     * @param scope the tenant and principal to fire on behalf of; must not be {@code null}
     * @param signalReceived the {@code SignalReceived}-kind outbox entry describing the signal;
     *     must not be {@code null}
     */
    void onSignal(Scope scope, OutboxEntry signalReceived);
}
