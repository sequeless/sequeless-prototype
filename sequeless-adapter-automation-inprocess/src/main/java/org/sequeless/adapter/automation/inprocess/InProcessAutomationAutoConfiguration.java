package org.sequeless.adapter.automation.inprocess;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link InProcessAutomationPort} into the Spring context when {@code
 * sequeless.automation.adapter} is set to {@code inprocess}, mirroring {@code
 * sequeless-adapter-expression-jexl}'s {@code JexlExpressionAutoConfiguration} pattern exactly (no
 * {@code matchIfMissing}, no {@code @ConditionalOnMissingBean} — the application's port registry,
 * not Spring, is what detects an absent or ambiguous port configuration).
 *
 * <p>Deliberately does <b>not</b> construct an {@link ActionExecutor}: this adapter module cannot
 * depend on {@code sequeless-core}, which is where the real implementation ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}) lives (the {@code noAdapterDependsOnCore}
 * architecture rule). {@link #automationPort} instead simply takes an {@link ActionExecutor} as an
 * injected {@code @Bean} method parameter; some later, application-level configuration task is
 * responsible for having registered that bean on the context by the time this auto-configuration
 * runs. Do not be tempted to {@code new DefaultActionExecutor(...)} here — this module cannot even
 * see that class.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.automation.adapter", havingValue = "inprocess")
@EnableConfigurationProperties({InProcessAutomationProperties.class, InProcessRecomputeProperties.class})
public class InProcessAutomationAutoConfiguration {

    /**
     * The single-thread executor {@link #timerScheduler}'s periodic sweep runs on. Bound to the
     * bean lifecycle via {@code destroyMethod = "shutdownNow"} rather than a hand-rolled {@code
     * @PreDestroy}/{@code DisposableBean} — there is no other executor-owning bean in this reactor
     * to mirror, so this is the simplest correct choice: Spring already knows how to shut an
     * {@link java.util.concurrent.ExecutorService} down cleanly via its {@code shutdown}/{@code
     * shutdownNow} methods without any extra wrapper class.
     *
     * @return a new daemon-thread-backed single-thread {@link ScheduledExecutorService}
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService timerTickExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "inprocess-timer-tick");
                thread.setDaemon(true);
                return thread;
            });
    }

    /**
     * @param triggerEvaluator the port {@link InProcessTimerScheduler#sweep()} calls {@code
     *     onTimerElapsed} on for each due timer; some later, application-level configuration task
     *     is responsible for having registered this bean (mirroring {@link #automationPort}'s own
     *     {@link ActionExecutor} parameter)
     * @param timerTickExecutor the executor the periodic sweep is scheduled on
     * @param tickMillis the {@code sequeless.automation.inprocess.timer-tick-ms} sweep interval,
     *     bound as a raw {@code long} rather than a {@link Duration}-typed {@code @Value} — see
     *     {@code CoreConfiguration}'s own precedent (T7's finding) for why a {@code @Value}-bound
     *     temporal type is unsafe under a bare {@code ApplicationContextRunner}; defaults to 100ms,
     *     short enough that a test relying on the real production tick (rather than {@code
     *     advanceTime}'s synchronous sweep) would not need to wait long, while still cheap enough
     *     to run forever in production
     * @return a new {@link InProcessTimerScheduler} over {@link Clock#systemUTC()}, with its sweep
     *     already scheduled at a fixed delay on {@code timerTickExecutor}
     */
    @Bean
    public InProcessTimerScheduler timerScheduler(
            TriggerEvaluator triggerEvaluator,
            ScheduledExecutorService timerTickExecutor,
            @Value("${sequeless.automation.inprocess.timer-tick-ms:100}") long tickMillis) {
        InProcessTimerScheduler scheduler = new InProcessTimerScheduler(Clock.systemUTC(), triggerEvaluator);
        timerTickExecutor.scheduleWithFixedDelay(
            scheduler::sweep, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    /**
     * @param actionExecutor the {@link ActionExecutor} bean some other, application-level
     *     configuration is expected to have already registered on the context
     * @param properties the bound {@code sequeless.automation.inprocess.*} webhook retry
     *     configuration
     * @param triggerEvaluator the {@link TriggerEvaluator} bean an object-lifecycle, timer, or
     *     signal entry is routed to after any recompute
     * @param derivationRecomputer the {@link DerivationRecomputer} bean an object-lifecycle entry's
     *     recompute is routed to, under {@code recomputeProperties}'s configured mode
     * @param recomputeProperties the bound {@code sequeless.automation.recompute.*} concurrency
     *     mode configuration
     * @param timerScheduler the scheduler {@code TimerScheduled}/{@code TimerCancelled} entries are
     *     routed to
     * @return a new {@link InProcessAutomationPort} over all of the above
     */
    @Bean
    public AutomationPort automationPort(
            ActionExecutor actionExecutor,
            InProcessAutomationProperties properties,
            TriggerEvaluator triggerEvaluator,
            DerivationRecomputer derivationRecomputer,
            InProcessRecomputeProperties recomputeProperties,
            InProcessTimerScheduler timerScheduler) {
        return new InProcessAutomationPort(
            actionExecutor,
            properties,
            triggerEvaluator,
            derivationRecomputer,
            recomputeProperties.getMode(),
            timerScheduler);
    }
}
