/**
 * Implementations of the use case interfaces declared in {@link org.sequeless.store.core.api}.
 *
 * <p>{@code org.sequeless.app.admin.DefaultWhoAmI} is the reference implementation of {@code
 * org.sequeless.app.admin.WhoAmI}: it consults the injected {@link
 * org.sequeless.store.spi.AuthorizationPort} for every call — never short-circuiting or caching
 * the decision — and translates a denial into an {@link org.sequeless.store.core.AuthorizationException}
 * carrying the port's own decision.
 */
package org.sequeless.store.core.usecase;
