/**
 * The query vocabulary: immutable value types for filtered, sorted, paged, faceted, and free-text
 * search over stored objects, independent of any RDF library, JSON library, or SQL dialect.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except sibling {@code org.sequeless.spi.*} types: {@link
 * org.sequeless.spi.Scope} from its parent package, {@link
 * org.sequeless.spi.meta.MetaModelSnapshot} from {@code org.sequeless.spi.meta}, and {@link
 * org.sequeless.spi.object.Value}, {@link org.sequeless.spi.object.BusinessObject}, and {@link
 * org.sequeless.spi.object.Page} from {@code org.sequeless.spi.object}. {@link
 * org.sequeless.spi.query.QueryPort} is the port interface. {@link org.sequeless.spi.query.Query},
 * {@link org.sequeless.spi.query.Criterion}, {@link org.sequeless.spi.query.Operator}, {@link
 * org.sequeless.spi.query.Sort}, {@link org.sequeless.spi.query.Direction}, {@link
 * org.sequeless.spi.query.QueryResult}, and {@link org.sequeless.spi.query.FacetBucket} are its
 * request/response vocabulary: a {@link org.sequeless.spi.query.Query} bundles the types to search,
 * a list of {@link org.sequeless.spi.query.Criterion} filter clauses, an optional free-text term, a
 * list of {@link org.sequeless.spi.query.Sort} clauses, a {@link org.sequeless.spi.object.Page}, and
 * the property IRIs to compute {@link org.sequeless.spi.query.FacetBucket} counts for; a {@link
 * org.sequeless.spi.query.QueryResult} carries back the matching page, the total match count, and
 * the computed facets.
 *
 * <p>This package deliberately reuses {@link org.sequeless.spi.object.Value} for {@link
 * org.sequeless.spi.query.Criterion} values rather than inventing a parallel value type — a
 * criterion compares a property against the same {@code TextValue}/{@code IntegerValue}/{@code
 * DecimalValue}/{@code BoolValue}/{@code DateTimeValue}/{@code DateValue}/{@code
 * ReferenceValue}/{@code ListValue} shapes a stored object's properties already use. The full
 * behavioural contract every implementation must satisfy is documented on {@link
 * org.sequeless.spi.query.QueryPort} itself, and will be asserted mechanically by a future {@code
 * sequeless-spi-testkit} {@code QueryContract}; see docs/architecture/query-port.md.
 */
package org.sequeless.spi.query;
