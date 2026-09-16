package org.sequeless.adapter.authz.permitall;

import org.sequeless.spi.authz.AuthorizationPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link PermitAllAuthorizationPort} into the Spring context when {@code
 * sequeless.authz.adapter} is set to {@code permit-all}. The only class in this package allowed
 * to import Spring; {@link PermitAllAuthorizationPort} and {@link
 * PermitAllAdapterDescriptorProvider} must remain plain, Spring-free classes so they can be used
 * outside a Spring context too.
 *
 * <p>Deliberately has no {@code matchIfMissing}: when the property is unset, the condition must
 * evaluate to {@code false} and no {@link AuthorizationPort} bean must exist here at all, so the
 * application's port registry can detect the absent-property case itself and report it, rather
 * than this auto-configuration silently conjuring a default.
 *
 * <p>Deliberately has no {@code @ConditionalOnMissingBean}: if another adapter's
 * auto-configuration also produces an {@link AuthorizationPort} bean under the same property
 * value, both beans must be allowed to exist so the application's port registry — not Spring — is
 * the thing that detects and rejects the ambiguity.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.authz.adapter", havingValue = "permit-all")
public class PermitAllAuthorizationAutoConfiguration {

    /**
     * @return a new {@link PermitAllAuthorizationPort}
     */
    @Bean
    public AuthorizationPort authorizationPort() {
        return new PermitAllAuthorizationPort();
    }
}
