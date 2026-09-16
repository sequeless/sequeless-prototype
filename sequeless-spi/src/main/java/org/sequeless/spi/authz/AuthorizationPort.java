package org.sequeless.spi.authz;

import org.sequeless.spi.Scope;

/**
 * The outbound port every authorization decision in Sequeless goes through. Exactly one
 * implementation is wired into the running application at a time, selected by configuration
 * property (see the app's {@code PortRegistry}); use cases never choose between implementations
 * themselves, and never make an authorization decision without consulting this port.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code AuthorizationContract} asserts every clause of
 * it mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> All three arguments — {@code scope}, {@code operation}, and {@code
 *       resource} — must be non-{@code null}. A conforming implementation throws {@link
 *       NullPointerException} when any argument is {@code null}. It must never turn a null
 *       argument into a deny decision: missing input is a programming error in the caller, not a
 *       fact about the caller's authorization, and collapsing the two would let a caller bug
 *       masquerade as a legitimate policy outcome.
 *   <li><b>Never throws for valid input.</b> For any non-null {@code scope}, any {@link
 *       Operation}, and any non-null {@code resource} string, {@link #decide(Scope, Operation,
 *       String)} must never throw and must return a non-null {@link AccessDecision} whose {@link
 *       AccessDecision#reason()} is also non-null. A well-formed request always gets an answer.
 *   <li><b>{@code resource} is an opaque string.</b> This port does not interpret {@code
 *       resource} beyond treating it as an identifier; typed resource references arrive in a
 *       later phase once the ontology exists. {@link #EVERYTHING} ("{@code *}") is the reserved
 *       sentinel meaning "no specific resource, decide over the operation alone." Implementations
 *       must accept any non-null opaque string, not just values they recognize.
 *   <li><b>Determinism.</b> Repeated calls with identical arguments (an equal {@code scope}, the
 *       same {@code operation}, an equal {@code resource}) must yield the same {@link
 *       AccessDecision#allowed()} value. Decisions must not depend on randomness or on call
 *       count. (An adapter backed by genuinely time-varying external state, such as a token whose
 *       validity expires, is a documented exception its own tests must call out explicitly — the
 *       testkit does not attempt to exercise that case.)
 * </ul>
 */
public interface AuthorizationPort {

    /**
     * Sentinel {@code resource} value meaning "no specific resource" — the decision is made over
     * the operation alone, independent of any particular target.
     */
    String EVERYTHING = "*";

    /**
     * Decides whether {@code operation} is authorized against {@code resource} for the caller and
     * tenant described by {@code scope}. See the interface-level javadoc for the full contract
     * this method must satisfy.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param operation the action being authorized; must not be {@code null}
     * @param resource an opaque resource identifier, or {@link #EVERYTHING}; must not be {@code
     *     null}
     * @return a non-null decision with a non-null {@link AccessDecision#reason()}
     * @throws NullPointerException if any argument is {@code null}
     */
    AccessDecision decide(Scope scope, Operation operation, String resource);
}
