/**
 * The state-machine interpreter shared by two callers: {@link
 * org.sequeless.core.usecase.DefaultTransitionService} (the write path — validates a requested
 * transition against an object's current state and guard, then dispatches it as a {@link
 * org.sequeless.spi.object.OutboxEntry}) and a later REST task's read path ({@code GET
 * .../objects/{type}/{id}/transitions}, not built by this package's originating task), both of
 * which resolve a {@link org.sequeless.spi.meta.TypeDefinition#stateMachine()} against an object's
 * {@link org.sequeless.spi.object.BusinessObject#state()} through {@link
 * org.sequeless.core.statemachine.StateMachineInterpreter#availableTransitions}.
 *
 * <p>{@link org.sequeless.core.statemachine.TransitionAvailability} is the interpreter's
 * per-transition result: whether the transition is currently available from the object's state
 * and, if not, why — the same shape the write path consumes (throwing {@link
 * org.sequeless.core.api.TransitionNotAvailableException} with the {@code reason} text verbatim
 * when a requested transition is unavailable) and the read path returns to a caller as-is.
 *
 * <p>{@link org.sequeless.core.statemachine.PayloadValueCodec} is the {@link
 * org.sequeless.spi.object.Value}-to-outbox-payload codec both {@link
 * org.sequeless.core.usecase.DefaultTransitionService} (the encode side, building each {@code
 * ActionRequest} {@link org.sequeless.spi.object.OutboxEntry}'s payload) and a later sibling {@code
 * org.sequeless.core.automation.ActionExecutor} implementation (the decode side, not built by this
 * package's originating task) use to move property values in and out of the JSON-compatible {@code
 * Map<String, Object>} an outbox payload is built from.
 */
package org.sequeless.core.statemachine;
