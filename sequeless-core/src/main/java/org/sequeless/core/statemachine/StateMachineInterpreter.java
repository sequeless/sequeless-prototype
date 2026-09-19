package org.sequeless.core.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.Scope;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionException;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Value;

/**
 * Resolves every {@link Transition} available from a {@link BusinessObject}'s current {@link
 * BusinessObject#state()}, evaluating each transition's optional {@link Transition#guard()}
 * through an {@link ExpressionPort}. This is the one place both {@code
 * org.sequeless.core.usecase.DefaultTransitionService} (the write path, which uses this to decide
 * whether a requested transition may fire) and a later REST read path ({@code GET
 * .../transitions}) compute transition availability, so the two never drift apart.
 *
 * <p>An object of a type with no {@link TypeDefinition#stateMachine()}, or one whose {@link
 * BusinessObject#state()} is absent, simply has no available transitions — {@link
 * #availableTransitions} returns an empty list rather than throwing, since "no state machine in
 * scope" is not an error condition.
 */
public final class StateMachineInterpreter {

    private final ExpressionPort expressionPort;

    /**
     * @param expressionPort the port every non-empty {@link Transition#guard()} is evaluated
     *     through; must not be {@code null}
     * @throws NullPointerException if {@code expressionPort} is {@code null}
     */
    public StateMachineInterpreter(ExpressionPort expressionPort) {
        this.expressionPort =
            Objects.requireNonNull(expressionPort, "expressionPort must not be null");
    }

    /**
     * @param scope the tenant and principal a guard is evaluated on behalf of; must not be {@code
     *     null}
     * @param snapshot the meta-model snapshot {@code object}'s type is resolved against; must not
     *     be {@code null}
     * @param object the object whose available transitions are computed; must not be {@code null}
     * @return every transition departing from {@code object}'s current state, each with its
     *     availability and (when unavailable) a reason; empty if {@code object}'s type has no state
     *     machine, or {@code object} has no current state
     * @throws NullPointerException if any argument is {@code null}
     * @throws ExpressionException if evaluating a guard fails — a malformed guard is a real error,
     *     not "just unavailable", so it is allowed to propagate rather than being swallowed into an
     *     unavailable result
     */
    public List<TransitionAvailability> availableTransitions(
        Scope scope, MetaModelSnapshot snapshot, BusinessObject object) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(object, "object must not be null");

        Optional<TypeDefinition> type = snapshot.type(object.type().iri());
        if (type.isEmpty() || type.get().stateMachine().isEmpty() || object.state().isEmpty()) {
            return List.of();
        }
        StateMachineDefinition machine = type.get().stateMachine().get();
        String currentState = object.state().get();

        List<TransitionAvailability> result = new ArrayList<>();
        for (Transition transition : machine.transitions()) {
            if (!Objects.equals(transition.fromStateIri(), currentState)) {
                continue;
            }
            result.add(evaluate(scope, object, transition));
        }
        return result;
    }

    private TransitionAvailability evaluate(Scope scope, BusinessObject object, Transition transition) {
        if (transition.guard().isEmpty()) {
            return new TransitionAvailability(transition.name(), true, Optional.empty());
        }
        ExpressionContext context = buildContext(scope, object);
        Value result = expressionPort.evaluate(transition.guard().get(), context);
        boolean passed = result instanceof BoolValue bool && bool.value();
        if (passed) {
            return new TransitionAvailability(transition.name(), true, Optional.empty());
        }
        String reason =
            transition
                .guardMessage()
                .orElse("Guard for transition '" + transition.name() + "' was not satisfied");
        return new TransitionAvailability(transition.name(), false, Optional.of(reason));
    }

    private ExpressionContext buildContext(Scope scope, BusinessObject object) {
        Map<String, Value> selfProperties = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : object.properties().entrySet()) {
            selfProperties.put(shortName(entry.getKey().iri()), entry.getValue());
        }
        return new ExpressionContext(selfProperties, object.state(), scope.principal().id());
    }

    /**
     * Duplicated from {@code DefaultBusinessObjectService}'s identically-named private helper
     * rather than shared: this codebase's established convention for this specific short pure
     * function (the substring after an IRI's last {@code #}, else after its last {@code /}, else
     * the whole IRI) is to duplicate it at each use site rather than introduce a shared dependency
     * for one line of logic.
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
