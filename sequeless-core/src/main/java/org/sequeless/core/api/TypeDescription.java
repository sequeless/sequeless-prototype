package org.sequeless.core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.ontology.behaviour.api.DerivationRule;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * One type as the type detail shows it: its schema definition, its state machine if it has one, its
 * derived properties, and the schema they were read with.
 *
 * @param type the resolved type's definition from the schema
 * @param stateMachine the state machine governing the type, or empty if it has none
 * @param derivations the derived properties of {@code type}, inherited ones included, keyed by
 *     property IRI; iterates in the order of {@code type.properties()}; unmodifiable
 * @param schema the schema read by the same {@link MetaModelService#describeType} call that
 *     resolved {@code type}, so {@code type}, {@code derivations} and {@code schema} all come from
 *     one activation; the rollup filters of {@code derivations} resolve against it
 * @see MetaModelService#describeType
 */
public record TypeDescription(
        TypeDefinition type,
        Optional<StateMachineDefinition> stateMachine,
        Map<String, DerivationRule> derivations,
        Schema schema) {

    /**
     * @throws NullPointerException if any argument is {@code null}
     */
    public TypeDescription {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        Objects.requireNonNull(derivations, "derivations must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        derivations = Collections.unmodifiableMap(new LinkedHashMap<>(derivations));
    }
}
