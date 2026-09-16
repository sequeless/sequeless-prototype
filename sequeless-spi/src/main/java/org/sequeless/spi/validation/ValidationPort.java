package org.sequeless.spi.validation;

import java.util.List;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.BusinessObject;

/**
 * The outbound port that runs shape-based validation (SHACL, per DR-06) against a {@link
 * BusinessObject}, beyond the structural checks — required properties, cardinality, datatype
 * agreement, reference integrity — the core performs itself before ever calling this port. Exactly
 * one implementation is wired into the running application at a time, selected by configuration
 * property (see the app's {@code PortRegistry}).
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code ValidationContract} asserts every clause of it
 * mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <p>What this contract deliberately does not check: which violations a specific ontology's shapes
 * produce for a specific object is entirely shape-specific, and is exercised by the adapter's own
 * tests against its own shapes, not by the testkit contract. The testkit contract asserts only
 * null-safety, determinism, and the "unmodifiable, non-null list" shape of the result — never the
 * content of any particular violation.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument — {@code scope}, {@code snapshot}, and {@code object}
 *       — must be non-{@code null}. A conforming implementation throws {@link
 *       NullPointerException} when any argument is {@code null}.
 *   <li><b>Never throws for a well-formed object.</b> For any non-null {@code scope}, {@code
 *       snapshot}, and {@code object}, {@link #validate(Scope, MetaModelSnapshot, BusinessObject)}
 *       must never throw and must return a non-null, unmodifiable {@link List}. An object that
 *       fails every shape in the ontology still gets a normal return value — a non-empty list of
 *       {@link Violation}s — never an exception.
 *   <li><b>Empty means valid.</b> An empty list means {@code object} satisfies every applicable
 *       shape in {@code snapshot}; a non-empty list means it does not, with one {@link Violation}
 *       per failed constraint.
 *   <li><b>Determinism.</b> Repeated calls with equal arguments (an equal {@code scope}, an equal
 *       {@code snapshot}, an equal {@code object}) must return lists that are equal in content,
 *       independent of call count or wall-clock time.
 * </ul>
 */
public interface ValidationPort {

    /**
     * Validates {@code object} against every shape in {@code snapshot} that applies to its type.
     * See the interface-level javadoc for the full contract this method must satisfy.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the type system {@code object} is validated against; must not be {@code null}
     * @param object the object to validate; must not be {@code null}
     * @return a non-null, unmodifiable list of violations; empty if {@code object} is valid
     * @throws NullPointerException if any argument is {@code null}
     */
    List<Violation> validate(Scope scope, MetaModelSnapshot snapshot, BusinessObject object);
}
