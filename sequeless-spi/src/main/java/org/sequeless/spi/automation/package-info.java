/**
 * The automation vocabulary: three inbound ports — {@link org.sequeless.spi.automation.ActionExecutor},
 * {@link org.sequeless.spi.automation.TriggerEvaluator}, and {@link
 * org.sequeless.spi.automation.DerivationRecomputer} — whose dependency direction is the mirror
 * image of every other port in this SPI, plus one ordinary outbound port, {@link
 * org.sequeless.spi.automation.AutomationPort}, that composes with all three.
 *
 * <p>Every other {@code sequeless-spi} port ({@link org.sequeless.spi.object.ObjectStorePort},
 * {@code org.sequeless.expression.api.ExpressionPort}, {@code AuthorizationPort},
 * {@code ValidationPort}, {@code QueryPort}, {@code OntologyAdministration}, ...) is <em>outbound</em>:
 * {@code sequeless-core} calls it, and an adapter module implements it, so {@code sequeless-core}
 * never depends on adapter-specific libraries. The three inbound ports in this package are
 * deliberately the opposite shape: {@code sequeless-core} implements each one ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}, {@code
 * org.sequeless.core.usecase.DefaultTriggerEvaluator}, {@code
 * org.sequeless.core.usecase.DefaultDerivationRecomputer}) and an automation adapter (the
 * in-process relay, or a Temporal activity/workflow) calls back into whichever one the Store event
 * calls for. This is what lets an adapter apply a fired transition's actions —
 * mutate an object, resolve a webhook's templated request, write a log line — evaluate a change,
 * or timer against the state machine, or recompute a materialised derived property, all
 * using the exact same validated, audited code path {@code sequeless-core} already uses for every
 * other write, without that adapter depending on {@code sequeless-core} itself: the existing
 * {@code noAdapterDependsOnCore} architecture rule stays intact because the adapter depends only
 * on this SPI, and {@code sequeless-app}'s config package is the one place that wires the three
 * concrete beans into each automation adapter.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except sibling {@code org.sequeless.spi.*} types: {@link
 * org.sequeless.kernel.identity.Scope}, the {@link StoreEvent} types, and {@link
 * org.sequeless.spi.outbox.OutboxEntry} are the only cross-package references this package makes.
 * The inbound ports take a {@link org.sequeless.kernel.identity.Scope} first, reconstructed from the
 * payload's own {@code tenantId}/{@code principalId} fields. {@link TriggerEvaluator#onChange} and
 * {@link TriggerEvaluator#timersStartedBy} then take a typed Store event, {@link ObjectChangedEvent}
 * or {@link TransitionFiredEvent}. {@link TriggerEvaluator#onTimerElapsed}, {@link
 * DerivationRecomputer#recompute} and the {@link ActionExecutor} methods still take an {@link
 * org.sequeless.spi.outbox.OutboxEntry}: a timer that elapses is not a Store event, and the action
 * path works on the outbox row that an adapter rebuilds from a {@code TransitionFired} event. Every
 * outbox payload is frozen, self-contained, and JSON-compatible at write time, so a retried Temporal
 * activity (or an in-process relay retry) never needs to re-read mutable object state to reproduce
 * the same result. See {@link org.sequeless.spi.automation.ActionExecutor}, {@link
 * org.sequeless.spi.automation.TriggerEvaluator}, and {@link
 * org.sequeless.spi.automation.DerivationRecomputer}'s own javadoc for the exact payload shape each
 * method expects. {@link org.sequeless.spi.automation.ResolvedWebhookRequest} is the return shape
 * {@link org.sequeless.spi.automation.ActionExecutor#resolveWebhookAction} hands back once a {@code
 * sq:Webhook} action's templated {@code url}/{@code method}/{@code body} fields have been rendered —
 * this package performs no HTTP I/O itself, leaving the actual call to whichever automation adapter
 * invoked {@code resolveWebhookAction}.
 *
 * <p>{@link org.sequeless.spi.automation.AutomationPort} is, by contrast, an ordinary
 * <em>outbound</em> port shaped like every other port in this SPI: the relay calls {@link
 * org.sequeless.spi.automation.AutomationPort#ingest} with a {@link StoreEvent}, and an automation
 * adapter implements it, matching on the sealed event type and calling back into the inbound ports
 * that apply it. The relay runs {@link org.sequeless.spi.automation.DerivationRecomputer#recompute}
 * for object lifecycle rows before it ingests them, so no adapter calls the recomputer.
 */
package org.sequeless.spi.automation;
