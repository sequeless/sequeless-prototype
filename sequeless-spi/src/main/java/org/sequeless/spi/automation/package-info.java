/**
 * The {@code ActionExecutor} vocabulary: the one port in this SPI whose dependency direction is
 * the mirror image of every other port so far.
 *
 * <p>Every other {@code sequeless-spi} port ({@link org.sequeless.spi.object.ObjectStorePort},
 * {@link org.sequeless.spi.expression.ExpressionPort}, {@code AuthorizationPort},
 * {@code ValidationPort}, {@code QueryPort}, {@code OntologyPort}, ...) is <em>outbound</em>:
 * {@code sequeless-core} calls it, and an adapter module implements it, so {@code sequeless-core}
 * never depends on adapter-specific libraries. {@link
 * org.sequeless.spi.automation.ActionExecutor} is deliberately the opposite shape: an
 * <em>inbound</em> port that {@code sequeless-core} implements ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}) and that an automation adapter (the
 * in-process relay, or a Temporal activity) calls back into. This is what lets an adapter apply a
 * fired transition's actions — mutate an object, resolve a webhook's templated request, write a
 * log line — using the exact same validated, audited code path {@code sequeless-core} already
 * uses for every other write, without that adapter depending on {@code sequeless-core} itself:
 * the existing {@code noAdapterDependsOnCore} architecture rule stays intact because the adapter
 * depends only on this SPI interface, and {@code sequeless-app}'s config package is the one place
 * that wires the concrete {@code DefaultActionExecutor} bean into each automation adapter.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except sibling {@code org.sequeless.spi.*} types: {@link
 * org.sequeless.spi.Scope} and {@link org.sequeless.spi.object.OutboxEntry} are the only
 * cross-package references this package makes. Every {@link
 * org.sequeless.spi.automation.ActionExecutor} method takes the same two arguments — a {@link
 * org.sequeless.spi.Scope} reconstructed from the {@code OutboxEntry} payload's own {@code
 * tenantId}/{@code principalId} fields, and the {@link org.sequeless.spi.object.OutboxEntry}
 * itself, whose {@code payload()} is the sole source of every field the method needs — because an
 * {@code ActionRequest} payload is frozen, self-contained, and JSON-compatible at transition-fire
 * time, so a retried Temporal activity (or an in-process relay retry) never needs to re-read
 * mutable object state to reproduce the same result. See {@link
 * org.sequeless.spi.automation.ActionExecutor}'s own javadoc for the exact payload shape each
 * method expects. {@link org.sequeless.spi.automation.ResolvedWebhookRequest} is the return shape
 * {@link org.sequeless.spi.automation.ActionExecutor#resolveWebhook} hands back once a {@code
 * sq:Webhook} action's templated {@code url}/{@code method}/{@code body} fields have been
 * rendered — this package performs no HTTP I/O itself, leaving the actual call to whichever
 * automation adapter invoked {@code resolveWebhook}.
 */
package org.sequeless.spi.automation;
