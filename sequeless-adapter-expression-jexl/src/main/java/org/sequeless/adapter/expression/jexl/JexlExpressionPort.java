package org.sequeless.adapter.expression.jexl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JxltEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionException;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;

/**
 * The default {@link ExpressionPort} adapter, backed by Apache Commons JEXL 3 in a sandboxed
 * configuration: no reflection, no static method or field access, no other side-effecting
 * operation reachable from an expression authored in the ontology. One {@link JexlEngine} and one
 * {@link JxltEngine} are built once, as fields on a single instance, and reused for every call —
 * neither engine has any thread-safety caveat beyond {@code setClassLoader}, which this adapter
 * never calls.
 *
 * <p><b>Sandboxing mechanism.</b> This adapter uses {@link JexlPermissions} ({@code JexlBuilder}'s
 * permission mechanism since JEXL 3.3), not the older {@code JexlSandbox} (3.0) mechanism. Once at
 * least one wildcard ({@code pkg.*}) line is supplied to {@link JexlPermissions#parse(String...)},
 * the result becomes a default-deny whitelist: any package with no matching wildcard is denied
 * outright, regardless of any explicit allow/deny line. {@link #PERMISSIONS} deliberately does
 * <em>not</em> reuse {@link JexlPermissions#RESTRICTED} unmodified: RESTRICTED's own {@code
 * java.io.*} wildcard only denies {@code File{}}/{@code FileDescriptor{}} by name, leaving classes
 * like {@code FileOutputStream}/{@code FileWriter} constructible via JEXL 3.6's {@code new
 * pkg.Class(args)} syntax. Since expression and JXLT template evaluation here never needs file or
 * network I/O, {@link #PERMISSIONS} is a tighter, purpose-built set that omits the {@code
 * java.io.*}/{@code java.nio.*} wildcards entirely (so all of {@code java.io} and {@code java.nio}
 * are denied by the default-deny rule, not by name) and adds a {@code java.time.*} wildcard that
 * RESTRICTED lacks, needed because {@link DateValue}/{@link DateTimeValue} unwrap to {@link
 * LocalDate}/{@link Instant}. A future reader tempted to "simplify" this back to {@code
 * RESTRICTED} should not: that would silently reopen the file-write gap described above.
 *
 * <p><b>Variable bindings.</b> Every {@link Value} in {@link ExpressionContext#selfProperties()} is
 * recursively unwrapped to its plain Java form ({@link TextValue} to {@link String}, {@link
 * IntegerValue} to {@link Long}, {@link DecimalValue} to {@link BigDecimal}, {@link BoolValue} to
 * {@link Boolean}, {@link DateTimeValue} to {@link Instant}, {@link DateValue} to {@link
 * LocalDate}, {@link ReferenceValue} to the referenced id as a plain {@link String} — never the
 * raw {@code ObjectId} record, since {@code org.sequeless.spi.object} is not on {@link
 * #PERMISSIONS}' allow-list and a bare id string is enough for every documented use — and {@link
 * ListValue} to an {@link ArrayList} of recursively-unwrapped elements) and bound into a plain
 * {@link Map} under the key {@code "self"}. JEXL resolves a bean-style {@code .property} or {@code
 * [key]} access on any {@code self} bound to a {@link Map} by falling back to {@code
 * self.get(key)}, returning {@code null} on a missing key — exactly what {@code self.owner !=
 * null} needs when {@code owner} is unset. {@link ExpressionContext#selfState()} is injected into
 * that same map under key {@code "state"} so {@code self.state} reads naturally; {@link
 * ExpressionContext#principalId()} is bound directly under {@code "principal"}.
 *
 * <p><b>{@code strict(true)} is required, not optional.</b> Without it, an undefined-variable or
 * null-operand error silently evaluates to {@code null} instead of throwing, which would make the
 * sandbox-rejection tests pass for the wrong reason.
 */
public final class JexlExpressionPort implements ExpressionPort {

    /**
     * A tighter, purpose-built permission set than {@link JexlPermissions#RESTRICTED} — see this
     * class's javadoc for the full rationale. Deliberately has no {@code java.io.*}/{@code
     * java.nio.*} wildcard (expressions never need file or network I/O, and RESTRICTED's own
     * wildcard there leaves classes like {@code FileOutputStream} constructible) and adds a {@code
     * java.time.*} wildcard RESTRICTED lacks (needed for {@link DateValue}/{@link DateTimeValue}
     * unwrapping to {@link LocalDate}/{@link Instant}).
     */
    private static final JexlPermissions PERMISSIONS =
        JexlPermissions.parse(
            "java.lang.*",
            "java.math.*",
            "java.text.*",
            "java.util.*",
            "java.time.*",
            "java.lang { Runtime{} System{} ProcessBuilder{} Process{} RuntimePermission{}"
                + " SecurityManager{} Thread{} ThreadGroup{} Class{} ClassLoader{} }",
            "java.lang.annotation {}",
            "java.lang.instrument {}",
            "java.lang.invoke {}",
            "java.lang.management {}",
            "java.lang.ref {}",
            "java.lang.reflect {}",
            "java.net {}",
            "java.rmi {}");

    private final JexlEngine engine;
    private final JxltEngine jxlt;

    public JexlExpressionPort() {
        this.engine =
            new JexlBuilder().permissions(PERMISSIONS).strict(true).silent(false).create();
        this.jxlt = engine.createJxltEngine();
    }

    @Override
    public Value evaluate(String expression, ExpressionContext context) {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Object result;
        try {
            result = engine.createExpression(expression).evaluate(bindings(context));
        } catch (JexlException e) {
            throw new ExpressionException("failed to evaluate expression: " + expression, e);
        }
        if (result == null) {
            throw new ExpressionException(
                "expression evaluated to null, which Value cannot represent: " + expression);
        }
        return toValue(result);
    }

    @Override
    public String renderTemplate(String template, ExpressionContext context) {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Object result;
        try {
            result = jxlt.createExpression(template).evaluate(bindings(context));
        } catch (JexlException e) {
            // JxltEngine.Exception extends JexlException, so this catches both.
            throw new ExpressionException("failed to render template: " + template, e);
        }
        if (result == null) {
            throw new ExpressionException(
                "template evaluated to null, which Value cannot represent: " + template);
        }
        return result instanceof String text ? text : String.valueOf(result);
    }

    private JexlContext bindings(ExpressionContext context) {
        Map<String, Object> self = new HashMap<>();
        for (Map.Entry<String, Value> entry : context.selfProperties().entrySet()) {
            self.put(entry.getKey(), unwrap(entry.getValue()));
        }
        self.put("state", context.selfState().orElse(null));

        JexlContext jexlContext = new MapContext();
        jexlContext.set("self", self);
        jexlContext.set("principal", context.principalId());
        return jexlContext;
    }

    /**
     * Recursively unwraps a {@link Value} to the plain Java form JEXL's built-in {@link Map}
     * property resolution understands. An exhaustive switch over the sealed {@link Value}
     * interface, with no {@code default} branch, so a future new {@code Value} variant is a
     * compile error here, not a silently-wrong fallback.
     */
    private static Object unwrap(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> integer.value();
            case DecimalValue decimal -> decimal.value();
            case BoolValue bool -> bool.value();
            case DateTimeValue dateTime -> dateTime.value();
            case DateValue date -> date.value();
            case ReferenceValue reference -> reference.target().value().toString();
            case ListValue list -> {
                List<Object> unwrapped = new ArrayList<>(list.values().size());
                for (Value element : list.values()) {
                    unwrapped.add(unwrap(element));
                }
                yield unwrapped;
            }
        };
    }

    /**
     * Converts a raw JEXL evaluation result back to a {@link Value}, dispatching on the result's
     * runtime type since JEXL itself has no notion of this SPI's {@link Value} hierarchy.
     *
     * @throws ExpressionException if {@code result}'s runtime type is not one this adapter knows
     *     how to represent as a {@link Value}
     */
    private static Value toValue(Object result) {
        if (result instanceof Long || result instanceof Integer || result instanceof BigInteger) {
            return Value.integer(((Number) result).longValue());
        }
        if (result instanceof BigDecimal decimal) {
            return Value.decimal(decimal);
        }
        if (result instanceof Double || result instanceof Float) {
            return Value.decimal(BigDecimal.valueOf(((Number) result).doubleValue()));
        }
        if (result instanceof Boolean bool) {
            return Value.bool(bool);
        }
        if (result instanceof Instant instant) {
            return Value.dateTime(instant);
        }
        if (result instanceof LocalDate date) {
            return Value.date(date);
        }
        if (result instanceof CharSequence text) {
            return Value.text(text.toString());
        }
        throw new ExpressionException(
            "cannot represent JEXL result of type " + result.getClass() + " as a Value");
    }
}
