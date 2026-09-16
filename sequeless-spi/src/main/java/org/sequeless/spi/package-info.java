/**
 * Outbound port interfaces and immutable value types for Sequeless.
 *
 * <p>This package, and every package beneath {@code org.sequeless.spi}, imports nothing outside the
 * JDK ({@code java.*}). That constraint is enforced twice: once mechanically at build time by a
 * {@code maven-enforcer-plugin} {@code bannedDependencies} rule in this module's POM, and once
 * structurally by an ArchUnit rule in {@code sequeless-app} that asserts no class under {@code
 * org.sequeless.spi..} depends on anything outside {@code java..} and {@code org.sequeless.spi..}
 * itself. Adapters and the application depend on this module; this module depends on nothing of
 * Sequeless's own.
 *
 * <p>{@link org.sequeless.spi.TenantId}, {@link org.sequeless.spi.Principal}, and {@link
 * org.sequeless.spi.Scope} carry request context across every port boundary. {@link
 * org.sequeless.spi.SpiVersion}, {@link org.sequeless.spi.AdapterDescriptor}, and {@link
 * org.sequeless.spi.AdapterDescriptorProvider} support runtime adapter discovery and
 * compatibility checking. Per-port contracts (the first being authorization) live in subpackages
 * such as {@link org.sequeless.spi.authz}.
 */
package org.sequeless.spi;
