/**
 * The permit-all {@code AuthorizationPort} adapter (DR-11): Sequeless's default authorization
 * policy, which allows every request. {@link
 * org.sequeless.adapter.authz.permitall.PermitAllAuthorizationPort} and {@link
 * org.sequeless.adapter.authz.permitall.PermitAllAdapterDescriptorProvider} have zero Spring
 * dependencies, so they can be discovered and used via plain {@link java.util.ServiceLoader}
 * outside any Spring context; {@link
 * org.sequeless.adapter.authz.permitall.PermitAllAuthorizationAutoConfiguration} is the sole
 * exception, wiring the port into a Spring Boot application when {@code
 * sequeless.authz.adapter=permit-all}.
 */
package org.sequeless.adapter.authz.permitall;
