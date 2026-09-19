package org.sequeless.adapter.expression.jexl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.expression.ExpressionPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link JexlExpressionAutoConfiguration} via a real Spring context ({@link
 * ApplicationContextRunner}), mirroring {@code sequeless-adapter-persistence-postgres}'s {@code
 * PostgresPersistenceAutoConfigurationTest}.
 */
class JexlExpressionAutoConfigurationTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JexlExpressionAutoConfiguration.class));

    @Test
    void wiresExpressionPortWhenAdapterPropertySet() {
        runner
            .withPropertyValues("sequeless.expression.adapter=jexl")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ExpressionPort.class);
                assertThat(context.getBean(ExpressionPort.class)).isInstanceOf(JexlExpressionPort.class);
            });
    }

    @Test
    void doesNotWireWhenAdapterPropertyAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ExpressionPort.class);
        });
    }

    @Test
    void doesNotWireWhenAdapterPropertyIsSomethingElse() {
        runner
            .withPropertyValues("sequeless.expression.adapter=other")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ExpressionPort.class);
            });
    }
}
