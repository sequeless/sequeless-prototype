/**
 * The mechanical contract test for {@link org.sequeless.spi.automation.AutomationPort}, plus a
 * hand-rolled {@link org.sequeless.spi.automation.ActionExecutor} test double this codebase's
 * no-Mockito convention uses throughout.
 *
 * <p>{@link org.sequeless.testkit.automation.AutomationContract} asserts that {@link
 * org.sequeless.spi.automation.AutomationPort#dispatch} applies the actions of a {@code
 * TransitionFired} row, checking that each action reaches {@link
 * org.sequeless.spi.automation.ActionExecutor#applyAction} or {@link
 * org.sequeless.spi.automation.ActionExecutor#resolveWebhookAction} as the row's transition defines
 * it; that a second {@code dispatch} of the same row does not apply its {@code Log} action again,
 * on the spy (idempotent redispatch); and null-safety/kind-validation. It deliberately does not
 * assert anything about HTTP retry behaviour for webhooks — that is adapter-specific policy,
 * tested separately per adapter.
 *
 * <p>{@link org.sequeless.testkit.automation.RecordingActionExecutor} is the spy every {@code
 * AutomationContract} implementor wires its port under test with, so the contract can observe
 * which {@code ActionExecutor} method — and how many times — {@code dispatch} actually invoked.
 */
package org.sequeless.testkit.automation;
