package org.sequeless.spi.meta.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.schema.OntologyVersion;

class AutomationTest {

    private static final OntologyVersion VERSION = new OntologyVersion(1);
    private static final String LIFECYCLE = "https://example.org/ns#TaskLifecycle";
    private static final String DRAFT = "https://example.org/ns#Draft";
    private static final String ACTIVE = "https://example.org/ns#Active";
    private static final TransitionRef ACTIVATE = new TransitionRef(LIFECYCLE, DRAFT, "activate");
    private static final Action FIRST = new LogAction("first");
    private static final Action SECOND = new LogAction("second");

    @Test
    void actionsReturnsTheActionsOfTheTransitionInOrder() {
        Automation automation = new Automation(VERSION, Map.of(ACTIVATE, List.of(FIRST, SECOND)));

        assertThat(automation.actions(ACTIVATE)).containsExactly(FIRST, SECOND);
    }

    @Test
    void actionsReturnsAnEmptyListWhenTheTransitionHasNoEntry() {
        Automation automation = new Automation(VERSION, Map.of());

        assertThat(automation.actions(ACTIVATE)).isEmpty();
    }

    @Test
    void actionsDistinguishTransitionsByEveryPartOfTheRef() {
        Automation automation = new Automation(VERSION, Map.of(ACTIVATE, List.of(FIRST)));

        assertThat(automation.actions(new TransitionRef(LIFECYCLE, ACTIVE, "activate")))
                .isEmpty();
        assertThat(automation.actions(new TransitionRef(LIFECYCLE, DRAFT, "close")))
                .isEmpty();
        assertThat(automation.actions(new TransitionRef("https://example.org/ns#Other", DRAFT, "activate")))
                .isEmpty();
    }

    @Test
    void actionsRejectsNullTransitionRef() {
        Automation automation = new Automation(VERSION, Map.of());

        assertThatNullPointerException().isThrownBy(() -> automation.actions(null));
    }

    @Test
    void carriesTheVersionOfTheActivationItWasReadFrom() {
        assertThat(new Automation(VERSION, Map.of()).ontologyVersion()).isEqualTo(VERSION);
    }

    @Test
    void rejectsNullOntologyVersionAndNullMap() {
        assertThatNullPointerException().isThrownBy(() -> new Automation(null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new Automation(VERSION, null));
    }

    @Test
    void rejectsANullActionListForATransition() {
        Map<TransitionRef, List<Action>> withNullList = new HashMap<>();
        withNullList.put(ACTIVATE, null);

        assertThatNullPointerException().isThrownBy(() -> new Automation(VERSION, withNullList));
    }

    @Test
    void mapAndActionListsAreUnmodifiableAndUnaffectedByLaterMutationOfCallerCollections() {
        List<Action> callerActions = new ArrayList<>(List.of(FIRST));
        Map<TransitionRef, List<Action>> callerMap = new HashMap<>();
        callerMap.put(ACTIVATE, callerActions);

        Automation automation = new Automation(VERSION, callerMap);
        callerActions.add(SECOND);
        callerMap.put(new TransitionRef(LIFECYCLE, ACTIVE, "close"), List.of(SECOND));

        assertThat(automation.actions(ACTIVATE)).containsExactly(FIRST);
        assertThat(automation.transitionActions()).containsOnlyKeys(ACTIVATE);
        assertThatThrownBy(() -> automation.actions(ACTIVATE).add(SECOND))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> automation.transitionActions().put(ACTIVATE, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void transitionRefRejectsNullOrBlankParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef(null, DRAFT, "activate"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef("  ", DRAFT, "activate"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef(LIFECYCLE, null, "activate"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef(LIFECYCLE, "", "activate"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef(LIFECYCLE, DRAFT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitionRef(LIFECYCLE, DRAFT, " "));
    }

    @Test
    void transitionRefIsEqualByItsParts() {
        assertThat(new TransitionRef(LIFECYCLE, DRAFT, "activate")).isEqualTo(ACTIVATE);
    }
}
