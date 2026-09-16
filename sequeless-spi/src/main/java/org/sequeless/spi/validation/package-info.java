/**
 * The validation port: the outbound port that runs shape-based (SHACL) checks against a stored
 * object, beyond the structural checks the core performs itself.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except {@link org.sequeless.spi.Scope} from its parent package and
 * {@link org.sequeless.spi.meta.MetaModelSnapshot} and {@link org.sequeless.spi.object.BusinessObject}
 * from its sibling {@code org.sequeless.spi.meta} and {@code org.sequeless.spi.object} packages.
 * {@link org.sequeless.spi.validation.ValidationPort} is the port interface; {@link
 * org.sequeless.spi.validation.Violation} is its result vocabulary — one instance per failed
 * constraint, carrying the property IRI it applies to (or {@code ""} for an object-level violation)
 * and a human-readable message. The full behavioural contract every implementation must satisfy is
 * documented on {@link org.sequeless.spi.validation.ValidationPort} itself, and asserted
 * mechanically by {@code sequeless-spi-testkit}'s {@code ValidationContract}.
 */
package org.sequeless.spi.validation;
