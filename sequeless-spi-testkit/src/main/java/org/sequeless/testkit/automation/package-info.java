/**
 * The mechanical contract test for {@link org.sequeless.spi.automation.AutomationPort}, plus a
 * hand-rolled {@link org.sequeless.spi.automation.ActionExecutor} test double this codebase's
 * no-Mockito convention uses throughout.
 *
 * <p>{@link org.sequeless.testkit.automation.AutomationContract} asserts that {@link
 * org.sequeless.spi.automation.AutomationPort#dispatch} routes each of the four {@code
 * ActionRequest} action kinds ({@code SetProperty}, {@code CreateObject}, {@code Webhook}, {@code
 * Log}) to the matching {@link org.sequeless.spi.automation.ActionExecutor} method, that a second
 * {@code dispatch} call for the same {@link org.sequeless.spi.object.OutboxEntry#id()} is a no-op
 * on the spy (idempotent redispatch), and null-safety/kind-validation. It deliberately does not
 * assert anything about HTTP retry behaviour for webhooks — that is adapter-specific policy,
 * tested separately per adapter.
 *
 * <p>{@link org.sequeless.testkit.automation.RecordingActionExecutor} is the spy every {@code
 * AutomationContract} implementor wires its port under test with, so the contract can observe
 * which {@code ActionExecutor} method — and how many times — {@code dispatch} actually invoked.
 */
package org.sequeless.testkit.automation;
