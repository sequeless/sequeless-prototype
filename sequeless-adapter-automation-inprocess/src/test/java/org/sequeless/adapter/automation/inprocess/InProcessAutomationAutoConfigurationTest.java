package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link InProcessAutomationAutoConfiguration} via a real Spring context ({@link
 * ApplicationContextRunner}), mirroring {@code sequeless-adapter-expression-jexl}'s {@code
 * JexlExpressionAutoConfigurationTest}. Registers a {@link RecordingActionExecutor} as the
 * context's {@link ActionExecutor} bean in place of the real {@code DefaultActionExecutor} this
 * adapter module cannot depend on — standing in for the later, application-level task that wires
 * the real bean in.
 */
class InProcessAutomationAutoConfigurationTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InProcessAutomationAutoConfiguration.class))
            .withBean(ActionExecutor.class, RecordingActionExecutor::new);

    @Test
    void wiresAutomationPortWhenAdapterPropertySetAndActionExecutorPresent() {
        runner
            .withPropertyValues("sequeless.automation.adapter=inprocess")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AutomationPort.class);
                assertThat(context.getBean(AutomationPort.class)).isInstanceOf(InProcessAutomationPort.class);
            });
    }

    @Test
    void doesNotWireWhenAdapterPropertyAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
        });
    }

    @Test
    void doesNotWireWhenAdapterPropertyIsSomethingElse() {
        runner
            .withPropertyValues("sequeless.automation.adapter=other")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(AutomationPort.class);
            });
    }
}
