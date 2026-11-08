package org.sequeless.spi.meta.behaviour;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.meta.schema.OntologyVersion;

/**
 * The Behaviour half of one ontology activation: the state machines that govern object types, and
 * the rules that derive property values. State machines are keyed by the IRI of the type they
 * govern, and derivation rules by the IRI of the property they derive, which is how the OWL
 * annotations {@code sq:appliesTo} and {@code sq:derivedBy} name them.
 *
 * @param ontologyVersion the version of the activation this half was read from; must not be
 *     {@code null}
 * @param stateMachines the state machines, keyed by the IRI of the governed type; must not be
 *     {@code null}; returned as an unmodifiable copy so callers cannot mutate this behaviour after
 *     construction
 * @param derivations the derivation rules, keyed by the IRI of the derived property; must not be
 *     {@code null}; returned as an unmodifiable copy so callers cannot mutate this behaviour after
 *     construction
 */
public record Behaviour(
        OntologyVersion ontologyVersion,
        Map<String, StateMachineDefinition> stateMachines,
        Map<String, DerivationRule> derivations) {

    public Behaviour {
        Objects.requireNonNull(ontologyVersion, "ontologyVersion must not be null");
        Objects.requireNonNull(stateMachines, "stateMachines must not be null");
        stateMachines = Map.copyOf(stateMachines);
        Objects.requireNonNull(derivations, "derivations must not be null");
        derivations = Map.copyOf(derivations);
    }

    /**
     * @param typeIri the IRI of the governed type; must not be {@code null}
     * @return the state machine that governs that type, or {@link Optional#empty()} if none does
     * @throws NullPointerException if {@code typeIri} is {@code null}
     */
    public Optional<StateMachineDefinition> stateMachine(String typeIri) {
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        return Optional.ofNullable(stateMachines.get(typeIri));
    }

    /**
     * @param propertyIri the IRI of the property; must not be {@code null}
     * @return the rule that derives that property's value, or {@link Optional#empty()} if the
     *     property is an ordinary stored property
     * @throws NullPointerException if {@code propertyIri} is {@code null}
     */
    public Optional<DerivationRule> derivation(String propertyIri) {
        Objects.requireNonNull(propertyIri, "propertyIri must not be null");
        return Optional.ofNullable(derivations.get(propertyIri));
    }
}
