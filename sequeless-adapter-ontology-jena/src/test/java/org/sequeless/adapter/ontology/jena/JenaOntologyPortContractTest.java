package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.testkit.ontology.OntologyContract;

/**
 * Proves {@link JenaOntologyPort} satisfies the shared {@link OntologyPort} behavioural contract
 * (null-handling, determinism, build-fresh-validate-swap-only-on-success, export/import round-trip,
 * inconsistency always thrown) — the mechanical requirements every {@code OntologyPort} adapter
 * must meet, independently of this adapter's own reasoner-specific behaviour, which is covered by
 * this module's own test classes instead (see {@code JenaOntologyPortAcceptanceTest} and friends).
 *
 * <p>Uses {@link ReasonerSetting#OWL}, the only one of the three settings that detects the
 * contract's inconsistency fixture (see {@code ConsistencyCheckerTest}), so {@code
 * inconsistentOntologyMakesSnapshotThrow} actually exercises a real rejection rather than passing
 * vacuously.
 */
class JenaOntologyPortContractTest extends OntologyContract {

    @Override
    protected OntologyPort portFor(OntologyDocument document) {
        return JenaOntologyPort.fromDocument(document, ReasonerSetting.OWL);
    }
}
