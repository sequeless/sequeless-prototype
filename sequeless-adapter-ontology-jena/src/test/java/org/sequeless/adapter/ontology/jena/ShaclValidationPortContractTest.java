package org.sequeless.adapter.ontology.jena;

import java.util.Set;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.testkit.validation.ValidationContract;

/**
 * Proves {@link ShaclValidationPort} satisfies every clause of {@link
 * org.sequeless.spi.validation.ValidationPort}'s behavioural contract, wired against a real {@link
 * JenaOntologyPort} built straight from an {@link OntologyDocument} (no store, no Spring context —
 * see {@link ShaclValidationAutoConfigurationTest} for the Spring-wired proof).
 */
class ShaclValidationPortContractTest extends ValidationContract {

    @Override
    protected Fixture fixtureFor(OntologyDocument document) {
        JenaOntologyPort ontologyPort = JenaOntologyPort.fromDocument(
                document, ReasonerSetting.OWL, () -> Set.of("workload"));
        Schema schema = ontologyPort.schema(TenantId.DEFAULT);
        return new Fixture(new ShaclValidationPort(ontologyPort), schema);
    }
}
