package org.sequeless.app.config;

import org.sequeless.core.api.WhoAmI;
import org.sequeless.core.usecase.DefaultWhoAmI;
import org.sequeless.spi.authz.AuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Wires sequeless-core's use cases into the Spring context.
 *
 * <p>{@code proxyBeanMethods = false} is safe here: {@link #whoAmI(AuthorizationPort)} never calls
 * another {@code @Bean} method on {@code this}, so there is nothing for CGLIB inter-bean-call
 * interception to preserve, and skipping it avoids generating a proxy subclass for no benefit.
 */
@Configuration(proxyBeanMethods = false)
public class CoreConfiguration {

    /**
     * Builds the {@link WhoAmI} use case around whichever {@link AuthorizationPort} adapter is
     * configured.
     *
     * <p><b>Why the parameter is {@code @Lazy}, and why that must not be "simplified" away:</b> if
     * {@code authorizationPort} bound eagerly, Spring would have to resolve an {@link
     * AuthorizationPort} bean while it is still running {@code preInstantiateSingletons()} — the
     * first singleton-creation pass. But {@code PortRegistry} (see {@code org.sequeless.app.port})
     * only validates the configured adapter — including producing its own clean, property-naming
     * {@code PortBindingException} when zero or more than one {@link AuthorizationPort} bean
     * exists — in {@code afterSingletonsInstantiated()}, the <em>second</em> pass, via {@code
     * SmartInitializingSingleton}. With an unset or misconfigured {@code sequeless.authz.adapter}
     * there may be zero {@link AuthorizationPort} beans at all, and an eager dependency here would
     * let Spring's own generic {@code UnsatisfiedDependencyException} fire during pass one, before
     * {@code PortRegistry} ever gets a chance to run and report the actionable error.
     *
     * <p>{@code @Lazy} on an interface-typed parameter makes Spring inject a JDK dynamic proxy
     * immediately, deferring actual candidate resolution to the first real method call on it. That
     * lets this bean — and the {@code WhoAmIController} that depends on it — construct cleanly in
     * pass one regardless of how many (zero, one, or many) {@link AuthorizationPort} beans exist,
     * so {@code PortRegistry}'s pass-two check becomes the first thing that actually counts beans
     * and reports a failure. The proxy is dereferenced only on the happy path, once a request
     * actually calls {@link WhoAmI#whoAmI}, by which point {@code PortRegistry} has already
     * guaranteed exactly one bean exists.
     *
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @return a {@link DefaultWhoAmI} consulting {@code authorizationPort} on every call
     */
    @Bean
    public WhoAmI whoAmI(@Lazy AuthorizationPort authorizationPort) {
        return new DefaultWhoAmI(authorizationPort);
    }
}
