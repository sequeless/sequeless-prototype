package org.sequeless.app.query;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.query.api.QueryService;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.spi.query.ReadRequest;

/**
 * The prototype's {@link ReadPort} over the released {@link QueryService}. A soft-deleted, unknown or foreign-tenant
 * object reads as empty, as the released read defines it.
 */
public final class ReleasedReadPortAdapter implements ReadPort {

    private final QueryService queryService;

    /**
     * @param queryService the released query service; must not be {@code null}
     */
    public ReleasedReadPortAdapter(QueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @Override
    public Optional<ObjectView> read(Scope scope, ReadRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return queryService
                .read(scope, new org.sequeless.query.api.ReadRequest(request.id()))
                .map(ObjectViewMapper::toPrototype);
    }
}
