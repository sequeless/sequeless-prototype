package org.sequeless.core.usecase;

import java.util.Objects;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.OntologyAdministration;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.query.QueryPort;

/**
 * Reference implementation of {@link OntologyAdministration}.
 *
 * <p>Consulting the injected {@link AuthorizationPort} is the entire point of this use case, the
 * same as it is for {@link DefaultWhoAmI} and {@link DefaultMetaModelService}: it is never
 * optional, never cached, and never short-circuited. Both methods authorize {@link Operation#ADMIN}
 * against {@link AuthorizationPort#EVERYTHING} — export/import act on the ontology as a whole, not
 * on any single type — before delegating to {@link OntologyPort}.
 *
 * <p>{@link #importTurtle} additionally calls {@link QueryPort#ensureIndexes} right after a
 * successful {@link OntologyPort#importDocument} call, using the freshly-reloaded snapshot — {@link
 * OntologyPort#importDocument}'s own contract is to either return an accepted report or throw, so
 * reaching that call always means the import succeeded.
 */
public final class DefaultOntologyAdministration implements OntologyAdministration {

    private final OntologyPort ontologyPort;
    private final AuthorizationPort authorizationPort;
    private final QueryPort queryPort;

    /**
     * @param ontologyPort the port every call delegates to; must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param queryPort the port {@link #importTurtle} primes with fresh indexes after a successful
     *     import; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultOntologyAdministration(
        OntologyPort ontologyPort, AuthorizationPort authorizationPort, QueryPort queryPort) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
    }

    @Override
    public OntologyDocument export(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope);
        return ontologyPort.export(scope, OntologyFormat.TURTLE);
    }

    @Override
    public ImportReport importTurtle(Scope scope, String content) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(content, "content must not be null");
        authorize(scope);
        OntologyDocument document = new OntologyDocument(content, OntologyFormat.TURTLE);
        ImportReport report = ontologyPort.importDocument(scope, document, ImportMode.REPLACE);

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        queryPort.ensureIndexes(scope, snapshot);

        return report;
    }

    private void authorize(Scope scope) {
        AccessDecision decision =
            authorizationPort.decide(scope, Operation.ADMIN, AuthorizationPort.EVERYTHING);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
