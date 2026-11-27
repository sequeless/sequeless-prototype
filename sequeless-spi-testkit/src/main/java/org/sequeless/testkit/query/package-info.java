/**
 * The mechanical contract tests for the query ports in {@link org.sequeless.spi.query}.
 *
 * <p>The browse and read contracts left this package with the U6 split: they now live in the
 * released {@code query-testkit} artifact. {@code ReadContract} asserted every clause of
 * {@code ReadPort}'s javadoc: null handling, the round trip of a committed object, the empty
 * result for an unknown, soft-deleted or other-tenant id, and the latest version after an update.
 * {@code BrowseContract} asserted every clause of {@code BrowsePort}'s javadoc (null handling,
 * tenant scoping, soft-delete filtering, the union semantics of {@code Query.types()}, the
 * {@code (createdAt, id)} ascending default sort order, page overrun, and the "conventional
 * multi-select" facet-counting rule) against whichever {@code BrowseContract.Environment} a
 * subclass supplied via {@code freshEnvironment()}. {@link
 * org.sequeless.testkit.query.AggregateContract} does the same for {@code QueryPort#aggregate}.
 * Unlike {@link org.sequeless.testkit.object.ObjectStoreContract},
 * these contracts are not ontology-agnostic: exercising filters, sorts, and facets on real
 * properties needs a real {@link org.sequeless.ontology.schema.api.Schema}, so {@link
 * org.sequeless.testkit.Fixtures#schema()} hand-builds one mirroring {@code reference.ttl}
 * directly from SPI records, without any RDF/OWL library.
 */
package org.sequeless.testkit.query;
