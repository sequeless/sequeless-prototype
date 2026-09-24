package org.sequeless.adapter.automation.inprocess;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.object.OutboxEntry;

/**
 * Applies {@link InProcessAutomationPort}'s configured {@link InProcessRecomputeProperties.Mode}
 * around every {@link DerivationRecomputer#recompute} call it makes for an object-lifecycle outbox
 * entry. See {@link InProcessRecomputeProperties.Mode}'s javadoc for what each of the three modes
 * means; this class is simply where each mode's mechanics live.
 *
 * <p>Both the per-key lock map ({@link #locks}) and the coalescing bookkeeping maps ({@link
 * #running}, {@link #pendingRerun}) grow unbounded for the process's lifetime — one entry per
 * distinct changed-object id ever seen. This mirrors {@link InProcessAutomationPort}'s own
 * dispatched-id set: an accepted limitation for an adapter that exists only for tests and local
 * development, not a production durability concern.
 */
final class RecomputeCoordinator {

    private final DerivationRecomputer derivationRecomputer;
    private final InProcessRecomputeProperties.Mode mode;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> pendingRerun = new ConcurrentHashMap<>();
    private final ExecutorService coalesceExecutor;

    /**
     * @param derivationRecomputer the port every mode eventually calls {@code recompute} on; must
     *     not be {@code null}
     * @param mode which concurrency strategy to apply; must not be {@code null}. Only {@link
     *     InProcessRecomputeProperties.Mode#COALESCE} allocates its own single-thread executor,
     *     since only it must avoid blocking the calling (relay) thread.
     */
    RecomputeCoordinator(DerivationRecomputer derivationRecomputer, InProcessRecomputeProperties.Mode mode) {
        this.derivationRecomputer =
            Objects.requireNonNull(derivationRecomputer, "derivationRecomputer must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.coalesceExecutor =
            mode == InProcessRecomputeProperties.Mode.COALESCE
                ? Executors.newSingleThreadExecutor(RecomputeCoordinator::newDaemonThread)
                : null;
    }

    /**
     * Runs {@code derivationRecomputer.recompute(scope, entry)} under this coordinator's configured
     * mode. See {@link InProcessRecomputeProperties.Mode} for what each mode guarantees about
     * ordering and blocking.
     *
     * @param scope the scope to recompute under; must not be {@code null}
     * @param entry the object-lifecycle outbox entry that may have invalidated a materialised
     *     value; must not be {@code null}
     */
    void recompute(Scope scope, OutboxEntry entry) {
        switch (mode) {
            case RETRY_ONLY -> derivationRecomputer.recompute(scope, entry);
            case SERIALIZE -> {
                ReentrantLock lock = locks.computeIfAbsent(key(entry), k -> new ReentrantLock());
                lock.lock();
                try {
                    derivationRecomputer.recompute(scope, entry);
                } finally {
                    lock.unlock();
                }
            }
            case COALESCE -> coalesce(scope, entry);
        }
    }

    /**
     * Single-flight coalescing: if no recompute for this key is currently running, claims {@code
     * running} and submits the actual work to {@link #coalesceExecutor} — never blocking the
     * calling thread. If a recompute for this key IS already running, simply marks {@code
     * pendingRerun} and returns immediately; the in-flight runner (see {@link #runCoalesced}) is
     * responsible for noticing that flag and running once more before releasing {@code running}.
     */
    private void coalesce(Scope scope, OutboxEntry entry) {
        String key = key(entry);
        AtomicBoolean runningFlag = running.computeIfAbsent(key, k -> new AtomicBoolean(false));
        AtomicBoolean pending = pendingRerun.computeIfAbsent(key, k -> new AtomicBoolean(false));
        if (runningFlag.compareAndSet(false, true)) {
            coalesceExecutor.submit(() -> runCoalesced(scope, entry, runningFlag, pending));
        } else {
            pending.set(true);
        }
    }

    /**
     * Runs {@code recompute} at least once, then, if {@code pending} was set by another arrival
     * while this call was in flight, clears it and runs once more — repeating until a full pass
     * completes with no pending arrival. The final re-check-and-reclaim
     * ({@code pending.get() && runningFlag.compareAndSet(false, true)}) closes the narrow race
     * where a new arrival sets {@code pending} in the gap between this runner's last {@code
     * recompute} call finishing and it releasing {@code runningFlag}: if that race is lost, the
     * arrival's own {@code coalesce} call simply reclaims {@code runningFlag} itself and this method
     * returns, leaving that arrival's own submitted run to happen instead.
     */
    private void runCoalesced(Scope scope, OutboxEntry entry, AtomicBoolean runningFlag, AtomicBoolean pending) {
        while (true) {
            pending.set(false);
            derivationRecomputer.recompute(scope, entry);
            runningFlag.set(false);
            if (!pending.get() || !runningFlag.compareAndSet(false, true)) {
                return;
            }
        }
    }

    private static String key(OutboxEntry entry) {
        Object objectId = entry.payload().get("objectId");
        return objectId == null ? entry.id().toString() : objectId.toString();
    }

    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "inprocess-recompute-coalesce");
        thread.setDaemon(true);
        return thread;
    }
}
