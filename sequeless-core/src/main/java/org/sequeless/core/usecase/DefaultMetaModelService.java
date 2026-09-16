package org.sequeless.core.usecase;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Reference implementation of {@link MetaModelService}.
 *
 * <p>Consulting the injected {@link AuthorizationPort} is the entire point of this use case, the
 * same as it is for {@link DefaultWhoAmI}: it is never optional, never cached, and never
 * short-circuited. Every method calls the port exactly once and translates a denial into an
 * {@link AuthorizationException} carrying the port's own decision.
 *
 * <p>{@link #describeType(Scope, String)} resolves {@code nameOrIri} against the {@link
 * OntologyPort#snapshot(Scope) current snapshot} <em>before</em> authorizing, because
 * authorization is performed against the resolved canonical type IRI, which is only known once
 * resolution has succeeded. Resolution tries {@link MetaModelSnapshot#typeByName(String)} first,
 * falling back to {@link MetaModelSnapshot#type(String)}; short names never contain {@code #} or
 * {@code /}, so there is no collision risk between the two lookups.
 */
public final class DefaultMetaModelService implements MetaModelService {

    private final OntologyPort ontologyPort;
    private final AuthorizationPort authorizationPort;

    /**
     * @param ontologyPort the port consulted for the type system itself; must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultMetaModelService(OntologyPort ontologyPort, AuthorizationPort authorizationPort) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
    }

    @Override
    public MetaModelSnapshot snapshot(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope, Operation.BROWSE, AuthorizationPort.EVERYTHING);
        return ontologyPort.snapshot(scope);
    }

    @Override
    public TypeDefinition describeType(Scope scope, String nameOrIri) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(nameOrIri, "nameOrIri must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition type =
            snapshot
                .typeByName(nameOrIri)
                .or(() -> snapshot.type(nameOrIri))
                .orElseThrow(() -> new TypeNotFoundException(nameOrIri));

        authorize(scope, Operation.READ, type.iri());
        return type;
    }

    @Override
    public MetaModelSnapshot reload(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope, Operation.ADMIN, AuthorizationPort.EVERYTHING);
        return ontologyPort.reload(scope);
    }

    private void authorize(Scope scope, Operation operation, String resource) {
        AccessDecision decision = authorizationPort.decide(scope, operation, resource);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
