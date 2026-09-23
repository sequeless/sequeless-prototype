package org.sequeless.spi.meta;

/**
 * The discriminator for {@link TriggerSpec}'s four permitted variants, mirroring {@code
 * sq:UserAction}/{@code sq:OnChange}/{@code sq:Timer}/{@code sq:ExternalSignal} in the {@code sq:}
 * vocabulary. Kept as its own enum, separate from the {@link TriggerSpec} sealed interface itself,
 * for two reasons that both predate the code that needs them: {@code
 * DefaultTransitionService.fireAutomated(scope, objectId, transitionName, TriggerKind expected)}
 * needs a cheap value to compare against without pattern-matching a whole {@link TriggerSpec}
 * instance just to check its shape, and the REST transition-descriptor DTOs need a plain,
 * JSON-serializable discriminator field (matching how {@link AggregateFunction} already serves
 * {@link RollupRule}) rather than reflecting on which sealed subtype a transition's {@link
 * TriggerSpec} happens to be.
 *
 * @see TriggerSpec
 */
public enum TriggerKind {

    /**
     * A transition whose only trigger is a user's own {@code POST
     * /objects/{type}/{id}/transitions/{name}} request, exactly as {@code activate} moves an
     * {@code ex:Project} from {@code ex:Draft} to {@code ex:Active} today. Paired with {@link
     * UserActionTrigger}.
     */
    USER_ACTION,

    /**
     * A transition that fires when another object's data changes, exactly as {@code autoClose}
     * closes an {@code ex:Project} the moment its last {@code ex:Task} leaves the {@code "done"}
     * filter behind {@code ex:openTaskCount}. Paired with {@link OnChangeTrigger}.
     */
    ON_CHANGE,

    /**
     * A transition that fires once a configured duration has elapsed since the object entered its
     * {@link Transition#fromStateIri()}, exactly as {@code expireHold} closes an {@code
     * ex:Project} 72 hours after it enters {@code ex:OnHold}. Paired with {@link TimerTrigger}.
     */
    TIMER,

    /**
     * A transition that fires when an external signal of a given name arrives for the object,
     * exactly as {@code reopen} revives a {@code ex:Closed} {@code ex:Project} in response to a
     * {@code POST /objects/Project/{id}/signals/reopen} call. Paired with {@link
     * ExternalSignalTrigger}.
     */
    EXTERNAL_SIGNAL
}
