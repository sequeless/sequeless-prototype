/**
 * Test data shared across every per-port contract in this testkit.
 *
 * <p>This package sits directly beneath {@code org.sequeless.testkit} — not beneath any single
 * port's subpackage — because {@link org.sequeless.testkit.Fixtures} is port-agnostic: it builds
 * {@link org.sequeless.spi.Scope} and related values that every contract needs (authorization
 * today; an object-store contract and a query contract in later phases), so it must not live
 * somewhere that ties it to one port.
 */
package org.sequeless.testkit;
