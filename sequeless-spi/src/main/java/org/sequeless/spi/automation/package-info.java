/**
 * The automation vocabulary: three inbound ports — {@link org.sequeless.spi.automation.ActionExecutor},
 * {@link org.sequeless.spi.automation.TriggerEvaluator}, and {@link
 * org.sequeless.spi.automation.DerivationRecomputer} — whose dependency direction is the mirror
 * image of every other port in this SPI, plus one ordinary outbound port, {@link
 * org.sequeless.spi.automation.AutomationPort}, that composes with all three.
 *
 * <p>Every other {@code sequeless-spi} port ({@link org.sequeless.spi.object.ObjectStorePort},
 * {@link org.sequeless.spi.expression.ExpressionPort}, {@code AuthorizationPort},
 * {@code ValidationPort}, {@code QueryPort}, {@code OntologyPort}, ...) is <em>outbound</em>:
 * {@code sequeless-core} calls it, and an adapter module implements it, so {@code sequeless-core}
 * never depends on adapter-specific libraries. The three inbound ports in this package are
 * deliberately the opposite shape: {@code sequeless-core} implements each one ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}, {@code
 * org.sequeless.core.usecase.DefaultTriggerEvaluator}, {@code
 * org.sequeless.core.usecase.DefaultDerivationRecomputer}) and an automation adapter (the
 * in-process relay, or a Temporal activity/workflow) calls back into whichever one a claimed
 * outbox row's kind calls for. This is what lets an adapter apply a fired transition's actions —
 * mutate an object, resolve a webhook's templated request, write a log line — evaluate a change,
 * timer, or signal against the state machine, or recompute a materialised derived property, all
 * using the exact same validated, audited code path {@code sequeless-core} already uses for every
 * other write, without that adapter depending on {@code sequeless-core} itself: the existing
 * {@code noAdapterDependsOnCore} architecture rule stays intact because the adapter depends only
 * on this SPI, and {@code sequeless-app}'s config package is the one place that wires the three
 * concrete beans into each automation adapter.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except sibling {@code org.sequeless.spi.*} types: {@link
 * org.sequeless.spi.Scope} and {@link org.sequeless.spi.object.OutboxEntry} are the only
 * cross-package references this package makes. Every method on all three inbound ports takes the
 * same two arguments — a {@link org.sequeless.spi.Scope} reconstructed from the {@code
 * OutboxEntry} payload's own {@code tenantId}/{@code principalId} fields, and the {@link
 * org.sequeless.spi.object.OutboxEntry} itself, whose {@code payload()} is the sole source of
 * every field the method needs — because every outbox payload is frozen, self-contained, and
 * JSON-compatible at write time, so a retried Temporal activity (or an in-process relay retry)
 * never needs to re-read mutable object state to reproduce the same result. See {@link
 * org.sequeless.spi.automation.ActionExecutor}, {@link org.sequeless.spi.automation.TriggerEvaluator},
 * and {@link org.sequeless.spi.automation.DerivationRecomputer}'s own javadoc for the exact
 * payload shape each method expects. {@link org.sequeless.spi.automation.ResolvedWebhookRequest}
 * is the return shape {@link org.sequeless.spi.automation.ActionExecutor#resolveWebhook} hands
 * back once a {@code sq:Webhook} action's templated {@code url}/{@code method}/{@code body}
 * fields have been rendered — this package performs no HTTP I/O itself, leaving the actual call
 * to whichever automation adapter invoked {@code resolveWebhook}.
 *
 * <p>{@link org.sequeless.spi.automation.AutomationPort} is, by contrast, an ordinary
 * <em>outbound</em> port shaped like every other port in this SPI: a relay calls {@link
 * org.sequeless.spi.automation.AutomationPort#dispatch}, and an automation adapter implements it,
 * itself switching on {@code entry.kind()} and calling back into whichever of the three inbound
 * ports actually applies that kind. The ports compose in opposite directions around the same
 * {@link org.sequeless.spi.object.OutboxEntry} row: {@code dispatch} is how the work gets durably
 * started, the inbound ports' methods are how it actually gets applied.
 */
package org.sequeless.spi.automation;
