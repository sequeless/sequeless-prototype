package org.sequeless.core.api;

import java.util.Objects;

/**
 * Thrown by {@link TransitionService#fire} when the requested transition cannot be fired: the
 * name does not match any transition on the resolved type's state machine, the matching
 * transition does not depart from the object's current state, or it does but its guard evaluated
 * to {@code false}. These three cases are differentiated only by {@link #reason()}'s text, not by
 * a distinct exception type or code, mirroring {@link
 * org.sequeless.core.statemachine.TransitionAvailability}'s own single {@code reason} field.
 *
 * <p>This sits in {@code org.sequeless.core.api} beside {@link TransitionService}, not in
 * {@code sequeless-app}, because {@code sequeless-core} cannot depend on {@code sequeless-app}; a
 * later REST task adds an {@code @ExceptionHandler} mapping this to a 409 response, exactly as
 * {@code ApiExceptionAdvice} already does for {@link org.sequeless.spi.object.StaleObjectException}
 * and {@link org.sequeless.core.AuthorizationException}.
 */
public final class TransitionNotAvailableException extends RuntimeException {

    private final String transitionName;
    private final String reason;

    /**
     * @param transitionName the name of the transition that could not be fired; must not be
     *     {@code null}
     * @param reason why the transition could not be fired, shown verbatim to the caller; must not
     *     be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public TransitionNotAvailableException(String transitionName, String reason) {
        super(reason);
        this.transitionName =
            Objects.requireNonNull(transitionName, "transitionName must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * @return the name of the transition that could not be fired
     */
    public String transitionName() {
        return transitionName;
    }

    /**
     * @return why the transition could not be fired
     */
    public String reason() {
        return reason;
    }
}
