package org.sequeless.core.api;

import org.sequeless.core.AuthorizationException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Exposes the ontology's type system to the rest of the application: the current snapshot, a
 * single type resolved by short name or full IRI, and the ability to force a reload from the
 * backing source. Every implementation must consult the injected {@link AuthorizationPort} for
 * each call, never answer from cached or hardcoded state — the same idiom {@link
 * org.sequeless.core.usecase.DefaultWhoAmI} establishes for {@link WhoAmI}.
 *
 * @see org.sequeless.core.usecase.DefaultMetaModelService
 */
public interface MetaModelService {

    /**
     * Returns the current type system snapshot for {@code scope}, without re-reading the backing
     * source.
     *
     * <p>Authorizes {@link org.sequeless.spi.authz.Operation#BROWSE} against {@link
     * AuthorizationPort#EVERYTHING} before delegating to {@link OntologyPort#snapshot(Scope)}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the current, non-null snapshot
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    MetaModelSnapshot snapshot(Scope scope);

    /**
     * Resolves {@code nameOrIri} — a type's short name or its full IRI — against the current
     * snapshot and returns its {@link TypeDefinition}.
     *
     * <p>Resolution happens <em>before</em> authorization: the operation is authorized against
     * the resolved canonical type IRI, which is only known once the lookup has succeeded. One
     * accepted consequence is that an unauthorized caller can distinguish "no such type" (a
     * {@link TypeNotFoundException}) from "forbidden" (an {@link AuthorizationException}) — this
     * is acceptable because the type vocabulary itself is public by design; per-type visibility
     * arrives with {@code sq:permission} in a later phase. Authorizes {@link
     * org.sequeless.spi.authz.Operation#READ} against the resolved type's IRI.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param nameOrIri the type's short name or full IRI; must not be {@code null}
     * @return the resolved type's definition
     * @throws NullPointerException if either argument is {@code null}
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code nameOrIri}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    TypeDefinition describeType(Scope scope, String nameOrIri);

    /**
     * Re-reads and re-validates the ontology from its backing source and returns the resulting
     * snapshot.
     *
     * <p>Authorizes {@link org.sequeless.spi.authz.Operation#ADMIN} against {@link
     * AuthorizationPort#EVERYTHING} before delegating to {@link OntologyPort#reload(Scope)}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the snapshot built fresh from the current backing source
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws org.sequeless.spi.ontology.OntologyException if the freshly read ontology is
     *     inconsistent
     */
    MetaModelSnapshot reload(Scope scope);
}
