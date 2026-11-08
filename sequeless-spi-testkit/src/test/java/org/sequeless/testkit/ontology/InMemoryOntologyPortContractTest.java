package org.sequeless.testkit.ontology;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.schema.DisplayHints;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyAdministration;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * This testkit's own proof that {@link OntologyContract} is not secretly biased toward
 * implementations that do real OWL reasoning. The port under test here does no OWL parsing at
 * all: it holds the current {@link OntologyDocument} verbatim, derives a fixed single-type
 * {@link Schema} and empty Behaviour and Automation halves from it regardless of content, and
 * decides consistency purely by comparing {@link OntologyDocument#content()} against {@link
 * Fixtures#inconsistentOntology()}'s content — genuine branching, trivially implemented, exactly
 * like {@code DenyAllAuthorizationPort} plays for {@code AuthorizationContract}. If {@link
 * OntologyContract} could only be passed by a port backed by a genuine reasoner, it would not be a
 * meaningful shared contract, and this test would fail.
 */
class InMemoryOntologyPortContractTest extends OntologyContract {

    @Override
    protected OntologyPorts portFor(OntologyDocument document) {
        return OntologyPorts.of(new FakeOntologyPort(document));
    }

    /**
     * Holds the current backing document and a version counter, and rebuilds the three halves from
     * the document on every read — no caching, no parsing, no reasoning of any kind. The counter
     * starts at 1 and goes up by one on each successful reload or import. A failed reload or import
     * leaves both the document and the counter as they were.
     */
    private static final class FakeOntologyPort
            implements SchemaSource, BehaviourSource, AutomationSource, OntologyAdministration {

        private static final String FAKE_ONTOLOGY_IRI = "https://example.org/fake";
        private static final List<TypeDefinition> TYPES = List.of(new TypeDefinition(
                "https://example.org/fake#Thing", "Thing", List.of(), List.of(), DisplayHints.none(), false));

        private OntologyDocument source;
        private int version = 1;

        FakeOntologyPort(OntologyDocument document) {
            this.source = Objects.requireNonNull(document, "document must not be null");
        }

        @Override
        public Schema schema(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            requireConsistent(source);
            return new Schema(new OntologyVersion(version), FAKE_ONTOLOGY_IRI, Optional.empty(), Map.of(), TYPES);
        }

        @Override
        public Behaviour behaviour(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            requireConsistent(source);
            return new Behaviour(new OntologyVersion(version), Map.of(), Map.of());
        }

        @Override
        public Automation automation(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            requireConsistent(source);
            return new Automation(new OntologyVersion(version), Map.of());
        }

        @Override
        public OntologyReport reload(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            requireConsistent(source);
            version++;
            return new OntologyReport(true, List.of());
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(format, "format must not be null");
            requireConsistent(source);
            return new OntologyDocument(source.content(), format);
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(document, "document must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            requireConsistent(document);
            this.source = document;
            version++;
            return new ImportReport(true, new OntologyReport(true, List.of()), TYPES.size());
        }

        /**
         * The one piece of genuine branching in this fake: a document whose content matches {@link
         * Fixtures#inconsistentOntology()} is rejected, everything else is accepted and mapped to the
         * same fixed shape regardless of what it actually contains.
         */
        private static void requireConsistent(OntologyDocument document) {
            if (document.content().equals(Fixtures.inconsistentOntology().content())) {
                OntologyReport report = new OntologyReport(
                        false,
                        List.of(new OntologyIssue(
                                Severity.ERROR,
                                Optional.empty(),
                                "fake port: document matches the known-inconsistent fixture")));
                throw new OntologyException(report);
            }
        }
    }
}
