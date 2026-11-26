package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Spec 17 P3, step (iii): the host and the released autoconfiguration register exactly one {@code ActionExecutor} and
 * one {@code TriggerEvaluator}, and exactly one host binding of each port they need. The released autoconfiguration
 * backs off when a bean of its type already exists, so a second host bean would silently suppress or duplicate the
 * released one.
 *
 * <p>Run this class on its own. It starts a Postgres container.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=inprocess",
            "sequeless.expression.adapter=jexl"
        })
class AutomationBeanUniquenessIT extends PostgresTestcontainersSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    void exactlyOneActionExecutorAndOneTriggerEvaluatorAreRegistered() {
        assertThat(context.getBeansOfType(ActionExecutor.class)).hasSize(1);
        assertThat(context.getBeansOfType(TriggerEvaluator.class)).hasSize(1);
    }

    @Test
    void exactlyOneHostBindingOfEachPortTheReleasedCoreNeedsIsRegistered() {
        assertThat(context.getBeansOfType(ObjectReadPort.class)).hasSize(1);
        assertThat(context.getBeansOfType(StoreCommandPort.class)).hasSize(1);
    }
}
