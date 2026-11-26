package org.sequeless.app.query;

import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.spi.query.IndexPreparationPort;

/**
 * The prototype's {@link IndexPreparationPort} over the released {@link QueryService#prepare}. The released prepare
 * reads the schema of the tenant through its own schema source, the same source the host reads, so the {@code schema}
 * argument is not passed on.
 *
 * <p>The released prepare returns the empty result, and does nothing, when the version is superseded (query ADR 0002).
 * That reads here as {@code false}, so the host does not run the search-vector backfill for it.
 */
public final class ReleasedIndexPreparationPortAdapter implements IndexPreparationPort {

    private final QueryService queryService;

    /**
     * @param queryService the released query service; must not be {@code null}
     */
    public ReleasedIndexPreparationPortAdapter(QueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * @return {@code true} when the released result lists the Fields newly registered for search ({@link
     *     org.sequeless.query.api.IndexPreparationResult}), which is the case the host's search-vector backfill runs
     *     for
     */
    @Override
    public boolean prepare(Scope scope, OntologyVersion version, Schema schema) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        IndexPreparationResult result = queryService.prepare(scope.tenantId(), new OntologyVersionRef(version.value()));
        return !result.newlyRegistered().isEmpty();
    }
}
