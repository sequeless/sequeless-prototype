package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.validation.ValidationPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link ShaclValidationPort} into the Spring context when {@code
 * sequeless.validation.adapter} is set to {@code shacl}, mirroring {@link
 * JenaOntologyAutoConfiguration}'s pattern exactly (no {@code matchIfMissing}, no {@code
 * @ConditionalOnMissingBean} — see that class's javadoc for why: the application's port registry,
 * not Spring, is what detects an absent or ambiguous port configuration).
 *
 * <p>Requires the Jena {@code OntologyPort} adapter to be active: this adapter's shapes are read
 * from whichever ontology {@link JenaOntologyPort} currently serves (see {@link
 * ShaclValidationPort}'s javadoc), so {@code sequeless.validation.adapter=shacl} without {@code
 * sequeless.ontology.adapter=jena} is a configuration error the {@link #validationPort(OntologyPort)}
 * bean method deliberately fails fast on, rather than silently doing nothing.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.validation.adapter", havingValue = "shacl")
public class ShaclValidationAutoConfiguration {

    /**
     * @param ontologyPort the configured {@code OntologyPort} bean, resolved by interface type so
     *     Spring wires whichever adapter is active; narrowed to the concrete {@link
     *     JenaOntologyPort} inside this method body only, never in this method's public signature —
     *     see {@link ShaclValidationPort}'s javadoc for why that narrowing is necessary
     * @return a {@link ShaclValidationPort} sharing {@code ontologyPort}'s live ontology
     * @throws IllegalStateException if {@code ontologyPort} is not a {@link JenaOntologyPort} —
     *     i.e. {@code sequeless.ontology.adapter} is not {@code jena}
     */
    @Bean
    public ValidationPort validationPort(OntologyPort ontologyPort) {
        if (!(ontologyPort instanceof JenaOntologyPort jenaPort)) {
            throw new IllegalStateException(
                "sequeless.validation.adapter=shacl requires the Jena OntologyPort adapter "
                    + "(sequeless.ontology.adapter=jena) to be active, sharing its ontology; got "
                    + ontologyPort.getClass());
        }
        return new ShaclValidationPort(jenaPort);
    }
}
