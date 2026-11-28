/**
 * Adapter registration for Sequeless: {@link org.sequeless.spi.SpiVersion}, {@link org.sequeless.spi.AdapterDescriptor} and
 * {@link org.sequeless.spi.AdapterDescriptorProvider}, which support runtime adapter discovery and compatibility checking.
 *
 * <p>This module imports nothing outside the JDK. A maven-enforcer {@code bannedDependencies} rule enforces that at build time,
 * and an ArchUnit rule in {@code sequeless-app} enforces it structurally. The Command ports and their types ({@code
 * org.sequeless.store.spi}, {@code org.sequeless.store.api}, {@code org.sequeless.spi.object}) come from the released
 * sequeless-store (spec 18, U11). The Query ports ({@code org.sequeless.spi.query}) live in {@code sequeless-app}.
 */
package org.sequeless.spi;
