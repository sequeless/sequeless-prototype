/**
 * The Schema half of an ontology activation: immutable value types describing the application's
 * type system as read from OWL, independent of any RDF library.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) and the kernel ({@code org.sequeless.kernel.*}). {@link
 * org.sequeless.spi.meta.schema.Schema} is the aggregate root: the ontology IRI, its declared
 * prefixes, the {@link org.sequeless.spi.meta.schema.OntologyVersion} of the activation it was read
 * from, and every {@link org.sequeless.spi.meta.schema.TypeDefinition} it contains. A type carries
 * {@link org.sequeless.spi.meta.schema.PropertyDefinition}s, sealed to {@link
 * org.sequeless.spi.meta.schema.AttributeDefinition} (scalar-valued) and {@link
 * org.sequeless.spi.meta.schema.RelationshipDefinition} (object-valued). {@link
 * org.sequeless.spi.meta.schema.Cardinality} and {@link org.sequeless.spi.meta.schema.DisplayHints}
 * are shared value types. {@link org.sequeless.spi.meta.schema.AggregateFunction} is the shared
 * function vocabulary of {@link org.sequeless.spi.query.AggregateRequest}. {@link
 * org.sequeless.spi.meta.schema.SchemaSource} is the read port for this half.
 *
 * <p>The state machines and derivation rules that govern these types are in {@code
 * org.sequeless.spi.meta.behaviour}, and the actions that run when a transition fires are in {@code
 * org.sequeless.spi.meta.automation}. The full mapping from OWL and the {@code sq:} annotation
 * vocabulary to these fields is documented in {@code docs/architecture/sq-vocabulary.md}, which this
 * package's field names must match exactly.
 */
package org.sequeless.spi.meta.schema;
