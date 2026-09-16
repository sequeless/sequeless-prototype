package org.sequeless.testkit.ontology;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * This testkit's own proof that {@link OntologyContract} is not secretly biased toward
 * implementations that do real OWL reasoning. The port under test here does no OWL parsing at
 * all: it holds the current {@link OntologyDocument} verbatim, derives a fixed-shape single-type
 * {@link MetaModelSnapshot} from it regardless of content, and decides consistency purely by
 * comparing {@link OntologyDocument#content()} against {@link Fixtures#inconsistentOntology()}'s
 * content — genuine branching, trivially implemented, exactly like {@code DenyAllAuthorizationPort}
 * plays for {@code AuthorizationContract}. If {@link OntologyContract} could only be passed by a
 * port backed by a genuine reasoner, it would not be a meaningful shared contract, and this test
 * would fail.
 */
class InMemoryOntologyPortContractTest extends OntologyContract {

    @Override
    protected OntologyPort portFor(OntologyDocument document) {
        return new FakeOntologyPort(document);
    }

    /**
     * Holds the current backing document and rebuilds a fixed-shape snapshot from it on every
     * call — no caching of "is this consistent" beyond a plain string comparison, no parsing, no
     * reasoning of any kind.
     */
    private static final class FakeOntologyPort implements OntologyPort {

        private OntologyDocument source;

        FakeOntologyPort(OntologyDocument document) {
            this.source = Objects.requireNonNull(document, "document must not be null");
        }

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return buildSnapshot(source);
        }

        @Override
        public MetaModelSnapshot reload(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return buildSnapshot(source);
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(format, "format must not be null");
            buildSnapshot(source);
            return new OntologyDocument(source.content(), format);
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(document, "document must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            MetaModelSnapshot rebuilt = buildSnapshot(document);
            this.source = document;
            return new ImportReport(true, rebuilt.report(), rebuilt.types().size());
        }

        /**
         * The one piece of genuine branching in this fake: a document whose content matches {@link
         * Fixtures#inconsistentOntology()} is rejected, everything else is accepted and mapped to
         * the same fixed single-type shape regardless of what it actually contains.
         */
        private MetaModelSnapshot buildSnapshot(OntologyDocument document) {
            if (document.content().equals(Fixtures.inconsistentOntology().content())) {
                OntologyReport report =
                    new OntologyReport(
                        false,
                        List.of(
                            new OntologyIssue(
                                Severity.ERROR,
                                Optional.empty(),
                                "fake port: document matches the known-inconsistent fixture")));
                throw new OntologyException(report);
            }

            TypeDefinition thing =
                new TypeDefinition(
                    "https://example.org/fake#Thing",
                    "Thing",
                    List.of(),
                    List.of(),
                    DisplayHints.none(),
                    false,
                    Optional.empty());
            return new MetaModelSnapshot(
                "https://example.org/fake",
                Optional.empty(),
                Map.of(),
                List.of(thing),
                new OntologyReport(true, List.of()));
        }
    }
}
