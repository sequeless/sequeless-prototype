package org.sequeless.adapter.automation.inprocess;

import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
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
@EnableConfigurationProperties(InProcessAutomationProperties.class)
public class InProcessAutomationAutoConfiguration {

    /**
     * @param actionExecutor the {@link ActionExecutor} bean some other, application-level
     *     configuration is expected to have already registered on the context
     * @param properties the bound {@code sequeless.automation.inprocess.*} webhook retry
     *     configuration
     * @return a new {@link InProcessAutomationPort} over both
     */
    @Bean
    public AutomationPort automationPort(ActionExecutor actionExecutor, InProcessAutomationProperties properties) {
        return new InProcessAutomationPort(actionExecutor, properties);
    }
}
