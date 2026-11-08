package org.sequeless.core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.meta.behaviour.DerivationRule;
import org.sequeless.spi.meta.behaviour.StateMachineDefinition;
import org.sequeless.spi.meta.schema.TypeDefinition;

/**
 * One type as the type detail shows it: its schema definition, its state machine if it has one, and
 * its derived properties.
 *
 * @param type the resolved type's definition from the schema
 * @param stateMachine the state machine governing the type, or empty if it has none
 * @param derivations the derived properties of {@code type}, inherited ones included, keyed by
 *     property IRI; iterates in the order of {@code type.properties()}; unmodifiable
 * @see MetaModelService#describeType
 */
public record TypeDescription(
        TypeDefinition type, Optional<StateMachineDefinition> stateMachine, Map<String, DerivationRule> derivations) {

    /**
     * @throws NullPointerException if any argument is {@code null}
     */
    public TypeDescription {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        Objects.requireNonNull(derivations, "derivations must not be null");
        derivations = Collections.unmodifiableMap(new LinkedHashMap<>(derivations));
    }
}
