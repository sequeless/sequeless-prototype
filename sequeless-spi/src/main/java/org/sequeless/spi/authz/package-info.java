/**
 * The authorization port: the first outbound port Sequeless defines, and the one Phase 0 proves
 * the SPI and adapter-discovery mechanisms end to end with.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except {@link org.sequeless.spi.Scope} from its parent package. {@link
 * org.sequeless.spi.authz.AuthorizationPort} is the port interface; {@link
 * org.sequeless.spi.authz.Operation} and {@link org.sequeless.spi.authz.AccessDecision} are its
 * request and response vocabulary. The full behavioural contract every implementation must
 * satisfy is documented on {@link org.sequeless.spi.authz.AuthorizationPort} itself, and asserted
 * mechanically by {@code sequeless-spi-testkit}'s {@code AuthorizationContract}.
 */
package org.sequeless.spi.authz;
