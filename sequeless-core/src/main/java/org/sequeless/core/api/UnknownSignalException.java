package org.sequeless.core.api;

import java.util.Objects;

/**
 * Thrown by {@link TransitionService#signal} when {@code signalName} does not match any {@link
 * org.sequeless.spi.meta.ExternalSignalTrigger#signalName()} declared by any transition anywhere
 * on the resolved type's state machine — checked type-wide, not scoped to the object's current
 * state, exactly as {@code plan.md} §6 describes the REST endpoint's {@code 400}: "a signal name
 * no transition on that type declares".
 *
 * <p>This sits in {@code org.sequeless.core.api} beside {@link TransitionService}, not in
 * {@code sequeless-app}, because {@code sequeless-core} cannot depend on {@code sequeless-app}; a
 * later REST task adds an {@code @ExceptionHandler} mapping this to a 400 response, the same way
 * {@code ApiExceptionAdvice} already maps {@link TransitionNotAvailableException} to a 409.
 */
public final class UnknownSignalException extends RuntimeException {

    private final String typeIri;
    private final String signalName;

    /**
     * @param typeIri the IRI of the type whose state machine declares no transition matching
     *     {@code signalName}; must not be {@code null}
     * @param signalName the signal name that matched no transition; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public UnknownSignalException(String typeIri, String signalName) {
        super(
            "No transition on type '" + Objects.requireNonNull(typeIri, "typeIri must not be null")
                + "' declares signal '"
                + Objects.requireNonNull(signalName, "signalName must not be null") + "'");
        this.typeIri = typeIri;
        this.signalName = signalName;
    }

    /**
     * @return the IRI of the type whose state machine declares no matching transition
     */
    public String typeIri() {
        return typeIri;
    }

    /**
     * @return the signal name that matched no transition
     */
    public String signalName() {
        return signalName;
    }
}
