package org.sequeless.spi.expression;

import org.sequeless.spi.object.Value;

/**
 * The outbound port every expression authored in the ontology is evaluated through — {@code
 * sq:guard}, {@code sq:SetProperty}/{@code sq:CreateObject}'s {@code sq:value}/{@code
 * sq:expression}, and the {@code ${expr}} templates on {@code sq:Webhook} and {@code sq:Log}.
 * Exactly one implementation is wired into the running application at a time, selected by
 * configuration property (see the app's {@code PortRegistry}); the core interpreter and the
 * automation adapters never evaluate an expression themselves, and never choose between
 * implementations.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code ExpressionContract} asserts every clause of it
 * mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface — {@code
 *       expression}, {@code template}, and {@code context} — must be non-{@code null}. A
 *       conforming implementation throws {@link NullPointerException} when any argument is {@code
 *       null}. It must never substitute a default or silently treat a null argument as an empty
 *       expression: missing input is a programming error in the caller, not a fact about the
 *       expression.
 *   <li><b>Variable bindings.</b> Both methods evaluate against the same bindings, derived from
 *       {@code context}: {@code self.<name>} resolves the entry of {@link
 *       ExpressionContext#selfProperties()} keyed by the short name {@code <name>}; {@code
 *       self.state} resolves {@link ExpressionContext#selfState()} (unset if empty); {@code
 *       principal} resolves {@link ExpressionContext#principalId()}. Neither method exposes any
 *       binding beyond these three.
 *   <li><b>{@link #evaluate(String, ExpressionContext)} takes a plain JEXL expression</b> — no
 *       {@code ${}} wrapper — and returns the single {@link Value} it evaluates to. It throws
 *       {@link ExpressionException} if {@code expression} fails to parse, if evaluating it would
 *       require reflection or a static method/class reference (sandboxing forbidding both is an
 *       adapter-level guarantee this contract tests directly), or if the result is JEXL-{@code
 *       null} (there is no {@code Value} that represents "no value" for a guard or property
 *       assignment to produce).
 *   <li><b>{@link #renderTemplate(String, ExpressionContext)} takes a JXLT template</b> using the
 *       {@code ${expr}} "immediate" placeholder syntax, evaluates every {@code ${expr}} occurrence
 *       against the same bindings as {@link #evaluate}, substitutes each with its rendered text,
 *       and returns the resulting string with any text outside {@code ${}} placeholders left
 *       untouched. It throws {@link ExpressionException} under the same conditions as {@link
 *       #evaluate} — a malformed placeholder, a sandbox rejection, or a JEXL-null result for any
 *       embedded expression.
 *   <li><b>Sandboxing is absolute.</b> Neither method ever permits an expression to reach outside
 *       the bindings described above — no reflection (e.g. {@code self.getClass()}), no static
 *       method or field access (e.g. {@code System.exit(1)}), and no other side-effecting
 *       operation. This is what lets expressions authored by a tenant in the ontology run inside
 *       the same process as every other tenant's data without becoming an escape hatch.
 * </ul>
 */
public interface ExpressionPort {

    /**
     * Evaluates {@code expression} — a plain JEXL expression, with no {@code ${}} wrapper — against
     * {@code context} and returns the resulting value. See the interface-level javadoc for the full
     * contract this method must satisfy, including variable bindings and sandboxing.
     *
     * @param expression the JEXL source to evaluate; must not be {@code null}
     * @param context the bindings to evaluate {@code expression} against; must not be {@code null}
     * @return the non-null {@link Value} {@code expression} evaluates to
     * @throws NullPointerException if either argument is {@code null}
     * @throws ExpressionException if {@code expression} fails to parse, attempts reflection or a
     *     static method/class reference, or evaluates to JEXL-{@code null}
     */
    Value evaluate(String expression, ExpressionContext context);

    /**
     * Renders {@code template} — a JXLT template using the {@code ${expr}} "immediate" placeholder
     * syntax — against {@code context}, substituting each {@code ${expr}} occurrence with its
     * evaluated text and leaving everything else untouched. See the interface-level javadoc for the
     * full contract this method must satisfy, including variable bindings and sandboxing.
     *
     * @param template the JXLT template to render; must not be {@code null}
     * @param context the bindings to evaluate each embedded expression against; must not be {@code
     *     null}
     * @return the non-null, fully-substituted string
     * @throws NullPointerException if either argument is {@code null}
     * @throws ExpressionException if any embedded expression fails to parse, attempts reflection or
     *     a static method/class reference, or evaluates to JEXL-{@code null}
     */
    String renderTemplate(String template, ExpressionContext context);
}
