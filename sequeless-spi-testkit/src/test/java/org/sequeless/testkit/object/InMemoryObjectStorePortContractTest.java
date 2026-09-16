package org.sequeless.testkit.object;

import java.util.List;
import java.util.UUID;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.ObjectStorePort;

/**
 * This module's own proof that {@link ObjectStoreContract} is implementable without SQL, JSONB, or
 * Flyway: see {@link InMemoryObjectStorePort}'s javadoc for why this matters.
 */
class InMemoryObjectStorePortContractTest extends ObjectStoreContract {

    private InMemoryObjectStorePort store;

    @Override
    protected ObjectStorePort freshStore() {
        store = new InMemoryObjectStorePort();
        return store;
    }

    @Override
    protected List<UUID> persistedOutboxIds(Scope scope) {
        return store.outboxIds();
    }
}
