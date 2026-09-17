/**
 * The mechanical contract test for {@link org.sequeless.spi.query.QueryPort}.
 *
 * <p>{@link org.sequeless.testkit.query.QueryContract} asserts every clause of the behavioural
 * contract documented on {@code QueryPort} itself — null handling, tenant scoping, soft-delete
 * filtering, the union semantics of {@code Query.types()}, the {@code (createdAt, id)} ascending
 * default sort order, and the "conventional multi-select" facet-counting rule — against whichever
 * {@link org.sequeless.testkit.query.QueryContract.Environment} a subclass supplies via {@code
 * freshEnvironment()}. Unlike {@link org.sequeless.testkit.object.ObjectStoreContract}, this
 * contract is not ontology-agnostic: exercising filters, sorts, and facets on real properties
 * needs a real {@link org.sequeless.spi.meta.MetaModelSnapshot}, so {@link
 * org.sequeless.testkit.query.QueryFixtures} hand-builds one mirroring {@code reference.ttl}
 * directly from SPI records, without any RDF/OWL library. {@code QueryPort} itself has no
 * implementation yet as of this contract's introduction — the Postgres adapter that extends it
 * arrives in a later phase.
 */
package org.sequeless.testkit.query;
