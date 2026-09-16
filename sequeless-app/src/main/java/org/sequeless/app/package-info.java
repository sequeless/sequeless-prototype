/**
 * Root package of sequeless-app: the Spring Boot application itself.
 *
 * <p>{@link org.sequeless.app.SequelessApplication} lives here, at the component-scan root, so its
 * implicit scan reaches every sibling package this module defines: {@link org.sequeless.app.rest}
 * (the inbound REST adapter), {@link org.sequeless.app.config} (bean wiring for sequeless-core's
 * use cases), and {@link org.sequeless.app.port} (the fail-fast adapter registry). This is the only
 * module in the reactor allowed to depend on Spring MVC and on {@code org.sequeless.core}; an
 * ArchUnit suite in this module's test sources enforces both boundaries structurally.
 */
package org.sequeless.app;
