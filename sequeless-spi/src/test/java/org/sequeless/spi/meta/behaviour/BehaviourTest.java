package org.sequeless.spi.meta.behaviour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.schema.OntologyVersion;

class BehaviourTest {

    private static final OntologyVersion VERSION = new OntologyVersion(1);
    private static final String TASK = "https://example.org/ns#Task";
    private static final String TASK_LIFECYCLE = "https://example.org/ns#TaskLifecycle";
    private static final String OPEN_TASK_COUNT = "https://example.org/ns#openTaskCount";

    private static final State OPEN = new State("https://example.org/ns#Open", "Open", 1);
    private static final StateMachineDefinition LIFECYCLE =
            new StateMachineDefinition(TASK_LIFECYCLE, List.of(OPEN), OPEN, List.of());
    private static final DerivationRule WORKLOAD = new PluginRule("workload", false);

    @Test
    void stateMachineLooksUpByGovernedTypeIri() {
        Behaviour behaviour = new Behaviour(VERSION, Map.of(TASK, LIFECYCLE), Map.of());

        assertThat(behaviour.stateMachine(TASK)).contains(LIFECYCLE);
        assertThat(behaviour.stateMachine("https://example.org/ns#Missing")).isEmpty();
    }

    @Test
    void derivationLooksUpByPropertyIri() {
        Behaviour behaviour = new Behaviour(VERSION, Map.of(), Map.of(OPEN_TASK_COUNT, WORKLOAD));

        assertThat(behaviour.derivation(OPEN_TASK_COUNT)).contains(WORKLOAD);
        assertThat(behaviour.derivation("https://example.org/ns#Missing")).isEmpty();
    }

    @Test
    void anEmptyBehaviourHasNoStateMachinesAndNoDerivations() {
        Behaviour behaviour = new Behaviour(VERSION, Map.of(), Map.of());

        assertThat(behaviour.stateMachine(TASK)).isEmpty();
        assertThat(behaviour.derivation(OPEN_TASK_COUNT)).isEmpty();
    }

    @Test
    void carriesTheVersionOfTheActivationItWasReadFrom() {
        assertThat(new Behaviour(VERSION, Map.of(), Map.of()).ontologyVersion()).isEqualTo(VERSION);
    }

    @Test
    void rejectsNullOntologyVersion() {
        assertThatNullPointerException().isThrownBy(() -> new Behaviour(null, Map.of(), Map.of()));
    }

    @Test
    void rejectsNullStateMachinesAndDerivations() {
        assertThatNullPointerException().isThrownBy(() -> new Behaviour(VERSION, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new Behaviour(VERSION, Map.of(), null));
    }

    @Test
    void rejectsNullEntriesInTheMaps() {
        Map<String, StateMachineDefinition> machinesWithNull = new HashMap<>();
        machinesWithNull.put(TASK, null);
        Map<String, DerivationRule> derivationsWithNull = new HashMap<>();
        derivationsWithNull.put(OPEN_TASK_COUNT, null);

        assertThatNullPointerException().isThrownBy(() -> new Behaviour(VERSION, machinesWithNull, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new Behaviour(VERSION, Map.of(), derivationsWithNull));
    }

    @Test
    void rejectsNullLookupKeys() {
        Behaviour behaviour = new Behaviour(VERSION, Map.of(), Map.of());

        assertThatNullPointerException().isThrownBy(() -> behaviour.stateMachine(null));
        assertThatNullPointerException().isThrownBy(() -> behaviour.derivation(null));
    }

    @Test
    void mapsAreUnmodifiableAndUnaffectedByLaterMutationOfCallerMaps() {
        Map<String, StateMachineDefinition> callerMachines = new HashMap<>();
        callerMachines.put(TASK, LIFECYCLE);

        Behaviour behaviour = new Behaviour(VERSION, callerMachines, Map.of());
        callerMachines.put("https://example.org/ns#Project", LIFECYCLE);

        assertThat(behaviour.stateMachines()).containsOnlyKeys(TASK);
        assertThatThrownBy(() -> behaviour.stateMachines().put("x", LIFECYCLE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> behaviour.derivations().put("x", WORKLOAD))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
