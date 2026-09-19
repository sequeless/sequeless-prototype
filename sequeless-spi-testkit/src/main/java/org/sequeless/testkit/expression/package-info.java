/**
 * The mechanical contract test for {@link org.sequeless.spi.expression.ExpressionPort}.
 *
 * <p>{@link org.sequeless.testkit.expression.ExpressionContract} asserts every clause of the
 * behavioural contract documented on {@code ExpressionPort} itself, against any implementation
 * supplied by a subclass via {@code port()}. It checks null-safety, the {@code self}/{@code
 * self.state}/{@code principal} variable bindings, that {@code renderTemplate} correctly
 * interpolates {@code ${expr}} placeholders, and that sandboxing rejects reflection and static
 * method/class access — but it deliberately does not pin down which concrete {@link
 * org.sequeless.spi.object.Value} subtype numeric literals produce beyond what {@link
 * org.sequeless.spi.object.Value}'s own static factories guarantee, so this contract stays
 * adapter-agnostic rather than describing one JEXL adapter's own result-conversion choices. The
 * JEXL adapter that extends this class (a later phase) is the standing proof that a real,
 * sandboxed implementation can pass it unmodified.
 */
package org.sequeless.testkit.expression;
