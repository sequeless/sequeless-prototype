package org.sequeless.adapter.expression.jexl;

import org.sequeless.spi.expression.ExpressionPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link JexlExpressionPort} into the Spring context when {@code
 * sequeless.expression.adapter} is set to {@code jexl}, mirroring {@code
 * sequeless-adapter-ontology-jena}'s {@code JenaOntologyAutoConfiguration}/{@code
 * ShaclValidationAutoConfiguration} pattern exactly (no {@code matchIfMissing}, no {@code
 * @ConditionalOnMissingBean} — the application's port registry, not Spring, is what detects an
 * absent or ambiguous port configuration).
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.expression.adapter", havingValue = "jexl")
public class JexlExpressionAutoConfiguration {

    @Bean
    public ExpressionPort expressionPort() {
        return new JexlExpressionPort();
    }
}
