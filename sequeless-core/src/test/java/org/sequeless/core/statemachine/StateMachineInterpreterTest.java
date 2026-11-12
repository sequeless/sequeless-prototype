package org.sequeless.core.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.State;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.api.UserActionTrigger;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;

/**
 * Proves the two {@link StateMachineInterpreter#availableTransitions} overloads answer identically
 * for the same object: the {@link BusinessObject} one that the commit path uses, and the {@link
 * ObjectView} one that the Read path uses. Both feed one private implementation, so these tests
 * guard against the two ever drifting apart.
 */
class StateMachineInterpreterTest {

    private static final String NS = "https://sequeless.dev/ns/interpreter-test#";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String DRAFT_IRI = NS + "Draft";
    private static final String ACTIVE_IRI = NS + "Active";
    private static final String OWNER_IRI = NS + "owner";
    private static final String OWNER_GUARD = "self.owner != null";
    private static final String GUARD_MESSAGE = "Only an owned project can be activated";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));
    private static final State DRAFT = new State(DRAFT_IRI, "Draft", 0);
    private static final State ACTIVE = new State(ACTIVE_IRI, "Active", 1);
    private static final Transition ACTIVATE = new Transition(
            "activate",
            DRAFT_IRI,
            ACTIVE_IRI,
            new UserActionTrigger(),
            Optional.of(OWNER_GUARD),
            Optional.of(GUARD_MESSAGE));
    private static final StateMachineDefinition LIFECYCLE =
            new StateMachineDefinition(NS + "ProjectLifecycle", List.of(DRAFT, ACTIVE), DRAFT, List.of(ACTIVATE));
    private static final Behaviour BEHAVIOUR =
            new Behaviour(new OntologyVersion(1), Map.of(PROJECT_IRI, LIFECYCLE), Map.of());

    private final StateMachineInterpreter interpreter = new StateMachineInterpreter(new OwnerGuardPort());

    @Test
    void bothOverloadsAgreeAndAvailableWhenTheGuardPasses() {
        BusinessObject object = draftProject(Map.of(new PropertyRef(OWNER_IRI), Value.text("alice")));

        List<TransitionAvailability> fromObject = interpreter.availableTransitions(ALICE, BEHAVIOUR, object);
        List<TransitionAvailability> fromView = interpreter.availableTransitions(ALICE, BEHAVIOUR, viewOf(object));

        assertThat(fromView).isEqualTo(fromObject);
        assertThat(fromView).containsExactly(new TransitionAvailability("activate", true, Optional.empty()));
    }

    @Test
    void bothOverloadsAgreeAndCarryTheReasonWhenTheGuardFails() {
        BusinessObject object = draftProject(Map.of());

        List<TransitionAvailability> fromObject = interpreter.availableTransitions(ALICE, BEHAVIOUR, object);
        List<TransitionAvailability> fromView = interpreter.availableTransitions(ALICE, BEHAVIOUR, viewOf(object));

        assertThat(fromView).isEqualTo(fromObject);
        assertThat(fromView).containsExactly(new TransitionAvailability("activate", false, Optional.of(GUARD_MESSAGE)));
    }

    @Test
    void bothOverloadsReturnNothingForAnObjectWithoutAState() {
        BusinessObject object = object(new TypeRef(PROJECT_IRI), Optional.empty(), Map.of());

        List<TransitionAvailability> fromView = interpreter.availableTransitions(ALICE, BEHAVIOUR, viewOf(object));

        assertThat(fromView).isEmpty();
        assertThat(fromView).isEqualTo(interpreter.availableTransitions(ALICE, BEHAVIOUR, object));
    }

    @Test
    void bothOverloadsReturnNothingForATypeWithoutAStateMachine() {
        BusinessObject object = object(new TypeRef(TASK_IRI), Optional.of(DRAFT_IRI), Map.of());

        List<TransitionAvailability> fromView = interpreter.availableTransitions(ALICE, BEHAVIOUR, viewOf(object));

        assertThat(fromView).isEmpty();
        assertThat(fromView).isEqualTo(interpreter.availableTransitions(ALICE, BEHAVIOUR, object));
    }

    private static BusinessObject draftProject(Map<PropertyRef, Value> properties) {
        return object(new TypeRef(PROJECT_IRI), Optional.of(DRAFT_IRI), properties);
    }

    private static BusinessObject object(TypeRef type, Optional<String> state, Map<PropertyRef, Value> properties) {
        return new BusinessObject(
                ObjectId.random(),
                type,
                ALICE.tenantId(),
                1,
                state,
                properties,
                new Audit(NOW, "alice", NOW, "alice"),
                false);
    }

    private static ObjectView viewOf(BusinessObject object) {
        return new ObjectView(
                object.id(),
                object.type(),
                object.tenant(),
                object.version(),
                object.state(),
                object.properties(),
                object.audit(),
                object.deleted());
    }

    /** Evaluates the one guard these fixtures use: a project is owned when it has an {@code owner}. */
    private static final class OwnerGuardPort implements ExpressionPort {

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            if (OWNER_GUARD.equals(expression)) {
                return Value.bool(context.selfProperties().containsKey("owner"));
            }
            throw new UnsupportedOperationException("Unrecognized expression in test fixture: " + expression);
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
