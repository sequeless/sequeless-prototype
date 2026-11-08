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
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyAdministration;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
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
    void importPreparesIndexesOnceWithTheSchemaActivatedByTheImport() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingQueryPort queryPort = new RecordingQueryPort();
        IndexPreparingOntologyAdministration port = newPort(delegate, queryPort);
        OntologyDocument document = new OntologyDocument("ignored", OntologyFormat.TURTLE);

        ImportReport report = port.importDocument(SCOPE, document, ImportMode.REPLACE);

        assertThat(report).isSameAs(delegate.importReport);
        assertThat(delegate.importCalls).isEqualTo(1);
        assertThat(queryPort.calls).containsExactly(new EnsureIndexesCall(SCOPE, ACTIVATED_SCHEMA));
    }

    @Test
    void reloadPreparesIndexesOnceWithTheSchemaActivatedByTheReload() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingQueryPort queryPort = new RecordingQueryPort();
        IndexPreparingOntologyAdministration port = newPort(delegate, queryPort);

        OntologyReport report = port.reload(SCOPE);

        assertThat(report).isSameAs(delegate.reloadReport);
        assertThat(delegate.reloadCalls).isEqualTo(1);
        assertThat(queryPort.calls).containsExactly(new EnsureIndexesCall(SCOPE, ACTIVATED_SCHEMA));
    }

    @Test
    void exportReturnsTheDelegateDocumentAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        RecordingQueryPort queryPort = new RecordingQueryPort();
        IndexPreparingOntologyAdministration port = newPort(delegate, queryPort);

        OntologyDocument exported = port.export(SCOPE, OntologyFormat.TURTLE);

        assertThat(exported).isSameAs(delegate.exportDocument);
        assertThat(delegate.exportCalls).isEqualTo(1);
        assertThat(queryPort.calls).isEmpty();
    }

    @Test
    void failedImportPropagatesAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        delegate.failImportsWith = new OntologyException(inconsistentReport());
        RecordingQueryPort queryPort = new RecordingQueryPort();
        IndexPreparingOntologyAdministration port = newPort(delegate, queryPort);

        assertThatThrownBy(() -> port.importDocument(
                        SCOPE, new OntologyDocument("ignored", OntologyFormat.TURTLE), ImportMode.REPLACE))
                .isSameAs(delegate.failImportsWith);
        assertThat(queryPort.calls).isEmpty();
    }

    @Test
    void failedReloadPropagatesAndDoesNotPrepareIndexes() {
        ActivatingAdministration delegate = new ActivatingAdministration();
        delegate.failReloadsWith = new OntologyException(inconsistentReport());
        RecordingQueryPort queryPort = new RecordingQueryPort();
        IndexPreparingOntologyAdministration port = newPort(delegate, queryPort);

        assertThatThrownBy(() -> port.reload(SCOPE)).isSameAs(delegate.failReloadsWith);
        assertThat(queryPort.calls).isEmpty();
    }

    private static IndexPreparingOntologyAdministration newPort(
            ActivatingAdministration delegate, RecordingQueryPort queryPort) {
        return new IndexPreparingOntologyAdministration(delegate, delegate.schemaSource(), queryPort);
    }

    private static OntologyReport inconsistentReport() {
        OntologyIssue error = new OntologyIssue(Severity.ERROR, Optional.empty(), "inconsistent by fixture");
        return new OntologyReport(false, List.of(error));
    }

    /** One {@link QueryPort#ensureIndexes} invocation: the scope and the Schema it was given. */
    private record EnsureIndexesCall(Scope scope, Schema schema) {}

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

    /** A {@link QueryPort} that records each {@code ensureIndexes} call and nothing else. */
    private static final class RecordingQueryPort implements QueryPort {

        private final List<EnsureIndexesCall> calls = new ArrayList<>();

        @Override
        public void ensureIndexes(Scope scope, Schema schema) {
            calls.add(new EnsureIndexesCall(scope, schema));
        }

        @Override
        public QueryResult query(Scope scope, Schema schema, Query query) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public AggregateResult aggregate(Scope scope, Schema schema, AggregateRequest request) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
