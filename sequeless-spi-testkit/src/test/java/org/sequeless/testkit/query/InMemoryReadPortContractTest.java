package org.sequeless.testkit.query;

/**
 * This module's own proof that {@link ReadContract} is implementable without SQL: it runs the
 * contract against {@link InMemoryReadPort}.
 */
class InMemoryReadPortContractTest extends ReadContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryAggregateObjectStorePort store = new InMemoryAggregateObjectStorePort();
        return new Environment(store, new InMemoryReadPort(store));
    }
}
