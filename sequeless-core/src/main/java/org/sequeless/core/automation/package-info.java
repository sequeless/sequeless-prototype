/**
 * {@code sequeless-core}'s implementation of the automation side of state machines: {@link
 * org.sequeless.core.automation.DefaultActionExecutor}, the reference implementation of {@link
 * org.sequeless.spi.automation.ActionExecutor}. Sibling to {@code
 * org.sequeless.core.statemachine} (the interpreter and {@code TransitionService} that produce
 * each {@code ActionRequest} outbox row) rather than nested inside it, because this package's one
 * job — applying an already-fired transition's action, on an automation adapter's callback thread
 * — is a distinct responsibility from computing which transitions are available or firing one.
 *
 * <p>This package has exactly one cross-package dependency within {@code sequeless-core} itself:
 * {@link org.sequeless.core.statemachine.PayloadValueCodec}, the {@link
 * org.sequeless.spi.object.Value}-to-outbox-payload codec {@code DefaultTransitionService} uses to
 * encode each {@code ActionRequest} payload's {@code self}/{@code value}/{@code createProperties}
 * fields. {@link org.sequeless.core.automation.DefaultActionExecutor} is the symmetric decode
 * side, calling {@link org.sequeless.core.statemachine.PayloadValueCodec#fromPayload} rather than
 * inventing a second codec, so both sides of the outbox payload format are guaranteed to agree.
 *
 * <p>Every other dependency this package has is on {@code sequeless-spi}: {@link
 * org.sequeless.spi.object.ObjectStorePort} (every mutation {@link
 * org.sequeless.core.automation.DefaultActionExecutor} performs — a property update, a new
 * object's creation — goes through it, exactly like {@code DefaultBusinessObjectService}) and
 * {@link org.sequeless.spi.expression.ExpressionPort} (every {@code value}/{@code expression}/
 * template field an {@code ActionRequest} payload carries is resolved through it, never evaluated
 * by hand here).
 */
package org.sequeless.core.automation;
