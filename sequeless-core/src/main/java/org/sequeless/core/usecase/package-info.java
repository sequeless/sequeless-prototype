/**
 * Implementations of the use case interfaces declared in {@link org.sequeless.core.api}.
 *
 * <p>{@link org.sequeless.core.usecase.DefaultWhoAmI} is the reference implementation of {@link
 * org.sequeless.core.api.WhoAmI}: it consults the injected {@link
 * org.sequeless.store.spi.AuthorizationPort} for every call — never short-circuiting or caching
 * the decision — and translates a denial into an {@link org.sequeless.core.AuthorizationException}
 * carrying the port's own decision.
 */
package org.sequeless.core.usecase;
