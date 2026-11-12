package org.sequeless.app.config;

import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.api.ImportMode;
import org.sequeless.ontology.api.ImportReport;
import org.sequeless.ontology.api.OntologyAdministration;
import org.sequeless.ontology.api.OntologyDocument;
import org.sequeless.ontology.api.OntologyFormat;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;

/**
 * Prepares query indexes after every ontology activation the host performs through the admin port,
 * so that index preparation stays out of the use case (#24).
 *
 * <p>After {@link #importDocument} or {@link #reload} returns normally, this reads the Schema the
 * delegate has just activated and calls {@link IndexPreparation#prepare} with its tenant and its
 * version, then returns the delegate's result. If the delegate throws, nothing is prepared. {@link
 * #export} only delegates.
 *
 * <p>This class is deliberately not a Spring bean: a second {@link OntologyAdministration} bean
 * would break {@link org.sequeless.app.port.PortRegistry}'s one-bean check. {@link
 * CoreConfiguration} builds it around the ontology adapter bean and hands it to the use cases.
 */
public final class IndexPreparingOntologyAdministration implements OntologyAdministration {

    private final OntologyAdministration delegate;
    private final SchemaSource schemaSource;
    private final IndexPreparation indexPreparation;

    /**
     * @param delegate the admin port every call goes through; must not be {@code null}
     * @param schemaSource the port the Schema of the new activation is read from; must not be {@code
     *     null}
     * @param indexPreparation the host use case that prepares indexes for the new activation; must
     *     not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public IndexPreparingOntologyAdministration(
            OntologyAdministration delegate, SchemaSource schemaSource, IndexPreparation indexPreparation) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.indexPreparation = Objects.requireNonNull(indexPreparation, "indexPreparation must not be null");
    }

    /**
     * @param scope the tenant and principal the request is made on behalf of
     * @return the delegate's report, after indexes for the reloaded Schema are prepared
     */
    @Override
    public OntologyReport reload(Scope scope) {
        OntologyReport report = delegate.reload(scope);
        prepareIndexes(scope);
        return report;
    }

    /**
     * @param scope the tenant and principal the request is made on behalf of
     * @param format the serialisation to export into
     * @return the delegate's document; no indexes are touched
     */
    @Override
    public OntologyDocument export(Scope scope, OntologyFormat format) {
        return delegate.export(scope, format);
    }

    /**
     * @param scope the tenant and principal the request is made on behalf of
     * @param document the ontology document to import
     * @param mode how {@code document} is applied
     * @return the delegate's report, after indexes for the imported Schema are prepared
     */
    @Override
    public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
        ImportReport report = delegate.importDocument(scope, document, mode);
        prepareIndexes(scope);
        return report;
    }

    private void prepareIndexes(Scope scope) {
        Schema schema = schemaSource.schema(scope.tenantId());
        indexPreparation.prepare(scope.tenantId(), schema.ontologyVersion());
    }
}
