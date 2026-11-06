/**
 * Outbound port interfaces and immutable value types for Sequeless.
 *
 * <p>This package, and every package beneath {@code org.sequeless.spi}, imports nothing outside the
 * JDK ({@code java.*}) and the released sequeless-kernel ({@code org.sequeless.kernel.*}). That
 * constraint is enforced twice: once mechanically at build time by a {@code
 * maven-enforcer-plugin} {@code bannedDependencies} rule, and once structurally by an ArchUnit rule
 * in {@code sequeless-app} that asserts no class under {@code org.sequeless.spi..} depends on
 * anything outside {@code java..}, {@code org.sequeless.kernel..} and {@code org.sequeless.spi..}
 * itself. Adapters and the application depend on this module; this module depends on the kernel
 * and on nothing else of Sequeless's own.
 *
 * <p>{@link org.sequeless.kernel.identity.TenantId}, {@link org.sequeless.kernel.identity.Principal}, and {@link
 * org.sequeless.kernel.identity.Scope} carry request context across every port boundary. {@link
 * org.sequeless.spi.SpiVersion}, {@link org.sequeless.spi.AdapterDescriptor}, and {@link
 * org.sequeless.spi.AdapterDescriptorProvider} support runtime adapter discovery and
 * compatibility checking. Per-port contracts (the first being authorization) live in subpackages
 * such as {@link org.sequeless.spi.authz}, {@link org.sequeless.spi.meta}, {@link
 * org.sequeless.spi.object}, {@link org.sequeless.spi.ontology}, {@link org.sequeless.spi.query},
 * {@link org.sequeless.spi.validation}, {@link org.sequeless.spi.derivation} — the {@code
 * sq:Plugin} vocabulary for derived properties computed by named, {@code ServiceLoader}-discovered
 * code rather than declaratively — and {@code org.sequeless.expression.api} — the {@code
 * ExpressionPort} that evaluates JEXL-family expressions authored in the ontology, such as {@code
 * sq:guard}.
 */
package org.sequeless.spi;
