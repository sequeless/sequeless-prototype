/**
 * The default, durable {@code AutomationPort} adapter, backed by the Temporal Java SDK: {@link
 * org.sequeless.adapter.automation.temporal.TemporalAutomationPort#dispatch} durably starts, for
 * each {@code TransitionFired} outbox entry, one {@link
 * org.sequeless.adapter.automation.temporal.TransitionActionWorkflow} execution per action of the
 * fired transition, keyed {@code "<entryId>:<index>"}, so each action has its own run and its own
 * idempotency (Temporal's own {@code WorkflowExecutionAlreadyStarted} semantics). Each execution
 * calls {@link org.sequeless.adapter.automation.temporal.ActionActivities#applyTransitionAction},
 * a thin wrapper over an injected {@code ActionExecutor}. The exception is a webhook action, which
 * also performs the real HTTP call and lets Temporal's own configurable {@code RetryOptions} retry
 * it, rather than hand-rolling a retry loop the way the in-process adapter does.
 *
 * <p>{@link org.sequeless.adapter.automation.temporal.ActionWorkflow} is registered on the worker
 * and never started, because dispatch no longer routes {@code ActionRequest} rows to it. P2e
 * deletes it.
 *
 * <p><b>This module deliberately depends on {@code io.temporal:temporal-sdk} directly, never
 * {@code temporal-spring-boot-starter}.</b> The starter was evaluated and rejected: it transitively
 * pulls Spring Boot 2.7.18 ({@code spring-boot-starter}/{@code spring-boot-autoconfigure} at
 * 2.7.18), incompatible with this project's Spring Boot 4.1.1 line, and risks {@code
 * NoSuchMethodError}/{@code ClassNotFoundException} from stale bytecode even after this reactor's
 * {@code dependencyManagement} force-upgrades the jar *versions* to match. {@code temporal-sdk}
 * itself has no Spring dependency at all, so {@link
 * org.sequeless.adapter.automation.temporal.TemporalAutomationAutoConfiguration} hand-rolls its own
 * {@code @AutoConfiguration} against it instead — exactly the same pattern every other adapter in
 * this repo uses ({@code PostgresPersistenceAutoConfiguration}, {@code
 * JexlExpressionAutoConfiguration}). A future reader tempted to add the starter back "to simplify
 * wiring" should re-run {@code mvn dependency:tree} first and see the Boot 2.7.18 jars reappear.
 *
 * <p>{@link org.sequeless.adapter.automation.temporal.ActionWorkflowImpl} has a genuinely tricky
 * constraint worth calling out here too: Temporal instantiates workflow implementations itself,
 * reflectively, per execution, so it cannot take its {@code ActivityOptions} (carrying the
 * configured {@code RetryOptions}) via constructor injection the way {@link
 * org.sequeless.adapter.automation.temporal.ActionActivitiesImpl} takes its {@code ActionExecutor}.
 * {@link org.sequeless.adapter.automation.temporal.TemporalAutomationAutoConfiguration} instead
 * publishes it once, before the worker starts, through a {@code static volatile} field on {@link
 * org.sequeless.adapter.automation.temporal.ActionWorkflowImpl} itself.
 */
package org.sequeless.adapter.automation.temporal;
