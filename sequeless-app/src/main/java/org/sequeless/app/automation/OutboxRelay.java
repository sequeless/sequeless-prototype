package org.sequeless.app.automation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A thin {@code @Scheduled} poller that drains the full dispatchable outbox kind set — {@link
 * OutboxEntry#KIND_ACTION_REQUEST}, the three object-lifecycle kinds, and the three automation
 * kinds ({@link OutboxEntry#KIND_TIMER_SCHEDULED}, {@link OutboxEntry#KIND_TIMER_CANCELLED}, {@link
 * OutboxEntry#KIND_SIGNAL_RECEIVED}) — one row at a time, into whichever {@link AutomationPort}
 * adapter is currently configured. {@link OutboxEntry#KIND_TRANSITION_FIRED} is deliberately never
 * included: it is audit-only and nothing ever claims it.
 *
 * <p><b>Why an {@link ObjectProvider}, not a plain {@code AutomationPort} constructor
 * parameter.</b> {@code AutomationPort} is supplied by an {@code @AutoConfiguration} class ({@code
 * InProcessAutomationAutoConfiguration} or {@code TemporalAutomationAutoConfiguration}), registered
 * via Spring Boot's deferred-import mechanism. A component-scanned bean's own {@code @Conditional}
 * (were this class instead gated with {@code @ConditionalOnBean(AutomationPort.class)}) is
 * evaluated before deferred auto-configurations are fully processed relative to it, so that
 * annotation would be at real risk of seeing "absent" even when an automation adapter is in fact
 * configured — Spring Boot's own documentation restricts {@code @ConditionalOnBean} to
 * auto-configuration classes for exactly this reason. Resolving the port lazily through {@link
 * ObjectProvider#getIfAvailable()}, at actual {@link #pollOnce()} call time — long after the whole
 * context has been fully assembled — sidesteps the ordering hazard entirely, and also lets this
 * relay run harmlessly (never touching the database) in any deployment or test that never
 * configures {@code sequeless.automation.adapter} at all.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private static final Set<String> DISPATCHABLE_KINDS =
        Set.of(
            OutboxEntry.KIND_ACTION_REQUEST,
            OutboxEntry.KIND_OBJECT_CREATED,
            OutboxEntry.KIND_OBJECT_UPDATED,
            OutboxEntry.KIND_OBJECT_DELETED,
            OutboxEntry.KIND_TIMER_SCHEDULED,
            OutboxEntry.KIND_TIMER_CANCELLED,
            OutboxEntry.KIND_SIGNAL_RECEIVED);

    private final OutboxPort outboxPort;
    private final ObjectProvider<AutomationPort> automationPortProvider;

    /**
     * @param outboxPort the lazily-resolved outbox port adapter; {@code @Lazy} for the same reason
     *     every port parameter in {@code CoreConfiguration} is {@code @Lazy} — it lets this bean
     *     construct cleanly in Spring's first singleton pass regardless of how many {@code
     *     OutboxPort} beans exist, leaving {@code PortRegistry}'s second-pass check as the first
     *     thing that actually counts them and reports a misconfiguration
     * @param automationPortProvider resolved lazily on every {@link #pollOnce()} call; see this
     *     class's javadoc for why this must not be collapsed into a plain constructor parameter
     * @throws NullPointerException if either argument is {@code null}
     */
    public OutboxRelay(
            @Lazy OutboxPort outboxPort, ObjectProvider<AutomationPort> automationPortProvider) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.automationPortProvider =
            Objects.requireNonNull(
                automationPortProvider, "automationPortProvider must not be null");
    }

    /**
     * Drains {@link #DISPATCHABLE_KINDS}, dispatching one row at a time, until either the queue is
     * empty or a dispatch fails.
     *
     * <p>Checks {@link ObjectProvider#getIfAvailable()} before touching {@link #outboxPort} at
     * all: a cheap short-circuit so a deployment or test that never configures an automation
     * adapter never polls the database for nothing, every tick.
     *
     * <p><b>Drain-then-break, not drain-forever-ignoring-errors.</b> {@link OutboxPort}'s own
     * javadoc guarantees that a handler exception rolls back the claim transaction — the row is
     * left unprocessed and its lock released — and propagates to this caller. This method catches
     * at the loop level and <em>breaks</em> (does not continue) on any such exception: it stops
     * hammering a possibly-systemic failure (e.g. the Temporal server being down) within one tick,
     * relying on the next {@code @Scheduled} tick to retry naturally rather than busy-looping
     * against the same failure.
     *
     * <p><b>Known, accepted limitation.</b> {@link OutboxPort#claimNext} always claims the oldest
     * unprocessed row (among {@link #DISPATCHABLE_KINDS}) first, and there is no dead-letter or
     * skip-ahead mechanism yet
     * ({@code sq_outbox.attempts} exists but is write-only per an earlier phase's finding). A single
     * "poison pill" row whose payload always makes {@link AutomationPort#dispatch} throw will
     * therefore block every row behind it indefinitely, at one-tick granularity. This is an
     * accepted limitation for this phase, not something this task solves.
     */
    @Scheduled(fixedDelayString = "${sequeless.automation.relay.poll-interval-ms:1000}")
    public void pollOnce() {
        AutomationPort automationPort = automationPortProvider.getIfAvailable();
        if (automationPort == null) {
            return; // no automation adapter configured; nothing to relay
        }
        while (true) {
            try {
                Optional<Boolean> claimed =
                    outboxPort.claimNext(
                        DISPATCHABLE_KINDS,
                        (tenantId, entry) -> {
                            automationPort.dispatch(buildScope(tenantId, entry), entry);
                            // Non-null sentinel: OutboxPort#claimNext returns
                            // Optional.empty() both when no row was available AND (per its own
                            // javadoc) if handler legitimately returned null, so the handler must
                            // return a non-null value to make "a row was claimed and dispatched"
                            // distinguishable from "the queue is empty".
                            return Boolean.TRUE;
                        });
                if (claimed.isEmpty()) {
                    break; // queue drained
                }
            } catch (RuntimeException e) {
                LOG.warn("Failed to dispatch one outbox action; leaving it for a later poll", e);
                break;
            }
        }
    }

    /**
     * Reconstructs the {@link Scope} an {@code ActionRequest} row's action must be dispatched
     * under, from {@code tenantId} (handed back raw by {@link OutboxPort}, by design — see its
     * javadoc) and the payload's own {@code principalId}.
     *
     * <p>{@code principalId} is used as both {@link Principal#id()} and {@link
     * Principal#displayName()}: there is no richer identity to recover from the frozen outbox
     * payload, and no "system principal" concept exists anywhere in this codebase. {@code roles} is
     * the empty set — safe because {@code DefaultActionExecutor}, the {@link
     * org.sequeless.spi.automation.ActionExecutor} implementation every automation adapter calls
     * back into, performs no {@link org.sequeless.spi.authz.AuthorizationPort} check of its own.
     */
    private static Scope buildScope(String tenantId, OutboxEntry entry) {
        String principalId = (String) entry.payload().get("principalId");
        Principal principal = new Principal(principalId, principalId, Set.of());
        return new Scope(new TenantId(tenantId), principal);
    }
}
