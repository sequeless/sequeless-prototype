package org.sequeless.app.config;

import java.util.Objects;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.SearchVectorBackfillPort;
import org.sequeless.spi.query.IndexPreparationPort;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The host use case that prepares query indexes for a tenant's active ontology through {@link
 * IndexPreparationPort}. The three ontology lifecycle call sites in this package (startup, reload
 * and import) reach index preparation only through this class, so none of them calls the port
 * directly (#24).
 *
 * <p>{@link #prepare} registers the tenant's searchable properties and then, when that registered a
 * new one, recomputes the search data of the tenant's existing objects through {@link
 * SearchVectorBackfillPort}. Both steps run in one transaction from {@code transactions}, so the
 * registry rows and the backfill commit or roll back together.
 *
 * <p>{@link #prepare} passes the version it is given through unchanged, without checking it against
 * the Schema it reads. Each call site reads the Schema it has just activated and passes that
 * Schema's version, so the two agree in practice, but the port must not depend on the match.
 */
public final class IndexPreparation {

    private final SchemaSource schemaSource;
    private final IndexPreparationPort indexPreparationPort;
    private final SearchVectorBackfillPort searchVectorBackfillPort;
    private final TransactionOperations transactions;

    /**
     * @param schemaSource the port the Schema of a tenant is read from; must not be {@code null}
     * @param indexPreparationPort the port indexes are prepared through; must not be {@code null}
     * @param searchVectorBackfillPort the port the existing objects' search data is recomputed
     *     through when a new searchable property was registered; must not be {@code null}
     * @param transactions the transaction the preparation and the backfill run in; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public IndexPreparation(
            SchemaSource schemaSource,
            IndexPreparationPort indexPreparationPort,
            SearchVectorBackfillPort searchVectorBackfillPort,
            TransactionOperations transactions) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.indexPreparationPort =
                Objects.requireNonNull(indexPreparationPort, "indexPreparationPort must not be null");
        this.searchVectorBackfillPort =
                Objects.requireNonNull(searchVectorBackfillPort, "searchVectorBackfillPort must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    /**
     * Prepares indexes for the Schema that {@code tenant} currently has active, and backfills the
     * tenant's existing objects when a new searchable property was registered.
     *
     * @param tenant the tenant whose ontology was activated; must not be {@code null}
     * @param version the ontology version the caller activated; must not be {@code null}; passed
     *     through unchanged, not checked against the Schema read here
     * @throws NullPointerException if either argument is {@code null}
     */
    public void prepare(TenantId tenant, OntologyVersion version) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Scope scope = new Scope(tenant, Principal.ANONYMOUS);
        Schema schema = schemaSource.schema(tenant);
        transactions.execute(status -> {
            boolean newlyRegistered = indexPreparationPort.prepare(scope, version, schema);
            if (newlyRegistered) {
                searchVectorBackfillPort.backfill(scope);
            }
            return null;
        });
    }
}
