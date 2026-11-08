/**
 * The Behaviour half of an ontology activation: the state machines that govern object types, the
 * transitions between their states and what makes each transition fire, and the rules that derive
 * property values.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) and the kernel, except sibling types in {@code org.sequeless.spi.meta.schema}
 * and, through {@link org.sequeless.spi.meta.behaviour.RollupRule}, the {@link
 * org.sequeless.spi.query.Criterion} filter clauses that rule carries already resolved. {@link
 * org.sequeless.spi.meta.behaviour.Behaviour} is the aggregate of this half: its state machines are
 * keyed by the IRI of the type each one governs, and its derivation rules by the IRI of the property
 * each one derives. {@link org.sequeless.spi.meta.behaviour.BehaviourSource} is the read port.
 *
 * <p>A {@link org.sequeless.spi.meta.behaviour.StateMachineDefinition} carries the {@link
 * org.sequeless.spi.meta.behaviour.State}s a type's objects move through, the state they start in,
 * and the {@link org.sequeless.spi.meta.behaviour.Transition}s between them. The actions a transition
 * runs are not here: they are in {@code org.sequeless.spi.meta.automation}. Each {@link
 * org.sequeless.spi.meta.behaviour.DerivationRule} is sealed to {@link
 * org.sequeless.spi.meta.behaviour.RollupRule} (a declarative {@code sq:Rollup} aggregate) and {@link
 * org.sequeless.spi.meta.behaviour.PluginRule} (a named {@code sq:Plugin} dispatched through {@link
 * org.sequeless.spi.derivation.DerivationPlugin}).
 *
 * <p>Every {@link org.sequeless.spi.meta.behaviour.Transition} carries a {@link
 * org.sequeless.spi.meta.behaviour.TriggerSpec}: sealed to {@link
 * org.sequeless.spi.meta.behaviour.UserActionTrigger} ({@code sq:UserAction}, a user's own REST
 * request), {@link org.sequeless.spi.meta.behaviour.OnChangeTrigger} ({@code sq:OnChange}, a related
 * object's data changing, named via {@code sq:watch}), {@link
 * org.sequeless.spi.meta.behaviour.TimerTrigger} ({@code sq:Timer}, an elapsed {@code sq:after}
 * duration), and {@link org.sequeless.spi.meta.behaviour.ExternalSignalTrigger} ({@code
 * sq:ExternalSignal}, a named {@code sq:signalName} arriving over the signals endpoint).
 * {@link org.sequeless.spi.meta.behaviour.TriggerKind} is the plain enum discriminator shared by all
 * four, used where a full {@code TriggerSpec} pattern-match would be overkill.
 */
package org.sequeless.spi.meta.behaviour;
