package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.ontology.OntologyPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link JenaOntologyPort} into the Spring context when {@code sequeless.ontology.adapter} is
 * set to {@code jena}, mirroring {@code sequeless-adapter-authz-permitall}'s {@code
 * PermitAllAuthorizationAutoConfiguration} exactly.
 *
 * <p>Deliberately has no {@code matchIfMissing}: when the property is unset, the condition must
 * evaluate to {@code false} and no {@link OntologyPort} bean must exist here at all, so the
 * application's port registry can detect the absent-property case itself and report it, rather than
 * this auto-configuration silently conjuring a default.
 *
 * <p>Deliberately has no {@code @ConditionalOnMissingBean}: if another adapter's auto-configuration
 * also produces an {@link OntologyPort} bean under the same property value, both beans must be
 * allowed to exist so the application's port registry — not Spring — is the thing that detects and
 * rejects the ambiguity.
 *
 * <p>Takes an {@link ObjectProvider} of {@link ObjectStorePort} — not a plain {@link ObjectStorePort}
 * parameter — because whether an {@link ObjectStorePort} bean exists at all depends on which
 * persistence adapter (if any) is configured: a plain parameter would fail to wire this bean
 * whenever no persistence adapter is active. When a store is available, {@link
 * JenaOntologyPort#fromStore} loads or seeds the active document for {@link TenantId#DEFAULT}
 * instead of always re-reading {@link JenaOntologyProperties#getSource()}.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.ontology.adapter", havingValue = "jena")
@EnableConfigurationProperties(JenaOntologyProperties.class)
public class JenaOntologyAutoConfiguration {

    /**
     * @param properties the bound {@code sequeless.ontology.*} configuration
     * @param objectStoreProvider resolves to the configured {@link ObjectStorePort}, if any adapter
     *     provides one; absent when no persistence adapter is active
     * @return a new {@link JenaOntologyPort}: backed by {@code objectStoreProvider}'s store when one
     *     is available (seeding it from {@link JenaOntologyProperties#getSource()} only if it has no
     *     active document yet), otherwise loaded directly from {@link
     *     JenaOntologyProperties#getSource()} exactly as before
     */
    @Bean
    public OntologyPort ontologyPort(
            JenaOntologyProperties properties, ObjectProvider<ObjectStorePort> objectStoreProvider) {
        ObjectStorePort store = objectStoreProvider.getIfAvailable();
        if (store == null) {
            return JenaOntologyPort.fromSource(
                "sequeless.ontology.source", properties.getSource(), properties.getReasoner());
        }
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
        return JenaOntologyPort.fromStore(
            store.ontologyDocuments(), scope, "sequeless.ontology.source", properties.getSource(),
            properties.getReasoner());
    }
}
