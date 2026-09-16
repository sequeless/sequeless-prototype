package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.ontology.OntologyPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link JenaOntologyPort} into the Spring context when {@code sequeless.ontology.adapter} is
 * set to {@code jena}, mirroring {@code sequeless-adapter-authz-permitall}'s {@code
 * PermitAllAuthorizationAutoConfiguration} exactly.
 *
 * <p>Deliberately has no {@code matchIfMissing}: when the property is unset, the condition must
 * evaluate to {@code false} and no {@link OntologyPort} bean must exist here at all, so the
 * application's port registry can detect the absent-property case itself and report it, rather than
 * this auto-configuration silently conjuring a default.
 *
 * <p>Deliberately has no {@code @ConditionalOnMissingBean}: if another adapter's auto-configuration
 * also produces an {@link OntologyPort} bean under the same property value, both beans must be
 * allowed to exist so the application's port registry — not Spring — is the thing that detects and
 * rejects the ambiguity.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.ontology.adapter", havingValue = "jena")
@EnableConfigurationProperties(JenaOntologyProperties.class)
public class JenaOntologyAutoConfiguration {

    /**
     * @param properties the bound {@code sequeless.ontology.*} configuration
     * @return a new {@link JenaOntologyPort} loaded from {@link JenaOntologyProperties#getSource()}
     *     under {@link JenaOntologyProperties#getReasoner()}
     */
    @Bean
    public OntologyPort ontologyPort(JenaOntologyProperties properties) {
        return JenaOntologyPort.fromSource(
            "sequeless.ontology.source", properties.getSource(), properties.getReasoner());
    }
}
