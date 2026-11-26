package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link InProcessAutomationAutoConfiguration} via a real Spring context ({@link
 * ApplicationContextRunner}), mirroring {@code expression-spring-boot-autoconfig}'s {@code
 * JexlExpressionAutoConfigurationTest}. Registers a {@link RecordingActionExecutor} as the
 * context's {@link ActionExecutor} bean in place of the real {@code DefaultActionExecutor} this
 * adapter module cannot depend on — standing in for the later, application-level task that wires
 * the real bean in.
 */
class InProcessAutomationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InProcessAutomationAutoConfiguration.class))
            .withBean(ActionExecutor.class, RecordingActionExecutor::new)
            .withBean(TriggerEvaluator.class, RecordingTriggerEvaluator::new)
            .withBean(AutomationSource.class, () -> tenant -> new Automation(new OntologyVersion(1), Map.of()));

    @Test
    void wiresAutomationPortWhenAdapterPropertySetAndActionExecutorPresent() {
        runner.withPropertyValues("sequeless.automation.adapter=inprocess").run(context -> {
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
        runner.withPropertyValues("sequeless.automation.adapter=other").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
        });
    }

    @Test
    void aSweepFailureOutsideATimerDoesNotEscapeTheTick() {
        Clock failingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                throw new IllegalStateException("clock failed");
            }
        };
        InProcessTimerScheduler scheduler = new InProcessTimerScheduler(failingClock, new RecordingTriggerEvaluator());

        assertThatCode(() -> InProcessAutomationAutoConfiguration.sweepOnce(scheduler))
                .doesNotThrowAnyException();
    }
}
