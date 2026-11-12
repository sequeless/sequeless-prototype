package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.api.ImportMode;
import org.sequeless.ontology.api.ImportReport;
import org.sequeless.ontology.api.OntologyAdministration;
import org.sequeless.ontology.api.OntologyDocument;
import org.sequeless.ontology.api.OntologyException;
import org.sequeless.ontology.api.OntologyFormat;
import org.sequeless.ontology.api.OntologyIssue;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.api.Severity;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.testkit.query.QueryFixtures;

/**
 * Unit-tests {@link IndexPreparingOntologyAdministration} against hand-built doubles, with no Spring
 * context and no mocking library, mirroring {@code OutboxRelayTest}'s convention. The delegate
 * "activates" a new Schema when it succeeds, so each test can check that index preparation reads the
 * Schema that is current after the delegate returned, not the one that was current before the call.
 */
class IndexPreparingOntologyAdministrationTest {

    private static final Scope SCOPE = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

    private static final Schema ACTIVATED_SCHEMA = new Schema(
            new OntologyVersion(2), "https://sequeless.dev/ns/activated", Optional.empty(), Map.of(), List.of());

    @Test
    void importPreparesIndexesOnceForTheVersionOfTheSchemaActivatedByTheImport() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        IndexPreparingOntologyAdministration admin = newAdministration(delegate, port);
        OntologyDocument document = new OntologyDocument("ignored", OntologyFormat.TURTLE);

        ImportReport report = admin.importDocument(SCOPE, document, ImportMode.REPLACE);

        assertThat(report).isSameAs(delegate.importReport);
        assertThat(delegate.importCalls).isEqualTo(1);
        assertThat(port.calls)
                .containsExactly(new PrepareCall(SCOPE, ACTIVATED_SCHEMA.ontologyVersion(), ACTIVATED_SCHEMA));
    }

    @Test
    void reloadPreparesIndexesOnceForTheVersionOfTheSchemaActivatedByTheReload() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        IndexPreparingOntologyAdministration admin = newAdministration(delegate, port);

        OntologyReport report = admin.reload(SCOPE);

        assertThat(report).isSameAs(delegate.reloadReport);
        assertThat(delegate.reloadCalls).isEqualTo(1);
        assertThat(port.calls)
                .containsExactly(new PrepareCall(SCOPE, ACTIVATED_SCHEMA.ontologyVersion(), ACTIVATED_SCHEMA));
    }

    @Test
    void exportReturnsTheDelegateDocumentAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        IndexPreparingOntologyAdministration admin = newAdministration(delegate, port);

        OntologyDocument exported = admin.export(SCOPE, OntologyFormat.TURTLE);

        assertThat(exported).isSameAs(delegate.exportDocument);
        assertThat(delegate.exportCalls).isEqualTo(1);
        assertThat(port.calls).isEmpty();
    }

    @Test
    void failedImportPropagatesAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        delegate.failImportsWith = new OntologyException(inconsistentReport());
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        IndexPreparingOntologyAdministration admin = newAdministration(delegate, port);

        assertThatThrownBy(() -> admin.importDocument(
                        SCOPE, new OntologyDocument("ignored", OntologyFormat.TURTLE), ImportMode.REPLACE))
                .isSameAs(delegate.failImportsWith);
        assertThat(port.calls).isEmpty();
    }

    @Test
    void failedReloadPropagatesAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        delegate.failReloadsWith = new OntologyException(inconsistentReport());
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        IndexPreparingOntologyAdministration admin = newAdministration(delegate, port);

        assertThatThrownBy(() -> admin.reload(SCOPE)).isSameAs(delegate.failReloadsWith);
        assertThat(port.calls).isEmpty();
    }

    private static IndexPreparingOntologyAdministration newAdministration(
            ActivatingAdministration delegate, RecordingIndexPreparationPort port) {
        SchemaSource schemaSource = delegate.schemaSource();
        return new IndexPreparingOntologyAdministration(
                delegate, schemaSource, new IndexPreparation(schemaSource, port));
    }

    private static OntologyReport inconsistentReport() {
        OntologyIssue error = new OntologyIssue(Severity.ERROR, Optional.empty(), "inconsistent by fixture");
        return new OntologyReport(false, List.of(error));
    }

    /** One {@link IndexPreparationPort#prepare} invocation: the scope, version and Schema it was given. */
    private record PrepareCall(Scope scope, OntologyVersion version, Schema schema) {}

    /**
     * A delegate whose successful import and reload first make {@link #ACTIVATED_SCHEMA} the Schema
     * its {@link SchemaSource} returns, then return a canned result. Each method counts its calls,
     * and a failure can be configured per method.
     */
    private static final class ActivatingAdministration implements OntologyAdministration {

        private final AtomicReference<Schema> active = new AtomicReference<>(QueryFixtures.schema());
        private final OntologyReport reloadReport = new OntologyReport(true, List.of());
        private final ImportReport importReport = new ImportReport(true, new OntologyReport(true, List.of()), 0);
        private final OntologyDocument exportDocument = new OntologyDocument("exported", OntologyFormat.TURTLE);

        private int reloadCalls;
        private int importCalls;
        private int exportCalls;
        private OntologyException failReloadsWith;
        private OntologyException failImportsWith;

        SchemaSource schemaSource() {
            return tenant -> active.get();
        }

        @Override
        public OntologyReport reload(Scope scope) {
            reloadCalls++;
            if (failReloadsWith != null) {
                throw failReloadsWith;
            }
            active.set(ACTIVATED_SCHEMA);
            return reloadReport;
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            exportCalls++;
            return exportDocument;
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            importCalls++;
            if (failImportsWith != null) {
                throw failImportsWith;
            }
            active.set(ACTIVATED_SCHEMA);
            return importReport;
        }
    }

    /** An {@link IndexPreparationPort} that records each {@code prepare} call and nothing else. */
    private static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        private final List<PrepareCall> calls = new ArrayList<>();

        @Override
        public void prepare(Scope scope, OntologyVersion version, Schema schema) {
            calls.add(new PrepareCall(scope, version, schema));
        }
    }
}
