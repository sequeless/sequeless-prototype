/**
 * The mechanical contract test for {@link org.sequeless.spi.authz.AuthorizationPort}.
 *
 * <p>{@link org.sequeless.testkit.authz.AuthorizationContract} asserts every clause of the
 * behavioural contract documented on {@code AuthorizationPort} itself, against any implementation
 * supplied by a subclass. It intentionally never inspects {@code AccessDecision.allowed()} against
 * a fixed expectation — only shape, null-safety, and determinism — so that both a permit-all and a
 * deny-all implementation can pass it unmodified. This module's own {@code
 * DenyAllAuthorizationPortContractTest} (in {@code src/test/java}) is the standing proof of that
 * property.
 */
package org.sequeless.testkit.authz;
