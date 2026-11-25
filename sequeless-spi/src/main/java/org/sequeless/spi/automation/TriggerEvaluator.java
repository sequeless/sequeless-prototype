package org.sequeless.spi.automation;

import java.util.List;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The inbound port an automation adapter calls back into to evaluate the state machine against a
 * change or a timer, and fire at most one available transition per candidate object — exactly the
 * same two ways {@code ex:ProjectLifecycle} reacts without a user's REST request: {@code autoClose}
 * on a {@code ex:Task} change, and {@code expireHold} on an elapsed timer. A signal is not one of
 * them: a client's signal request fires its transition synchronously, through the transition
 * service. See this package's {@code package-info.java} for why this
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
     * transition in the Behaviour whose {@code sq:watch} names a property declared on the changed
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
     * Fires the timer transition named by a previously scheduled {@code TimerScheduled}-shaped
     * entry, unless the object's current state no longer matches the payload's {@code state},
     * meaning the object left that state while the timer was pending. No cancel is recorded when an
     * object leaves a state, so that staleness check is the only guard against a timer firing twice
     * or against the wrong state.
     *
     * <p>Kind-specific payload fields, beyond {@code objectId}/{@code tenantId}/{@code
     * principalId}: {@code typeIri}, {@code state} (the state the object was in when the timer was
     * scheduled — compared against the object's live current state), {@code transitionName}, and
     * {@code timerKey}.
     *
     * @param scope the tenant and principal to fire on behalf of; must not be {@code null}
     * @param timerScheduled the {@code TimerScheduled}-shaped entry, built with {@link
     *     TimerScheduledEntry#of}, describing the elapsed timer; must not be {@code null}
     */
    void onTimerElapsed(Scope scope, OutboxEntry timerScheduled);

    /**
     * Resolves the timer transitions that a {@code TransitionFired} row starts: one {@link
     * ResolvedTimer} for every {@code TimerTrigger} transition departing the state the row moved
     * the object into ({@code toState}), in declaration order. Each carries that timer's key, its
     * duration, and the state and transition name it waits under. A row whose {@code toState} has
     * no timer transition resolves to an empty list.
     *
     * <p>The lookup reads the active behaviour, so the row's {@code ontologyVersion} stamp must
     * match that behaviour's version. Unlike the three methods above, this method does not treat a
     * bad row as a stale trigger: a missing or mismatched stamp is an error, not a silent no-op.
     *
     * @param scope the tenant to read the behaviour for; must not be {@code null}
     * @param transitionFired the {@code TransitionFired}-kind outbox entry describing the move; must
     *     not be {@code null}
     * @return the timers the row starts, in declaration order; never {@code null}
     * @throws IllegalStateException if the row has no {@code ontologyVersion} stamp, or if the stamp
     *     differs from the active behaviour's ontology version
     */
    List<ResolvedTimer> timersStartedBy(Scope scope, OutboxEntry transitionFired);
}
