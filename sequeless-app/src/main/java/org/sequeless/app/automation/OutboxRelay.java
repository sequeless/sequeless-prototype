package org.sequeless.app.automation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A thin {@code @Scheduled} poller that drains the full dispatchable outbox kind set — {@link
 * OutboxEntry#KIND_ACTION_REQUEST}, the three object-lifecycle kinds, {@link
 * OutboxEntry#KIND_TRANSITION_FIRED}, and the three automation kinds ({@link
 * OutboxEntry#KIND_TIMER_SCHEDULED}, {@link OutboxEntry#KIND_TIMER_CANCELLED}, {@link
 * OutboxEntry#KIND_SIGNAL_RECEIVED}) — one row at a time, into whichever {@link AutomationPort}
 * adapter is currently configured. The automation adapters accept {@link
 * OutboxEntry#KIND_TRANSITION_FIRED} as a no-op until it carries actions and timers.
 *
 * <p><b>Recompute before the ingress.</b> For each object-lifecycle row ({@link
 * OutboxEntry#KIND_OBJECT_CREATED}, {@link OutboxEntry#KIND_OBJECT_UPDATED}, {@link
 * OutboxEntry#KIND_OBJECT_DELETED}), {@link #pollOnce()} calls {@link
 * DerivationRecomputer#recompute} synchronously, in the same claim, before it dispatches that row to
 * the {@link AutomationPort}. The automation adapter's change handling therefore reads materialised
 * values that are already fresh.
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

    private static final Set<String> DISPATCHABLE_KINDS = Set.of(
            OutboxEntry.KIND_ACTION_REQUEST,
            OutboxEntry.KIND_OBJECT_CREATED,
            OutboxEntry.KIND_OBJECT_UPDATED,
            OutboxEntry.KIND_OBJECT_DELETED,
            OutboxEntry.KIND_TRANSITION_FIRED,
            OutboxEntry.KIND_TIMER_SCHEDULED,
            OutboxEntry.KIND_TIMER_CANCELLED,
            OutboxEntry.KIND_SIGNAL_RECEIVED);

    private static final Set<String> OBJECT_LIFECYCLE_KINDS =
            Set.of(OutboxEntry.KIND_OBJECT_CREATED, OutboxEntry.KIND_OBJECT_UPDATED, OutboxEntry.KIND_OBJECT_DELETED);

    private final OutboxPort outboxPort;
    private final ObjectProvider<AutomationPort> automationPortProvider;
    private final DerivationRecomputer derivationRecomputer;

    /**
     * @param outboxPort the lazily-resolved outbox port adapter; {@code @Lazy} for the same reason
     *     every port parameter in {@code CoreConfiguration} is {@code @Lazy} — it lets this bean
     *     construct cleanly in Spring's first singleton pass regardless of how many {@code
     *     OutboxPort} beans exist, leaving {@code PortRegistry}'s second-pass check as the first
     *     thing that actually counts them and reports a misconfiguration
     * @param automationPortProvider resolved lazily on every {@link #pollOnce()} call; see this
     *     class's javadoc for why this must not be collapsed into a plain constructor parameter
     * @param derivationRecomputer the recompute run before each object-lifecycle row is dispatched;
     *     {@code @Lazy} for the same reason as {@code outboxPort}, so this bean constructs in
     *     Spring's first singleton pass and the bean is resolved only when a row needs it
     * @throws NullPointerException if any argument is {@code null}
     */
    public OutboxRelay(
            @Lazy OutboxPort outboxPort,
            ObjectProvider<AutomationPort> automationPortProvider,
            @Lazy DerivationRecomputer derivationRecomputer) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.automationPortProvider =
                Objects.requireNonNull(automationPortProvider, "automationPortProvider must not be null");
        this.derivationRecomputer =
                Objects.requireNonNull(derivationRecomputer, "derivationRecomputer must not be null");
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
     * "poison pill" row whose payload always makes the recompute or {@link AutomationPort#dispatch}
     * throw will therefore block every row behind it indefinitely, at one-tick granularity. This is an
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
                Optional<Boolean> claimed = outboxPort.claimNext(DISPATCHABLE_KINDS, (tenantId, entry) -> {
                    Scope scope = buildScope(tenantId, entry);
                    if (OBJECT_LIFECYCLE_KINDS.contains(entry.kind())) {
                        // Recompute first, in this same claim, so the ingress below reads fresh values.
                        derivationRecomputer.recompute(scope, entry);
                    }
                    automationPort.dispatch(scope, entry);
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
     * The placeholder principal id/display name used to dispatch an object-lifecycle
     * ({@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}) outbox row, whose payload
     * — unlike {@code ActionRequest}, {@code TimerScheduled}, and {@code SignalReceived} — carries
     * no {@code principalId} at all (verified against {@code DefaultBusinessObjectService}/{@code
     * DefaultActionExecutor}'s shared {@code outboxPayload} helper). There is no richer identity to
     * recover from that frozen payload, and {@code Principal}'s compact constructor rejects a
     * blank/{@code null} id outright, so a placeholder is required, not merely convenient. Safe
     * because the only place {@link Scope#principal()} is read for these three kinds is as an audit
     * "updated by" stamp ({@code DefaultDerivationRecomputer}'s commit path) — never for an
     * authorization decision.
     */
    private static final String LIFECYCLE_EVENT_PRINCIPAL_ID = "system";

    /**
     * Reconstructs the {@link Scope} an outbox row's dispatch must run under, from {@code tenantId}
     * (handed back raw by {@link OutboxPort}, by design — see its javadoc) and the payload's own
     * {@code principalId} where one is present.
     *
     * <p>{@code principalId} is used as {@link Principal#id()}: there is no richer identity to
     * recover from the frozen outbox
     * payload, and no "system principal" concept exists anywhere in this codebase. {@code roles} is
     * the empty set — safe because {@code DefaultActionExecutor}, the {@link
     * org.sequeless.spi.automation.ActionExecutor} implementation every automation adapter calls
     * back into, performs no {@link org.sequeless.spi.authz.AuthorizationPort} check of its own.
     *
     * <p>The three object-lifecycle kinds carry no {@code principalId} in their payload at all —
     * {@link #LIFECYCLE_EVENT_PRINCIPAL_ID} stands in for it in that case, since {@link Principal}'s
     * compact constructor rejects a blank id and there is nothing else to reconstruct it from.
     */
    private static Scope buildScope(String tenantId, OutboxEntry entry) {
        String principalId = (String) entry.payload().get("principalId");
        if (principalId == null) {
            principalId = LIFECYCLE_EVENT_PRINCIPAL_ID;
        }
        Principal principal = new Principal(principalId, Set.of());
        return new Scope(new TenantId(tenantId), principal);
    }
}
