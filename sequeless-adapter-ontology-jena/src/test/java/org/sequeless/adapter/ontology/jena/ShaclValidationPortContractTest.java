package org.sequeless.adapter.ontology.jena;

import java.util.Set;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.jena.JenaOntologyPort;
import org.sequeless.ontology.jena.ReasonerSetting;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.api.OntologyDocument;
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
        JenaOntologyPort ontologyPort =
                JenaOntologyPort.fromDocument(document, ReasonerSetting.OWL, () -> Set.of("workload"));
        Schema schema = ontologyPort.schema(TenantId.DEFAULT);
        return new Fixture(new ShaclValidationPort(ontologyPort), schema);
    }
}
