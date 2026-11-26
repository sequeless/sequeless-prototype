/**
 * Inbound API of sequeless-core: the use case interfaces adapters call into, and the result types
 * those calls return.
 *
 * <p>{@link org.sequeless.store.core.api.WhoAmI} is the first such use case — Phase 0's proof that a
 * caller's own identity and authorization decision can be reported end to end through the {@link
 * org.sequeless.store.spi.AuthorizationPort} SPI. {@link org.sequeless.store.core.api.WhoAmIResult}
 * carries its result, including the SPI's own {@link org.sequeless.store.api.AccessDecision}
 * rather than duplicating its allowed/reason fields. Implementations of the interfaces declared
 * here live in {@link org.sequeless.store.core.usecase}.
 */
package org.sequeless.store.core.api;
