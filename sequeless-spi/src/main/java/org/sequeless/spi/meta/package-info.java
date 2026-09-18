/**
 * The ontology snapshot vocabulary: immutable value types describing the application's meta-model
 * as read from OWL, independent of any RDF library.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) except sibling types from {@link org.sequeless.spi.ontology} and — as of
 * {@link org.sequeless.spi.meta.RollupRule}, which carries a {@code List<}{@link
 * org.sequeless.spi.query.Criterion}{@code >} of already-resolved filter clauses — from {@link
 * org.sequeless.spi.query}. This is a genuine mutual dependency between the two sub-packages, not
 * an oversight: {@code org.sequeless.spi.query}'s own package-info already documents the reverse
 * dependency on {@link org.sequeless.spi.meta.MetaModelSnapshot}, and this package now depends back
 * on {@code org.sequeless.spi.query.Criterion} in turn. {@link
 * org.sequeless.spi.meta.MetaModelSnapshot} is the aggregate root: an ontology IRI, its declared
 * prefixes, an {@link org.sequeless.spi.ontology.OntologyReport}, and every {@link
 * org.sequeless.spi.meta.TypeDefinition} it contains. A type carries {@link
 * org.sequeless.spi.meta.PropertyDefinition}s, sealed to {@link
 * org.sequeless.spi.meta.AttributeDefinition} (scalar-valued) and {@link
 * org.sequeless.spi.meta.RelationshipDefinition} (object-valued). {@link
 * org.sequeless.spi.meta.Cardinality} and {@link org.sequeless.spi.meta.DisplayHints} are shared
 * value types; {@link org.sequeless.spi.meta.StateMachineDefinition} remains a deliberately empty
 * placeholder for Phase 5, present now so the snapshot shape does not change again when that phase
 * lands. {@link org.sequeless.spi.meta.DerivationRule} is no longer such a placeholder: it is
 * sealed to {@link org.sequeless.spi.meta.RollupRule} (a declarative {@code sq:Rollup} aggregate)
 * and {@link org.sequeless.spi.meta.PluginRule} (a named {@code sq:Plugin} dispatched through
 * {@link org.sequeless.spi.derivation.DerivationPlugin}), with {@link
 * org.sequeless.spi.meta.AggregateFunction} as the shared function vocabulary also used by {@link
 * org.sequeless.spi.query.AggregateRequest}. The full mapping from OWL and the {@code sq:}
 * annotation vocabulary to these fields is documented in {@code docs/architecture/sq-vocabulary.md},
 * which this package's field names must match exactly.
 */
package org.sequeless.spi.meta;
