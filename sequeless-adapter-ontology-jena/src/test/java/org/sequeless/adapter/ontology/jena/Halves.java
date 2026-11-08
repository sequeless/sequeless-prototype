package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;

/**
 * The three halves one port serves for {@link TenantId#DEFAULT}, read together so that a test can
 * compare one activation with another, or check that the halves of one activation share a version.
 *
 * @param schema the Schema half
 * @param behaviour the Behaviour half
 * @param automation the Automation half
 */
record Halves(Schema schema, Behaviour behaviour, Automation automation) {

    private static final OntologyVersion ANY = new OntologyVersion(1);

    static Halves of(JenaOntologyPort port) {
        return new Halves(
                port.schema(TenantId.DEFAULT), port.behaviour(TenantId.DEFAULT), port.automation(TenantId.DEFAULT));
    }

    /**
     * @return the version of the Schema half; every test that calls this first asserts that the three
     *     halves agree
     */
    OntologyVersion version() {
        assertSharesOneVersion();
        return schema.ontologyVersion();
    }

    /**
     * @return these halves with every version replaced by one fixed value, so that two activations
     *     can be compared on their content alone
     */
    Halves withoutVersion() {
        return new Halves(
                new Schema(ANY, schema.ontologyIri(), schema.versionIri(), schema.prefixes(), schema.types()),
                new Behaviour(ANY, behaviour.stateMachines(), behaviour.derivations()),
                new Automation(ANY, automation.transitionActions()));
    }

    void assertSharesOneVersion() {
        assertThat(behaviour.ontologyVersion()).isEqualTo(schema.ontologyVersion());
        assertThat(automation.ontologyVersion()).isEqualTo(schema.ontologyVersion());
    }
}
