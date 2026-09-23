package org.sequeless.spi.meta;

/**
 * What causes a {@link Transition} to become eligible to fire ({@code sq:trigger}): a user's own
 * REST request ({@link UserActionTrigger}, {@code sq:UserAction}), a change to another object's
 * data ({@link OnChangeTrigger}, {@code sq:OnChange}), an elapsed duration ({@link TimerTrigger},
 * {@code sq:Timer}), or a named external signal ({@link ExternalSignalTrigger}, {@code
 * sq:ExternalSignal}) — exactly the four ways {@code ex:ProjectLifecycle} moves a project through
 * its states: a user calls {@code activate}; {@code autoClose} reacts to a {@code ex:Task}
 * changing; {@code expireHold} reacts to 72 hours passing in {@code ex:OnHold}; {@code reopen}
 * reacts to a {@code "reopen"} signal. Sealed to exactly these four permitted implementations, so a
 * {@code switch} over {@code TriggerSpec} is exhaustive without a default case, mirroring {@link
 * Action} and {@link DerivationRule}.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because each permitted type
 * lives in its own file, not nested inside this one; implicit permits only works for subtypes
 * nested in the sealed type's own file.
 *
 * @see TriggerKind
 */
public sealed interface TriggerSpec
    permits UserActionTrigger, OnChangeTrigger, TimerTrigger, ExternalSignalTrigger {

    /**
     * @return the {@link TriggerKind} this instance is the payload for; never {@code null}, and in
     *     one-to-one correspondence with which of the four permitted implementations {@code this}
     *     is — see each implementation's own {@code kind()} override
     */
    TriggerKind kind();
}
