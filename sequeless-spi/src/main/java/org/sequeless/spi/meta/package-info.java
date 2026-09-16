/**
 * The ontology snapshot vocabulary: immutable value types describing the application's meta-model
 * as read from OWL, independent of any RDF library.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) except sibling types from {@link org.sequeless.spi.ontology}. {@link
 * org.sequeless.spi.meta.MetaModelSnapshot} is the aggregate root: an ontology IRI, its declared
 * prefixes, an {@link org.sequeless.spi.ontology.OntologyReport}, and every {@link
 * org.sequeless.spi.meta.TypeDefinition} it contains. A type carries {@link
 * org.sequeless.spi.meta.PropertyDefinition}s, sealed to {@link
 * org.sequeless.spi.meta.AttributeDefinition} (scalar-valued) and {@link
 * org.sequeless.spi.meta.RelationshipDefinition} (object-valued). {@link
 * org.sequeless.spi.meta.Cardinality} and {@link org.sequeless.spi.meta.DisplayHints} are shared
 * value types; {@link org.sequeless.spi.meta.DerivationRule} and {@link
 * org.sequeless.spi.meta.StateMachineDefinition} are deliberately empty placeholders for Phase 4
 * and Phase 5 respectively, present now so the snapshot shape does not change again when those
 * phases land. The full mapping from OWL and the {@code sq:} annotation vocabulary to these fields
 * is documented in {@code docs/architecture/sq-vocabulary.md}, which this package's field names must
 * match exactly.
 */
package org.sequeless.spi.meta;
