/**
 * The mechanical contract test for {@link org.sequeless.spi.automation.AutomationPort}, plus a
 * hand-rolled {@link org.sequeless.spi.automation.ActionExecutor} test double this codebase's
 * no-Mockito convention uses throughout.
 *
 * <p>{@link org.sequeless.testkit.automation.AutomationContract} asserts that {@link
 * org.sequeless.spi.automation.AutomationPort#ingest} applies the actions of a {@code
 * TransitionFired} event, checking that each action reaches {@link
 * org.sequeless.spi.automation.ActionExecutor#applyAction} or {@link
 * org.sequeless.spi.automation.ActionExecutor#resolveWebhookAction} as the event's transition defines
 * it; that a second {@code ingest} of the same event does not apply its {@code Log} action again,
 * on the spy (idempotent redelivery); and null-safety and the ontology-version stamp check. It
 * deliberately does not assert anything about HTTP retry behaviour for webhooks — that is
 * adapter-specific policy, tested separately per adapter.
 *
 * <p>{@link org.sequeless.testkit.automation.RecordingActionExecutor} is the spy every {@code
 * AutomationContract} implementor wires its port under test with, so the contract can observe
 * which {@code ActionExecutor} method — and how many times — {@code ingest} actually invoked.
 */
package org.sequeless.testkit.automation;
