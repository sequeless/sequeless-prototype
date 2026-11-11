package org.sequeless.core.usecase;

import java.util.Objects;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.OntologyAdministration;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.api.ImportMode;
import org.sequeless.ontology.api.ImportReport;
import org.sequeless.ontology.api.OntologyDocument;
import org.sequeless.ontology.api.OntologyFormat;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * Reference implementation of {@link OntologyAdministration}.
 *
 * <p>Consulting the injected {@link AuthorizationPort} is the entire point of this use case, the
 * same as it is for {@link DefaultWhoAmI} and {@link DefaultMetaModelService}: it is never
 * optional, never cached, and never short-circuited. Both methods authorize {@link Operation#ADMIN}
 * against {@link AuthorizationPort#EVERYTHING} — export/import act on the ontology as a whole, not
 * on any single type — before delegating to {@link org.sequeless.ontology.api.OntologyAdministration}.
 *
 * <p>Index preparation for the imported type system is not done here. The host wraps the
 * administration port and prepares indexes around it.
 */
public final class DefaultOntologyAdministration implements OntologyAdministration {

    private final org.sequeless.ontology.api.OntologyAdministration ontologyAdministration;
    private final AuthorizationPort authorizationPort;

    /**
     * @param ontologyAdministration the port every call delegates to; must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultOntologyAdministration(
            org.sequeless.ontology.api.OntologyAdministration ontologyAdministration,
            AuthorizationPort authorizationPort) {
        this.ontologyAdministration =
                Objects.requireNonNull(ontologyAdministration, "ontologyAdministration must not be null");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
    }

    @Override
    public OntologyDocument export(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope);
        return ontologyAdministration.export(scope, OntologyFormat.TURTLE);
    }

    @Override
    public ImportReport importTurtle(Scope scope, String content) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(content, "content must not be null");
        authorize(scope);
        OntologyDocument document = new OntologyDocument(content, OntologyFormat.TURTLE);
        return ontologyAdministration.importDocument(scope, document, ImportMode.REPLACE);
    }

    private void authorize(Scope scope) {
        AccessDecision decision = authorizationPort.decide(scope, Operation.ADMIN, AuthorizationPort.EVERYTHING);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
