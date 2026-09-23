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
 * value types; {@link org.sequeless.spi.meta.StateMachineDefinition} is no longer a placeholder: it
 * carries the {@link org.sequeless.spi.meta.State}s a type's objects move through, the {@link
 * org.sequeless.spi.meta.State} they start in, and the {@link org.sequeless.spi.meta.Transition}s
 * between them, each transition's actions sealed to {@link
 * org.sequeless.spi.meta.SetPropertyAction}, {@link org.sequeless.spi.meta.CreateObjectAction},
 * {@link org.sequeless.spi.meta.WebhookAction}, and {@link org.sequeless.spi.meta.LogAction} via
 * the {@link org.sequeless.spi.meta.Action} sealed interface. {@link
 * org.sequeless.spi.meta.DerivationRule} is likewise no longer a placeholder: it is sealed to
 * {@link org.sequeless.spi.meta.RollupRule} (a declarative {@code sq:Rollup} aggregate) and {@link
 * org.sequeless.spi.meta.PluginRule} (a named {@code sq:Plugin} dispatched through {@link
 * org.sequeless.spi.derivation.DerivationPlugin}), with {@link
 * org.sequeless.spi.meta.AggregateFunction} as the shared function vocabulary also used by {@link
 * org.sequeless.spi.query.AggregateRequest}, and {@link org.sequeless.spi.meta.DerivationRule#materialised()}
 * marking whether a rule's value is additionally kept correct in {@code sq_object.props} so it can
 * be filtered and sorted, not just read. The full mapping from OWL and the {@code sq:} annotation
 * vocabulary to these fields is documented in {@code docs/architecture/sq-vocabulary.md}, which
 * this package's field names must match exactly.
 *
 * <p>Every {@link org.sequeless.spi.meta.Transition} now also carries a {@link
 * org.sequeless.spi.meta.TriggerSpec}: sealed to {@link org.sequeless.spi.meta.UserActionTrigger}
 * ({@code sq:UserAction}, a user's own REST request — every transition's implicit trigger before
 * this phase), {@link org.sequeless.spi.meta.OnChangeTrigger} ({@code sq:OnChange}, a related
 * object's data changing, named via {@code sq:watch}), {@link org.sequeless.spi.meta.TimerTrigger}
 * ({@code sq:Timer}, an elapsed {@code sq:after} duration), and {@link
 * org.sequeless.spi.meta.ExternalSignalTrigger} ({@code sq:ExternalSignal}, a named {@code
 * sq:signalName} arriving over the signals endpoint). {@link org.sequeless.spi.meta.TriggerKind}
 * is the plain enum discriminator shared by all four, used where a full {@code TriggerSpec}
 * pattern-match would be overkill — comparing against an expected kind, or serializing a
 * transition descriptor for REST.
 */
package org.sequeless.spi.meta;
