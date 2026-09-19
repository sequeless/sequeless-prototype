package org.sequeless.testkit.query;

/**
 * Proves {@link AggregateContract} itself is correct — abstract, and otherwise unreachable in
 * this module — by running it against {@link InMemoryAggregateObjectStorePort} and {@link
 * InMemoryAggregateQueryPort}, a purpose-built pairing that reproduces {@link
 * org.sequeless.spi.query.QueryPort#aggregate}'s density, tenant-scoping, and soft-delete
 * semantics honestly rather than faking them. T7's {@code PostgresQueryStoreAggregateContractIT}
 * extends the same {@link AggregateContract} against the real Postgres adapter.
 */
class InMemoryAggregateContractTest extends AggregateContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryAggregateObjectStorePort store = new InMemoryAggregateObjectStorePort();
        return new Environment(store, new InMemoryAggregateQueryPort(store));
    }
}
