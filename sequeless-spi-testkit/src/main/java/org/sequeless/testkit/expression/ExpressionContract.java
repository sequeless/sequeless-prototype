package org.sequeless.testkit.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionException;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;

/**
 * The mechanical form of the behavioural contract documented on {@link ExpressionPort}'s
 * interface-level javadoc. Every {@link ExpressionPort} implementation — adapter or test double —
 * is expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever port {@link #port()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #port()} to return the implementation under test:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends ExpressionContract {
 *     protected ExpressionPort port() {
 *         return new MyAdapter();
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> {@link #evaluatesIntegerLiteral()}
 * asserts only that the result is an {@link IntegerValue} carrying the expected numeric value, not
 * which JEXL-internal numeric type produced it — the JEXL-result-to-{@link Value} conversion is an
 * adapter concern this contract must stay agnostic to, exactly as {@link
 * org.sequeless.testkit.authz.AuthorizationContract} stays agnostic to a policy's actual
 * allow/deny outcome. Sandbox rejection is asserted only by exception type ({@link
 * ExpressionException}), never by message text, since message wording is also an adapter choice.
 */
public abstract class ExpressionContract {

    /**
     * @return the {@link ExpressionPort} implementation under test; invoked fresh for every {@code
     *     @Test} method, so implementors may return a new instance each time or a shared one,
     *     whichever suits the port under test
     */
    protected abstract ExpressionPort port();

    @Test
    void evaluateRejectsNullExpression() {
        ExpressionPort port = port();
        assertThatNullPointerException().isThrownBy(() -> port.evaluate(null, emptyContext()));
    }

    @Test
    void evaluateRejectsNullContext() {
        ExpressionPort port = port();
        assertThatNullPointerException().isThrownBy(() -> port.evaluate("1", null));
    }

    @Test
    void renderTemplateRejectsNullTemplate() {
        ExpressionPort port = port();
        assertThatNullPointerException()
            .isThrownBy(() -> port.renderTemplate(null, emptyContext()));
    }

    @Test
    void renderTemplateRejectsNullContext() {
        ExpressionPort port = port();
        assertThatNullPointerException().isThrownBy(() -> port.renderTemplate("hello", null));
    }

    @Test
    void evaluatesIntegerLiteral() {
        ExpressionPort port = port();
        Value result = port.evaluate("1 + 2", emptyContext());
        assertThat(result).isInstanceOf(IntegerValue.class);
        assertThat(((IntegerValue) result).value()).isEqualTo(3L);
    }

    @Test
    void evaluatesStringConcatenation() {
        ExpressionPort port = port();
        ExpressionContext context =
            context(Map.of("title", Value.text("Launch")), Optional.empty(), "user-1");
        Value result = port.evaluate("'Kickoff: ' + self.title", context);
        assertThat(result).isEqualTo(new TextValue("Kickoff: Launch"));
    }

    @Test
    void evaluatesBooleanGuardReferencingSelfProperty() {
        ExpressionPort port = port();
        ExpressionContext withOwner =
            context(Map.of("owner", Value.text("user-2")), Optional.empty(), "user-1");
        ExpressionContext withoutOwner = context(Map.of(), Optional.empty(), "user-1");

        assertThat(port.evaluate("self.owner != null", withOwner)).isEqualTo(new BoolValue(true));
        assertThat(port.evaluate("self.owner != null", withoutOwner))
            .isEqualTo(new BoolValue(false));
    }

    @Test
    void evaluatesBooleanGuardReferencingSelfState() {
        ExpressionPort port = port();
        ExpressionContext context = context(Map.of(), Optional.of("Active"), "user-1");
        assertThat(port.evaluate("self.state == 'Active'", context))
            .isEqualTo(new BoolValue(true));
    }

    @Test
    void evaluatesExpressionReferencingPrincipal() {
        ExpressionPort port = port();
        ExpressionContext context = context(Map.of(), Optional.empty(), "user-1");
        assertThat(port.evaluate("principal == 'user-1'", context)).isEqualTo(new BoolValue(true));
    }

    @Test
    void renderTemplateInterpolatesSelfProperty() {
        ExpressionPort port = port();
        ExpressionContext context =
            context(Map.of("title", Value.text("Acme")), Optional.empty(), "user-1");
        assertThat(port.renderTemplate("Hello ${self.title}", context)).isEqualTo("Hello Acme");
    }

    @Test
    void reflectionAttemptIsRejected() {
        ExpressionPort port = port();
        assertThatThrownBy(() -> port.evaluate("self.getClass().getName()", emptyContext()))
            .isInstanceOf(ExpressionException.class);
    }

    @Test
    void staticMethodCallAttemptIsRejected() {
        ExpressionPort port = port();
        assertThatThrownBy(() -> port.evaluate("System.exit(1)", emptyContext()))
            .isInstanceOf(ExpressionException.class);
    }

    @Test
    void malformedExpressionIsRejected() {
        ExpressionPort port = port();
        assertThatThrownBy(() -> port.evaluate("self.title +", emptyContext()))
            .isInstanceOf(ExpressionException.class);
    }

    private static ExpressionContext emptyContext() {
        return context(Map.of(), Optional.empty(), "user-1");
    }

    private static ExpressionContext context(
        Map<String, Value> selfProperties, Optional<String> selfState, String principalId) {
        return new ExpressionContext(selfProperties, selfState, principalId);
    }
}
