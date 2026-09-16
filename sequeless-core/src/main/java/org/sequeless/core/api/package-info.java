/**
 * Inbound API of sequeless-core: the use case interfaces adapters call into, and the result types
 * those calls return.
 *
 * <p>{@link org.sequeless.core.api.WhoAmI} is the first such use case — Phase 0's proof that a
 * caller's own identity and authorization decision can be reported end to end through the {@link
 * org.sequeless.spi.authz.AuthorizationPort} SPI. {@link org.sequeless.core.api.WhoAmIResult}
 * carries its result, including the SPI's own {@link org.sequeless.spi.authz.AccessDecision}
 * rather than duplicating its allowed/reason fields. Implementations of the interfaces declared
 * here live in {@link org.sequeless.core.usecase}.
 */
package org.sequeless.core.api;
