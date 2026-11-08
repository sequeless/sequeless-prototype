package org.sequeless.adapter.ontology.jena;

import java.util.Set;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.testkit.ontology.OntologyContract;
import org.sequeless.testkit.ontology.OntologyPorts;

/**
 * Proves {@link JenaOntologyPort} satisfies the shared {@link OntologyContract} behavioural contract
 * (null-handling, determinism, build-fresh-validate-swap-only-on-success, export/import round-trip,
 * inconsistency always thrown) — the mechanical requirements every ontology adapter must meet,
 * independently of this adapter's own reasoner-specific behaviour, which is covered by this module's
 * own test classes instead (see {@code JenaOntologyPortAcceptanceTest} and friends).
 *
 * <p>Uses {@link ReasonerSetting#OWL}, the only one of the three settings that detects the
 * contract's inconsistency fixture (see {@code ConsistencyCheckerTest}), so {@code
 * inconsistentOntologyMakesSchemaThrow} (and its Behaviour and Automation siblings) actually
 * exercises a real rejection rather than passing vacuously.
 */
class JenaOntologyPortContractTest extends OntologyContract {

    @Override
    protected OntologyPorts portFor(OntologyDocument document) {
        return OntologyPorts.of(JenaOntologyPort.fromDocument(document, ReasonerSetting.OWL, () -> Set.of("workload")));
    }
}
