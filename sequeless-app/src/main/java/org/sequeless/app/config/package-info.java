/**
 * Bean wiring for sequeless-app.
 *
 * <p>{@link org.sequeless.app.config.CoreConfiguration} builds sequeless-core's use cases —
 * currently just {@link org.sequeless.core.api.WhoAmI} — around whatever adapter beans are
 * currently in the Spring context. Together with {@link org.sequeless.app.rest}, this is one of
 * only two packages in the whole reactor allowed to depend on {@code org.sequeless.core}. See
 * {@link org.sequeless.app.config.CoreConfiguration#whoAmI} for why its {@link
 * org.sequeless.spi.authz.AuthorizationPort} parameter is annotated {@code @Lazy} — that ordering
 * detail is load-bearing for {@link org.sequeless.app.port}'s fail-fast startup checks and must not
 * be removed.
 */
package org.sequeless.app.config;
