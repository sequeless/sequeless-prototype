package org.sequeless.testkit.query;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.spi.query.ReadRequest;

/**
 * An in-memory {@link ReadPort}, the double {@link ReadContract} is proven against. It answers a
 * read by scanning the store's listing of the tenant's objects rather than calling the store's
 * {@code find}, so the contract is checked against an implementation of the read semantics that is
 * independent of the one the seeding store uses.
 */
final class InMemoryReadPort implements ReadPort {

    private final InMemoryAggregateObjectStorePort store;

    InMemoryReadPort(InMemoryAggregateObjectStorePort store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public Optional<ObjectView> read(Scope scope, ReadRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return store.everyObject(scope).stream()
                .filter(object -> object.id().equals(request.id()) && !object.deleted())
                .findFirst()
                .map(ObjectViews::of);
    }
}
