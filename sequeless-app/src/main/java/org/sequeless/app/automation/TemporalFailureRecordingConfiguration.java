package org.sequeless.app.automation;

import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.spi.automation.AutomationFailureRecorder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Records the failures of the Temporal adapter's activities, which the released adapter does not record (spec 15 has no
 * failure port; spec 17, section 10, Q2).
 *
 * <p>A {@link BeanPostProcessor} wraps the released {@link ActionExecutor} and {@link TriggerEvaluator} beans, so the
 * wrappers sit between the activities and the released implementations. The wrappers only act when a Temporal activity
 * has used its last attempt. The records they write are the ones the prototype wrote, keyed the same way.
 *
 * <p>Active only for {@code sequeless.automation.adapter=temporal}. The in-process adapter never recorded failures.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sequeless.automation.adapter", havingValue = "temporal")
public class TemporalFailureRecordingConfiguration {

    /**
     * @param recorder the persistence adapter's recorder, looked up when a failure needs it
     * @param maximumAttempts the Temporal retry limit, from {@code sequeless.automation.temporal.retry.maximum-attempts}
     * @return the post-processor that wraps the released beans
     */
    @Bean
    static BeanPostProcessor temporalFailureRecording(
            ObjectProvider<AutomationFailureRecorder> recorder,
            @Value("${sequeless.automation.temporal.retry.maximum-attempts:5}") int maximumAttempts) {
        return new FailureRecordingPostProcessor(new TemporalFailureRecorder(recorder, maximumAttempts));
    }

    /** Wraps each released {@link ActionExecutor} and {@link TriggerEvaluator} bean once, after its creation. */
    static final class FailureRecordingPostProcessor implements BeanPostProcessor {

        private final TemporalFailureRecorder failures;

        FailureRecordingPostProcessor(TemporalFailureRecorder failures) {
            this.failures = failures;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ActionExecutor executor) {
                return new FailureRecordingActionExecutor(executor, failures);
            }
            if (bean instanceof TriggerEvaluator evaluator) {
                return new FailureRecordingTriggerEvaluator(evaluator, failures);
            }
            return bean;
        }
    }
}
