package org.sequeless.testkit.query;

/**
 * This module's own proof that {@link BrowseContract} is implementable without SQL: it runs the
 * contract against {@link InMemoryBrowsePort}, a real in-memory query engine.
 */
class InMemoryBrowsePortContractTest extends BrowseContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryAggregateObjectStorePort store = new InMemoryAggregateObjectStorePort();
        return new Environment(store, new InMemoryBrowsePort(store));
    }
}
