/**
 * Root package of sequeless-core: use cases (inbound API in {@link org.sequeless.core.api} and
 * their implementations in {@link org.sequeless.core.usecase}), and the core-internal exception
 * types they throw.
 *
 * <p>This module — every package beneath {@code org.sequeless.core} — depends only on the JDK
 * ({@code java.*}) and {@code org.sequeless.spi..}; never on a framework such as Spring, and never
 * on Lombok. A {@code maven-enforcer-plugin} {@code bannedDependencies} rule in this module's POM
 * enforces that mechanically, and an ArchUnit rule in {@code sequeless-app} enforces it
 * structurally. {@link org.sequeless.core.AuthorizationException} is the unchecked exception a use
 * case throws when an {@link org.sequeless.spi.authz.AuthorizationPort} denies an operation.
 */
package org.sequeless.core;
