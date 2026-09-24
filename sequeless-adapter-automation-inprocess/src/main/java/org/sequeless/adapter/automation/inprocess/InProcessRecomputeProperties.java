package org.sequeless.adapter.automation.inprocess;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code sequeless.automation.recompute.*} configuration namespace: which concurrency
 * strategy {@link RecomputeCoordinator} applies around every {@link
 * org.sequeless.spi.automation.DerivationRecomputer#recompute} call {@link InProcessAutomationPort}
 * makes for an object-lifecycle outbox entry. See {@link Mode} for what each value means.
 */
@ConfigurationProperties("sequeless.automation.recompute")
public class InProcessRecomputeProperties {

    /** Which concurrency strategy {@link RecomputeCoordinator} applies. */
    public enum Mode {
        /**
         * Calls {@link org.sequeless.spi.automation.DerivationRecomputer#recompute} directly,
         * synchronously, on the calling (relay) thread, with no extra coordination — {@code
         * DefaultDerivationRecomputer}'s own intra-call {@code StaleObjectException} retry loop is
         * the only guard against a racing writer. Simplest, and correct on its own because every
         * retry re-reads and re-aggregates from scratch; also the only mode that keeps dispatch's
         * recompute-then-onChange ordering synchronous within a single dispatch call, which is why
         * the contract tests configure this mode explicitly rather than relying on the default.
         */
        RETRY_ONLY,

        /**
         * Keys work by the changed object's id and acquires a per-key lock before calling {@link
         * org.sequeless.spi.automation.DerivationRecomputer#recompute}, so two recomputes for the
         * same target never overlap. Still synchronous/blocking on the calling thread.
         */
        SERIALIZE,

        /**
         * {@link #SERIALIZE}'s per-key exclusivity plus single-flight collapsing: a recompute
         * request for a key already in flight does not block the calling thread — it marks "one
         * more pending" and returns immediately, and the in-flight runner re-runs once more after
         * finishing if a pending request arrived. Safe because {@code
         * DefaultDerivationRecomputer#recompute} always reads fresh state, so the surviving run
         * sees the final value.
         */
        COALESCE
    }

    /**
     * Which concurrency strategy to apply. Defaults to {@link Mode#COALESCE}: fastest and least
     * wasteful under real load (200 racing Task updates against one Project is exactly the shape
     * {@link Mode#COALESCE} exists for), and safe by construction — every recompute re-reads and
     * re-aggregates from scratch, so a coalesced run always converges on the value true as of the
     * moment it actually runs. This default trades away same-dispatch recompute-before-onChange
     * ordering (see {@link Mode#RETRY_ONLY}'s javadoc): under {@link Mode#COALESCE} a single change
     * event's own {@code onChange} call may see a value one hop stale, but the recompute's own
     * commit produces a further {@code ObjectUpdated} event that retriggers evaluation against the
     * now-current value, so the system still converges — just, occasionally, one extra outbox
     * round-trip later. The contract tests that assert the tighter same-dispatch ordering guarantee
     * configure {@link Mode#RETRY_ONLY} explicitly rather than relying on this default.
     */
    private Mode mode = Mode.COALESCE;

    /** @return the configured recompute concurrency mode */
    public Mode getMode() {
        return mode;
    }

    /** @param mode the recompute concurrency mode to apply */
    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
