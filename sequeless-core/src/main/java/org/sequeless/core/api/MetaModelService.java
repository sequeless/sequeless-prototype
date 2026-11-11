package org.sequeless.core.api;

import org.sequeless.core.AuthorizationException;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.api.OntologyReport;

/**
 * Exposes the ontology's type system to the rest of the application: the current schema and
 * behaviour, a single type resolved by short name or full IRI, and the ability to force a reload
 * from the backing source. Every implementation must consult the injected {@link
 * AuthorizationPort} for each call, never answer from cached or hardcoded state — the same idiom
 * {@link org.sequeless.core.usecase.DefaultWhoAmI} establishes for {@link WhoAmI}.
 *
 * @see org.sequeless.core.usecase.DefaultMetaModelService
 */
public interface MetaModelService {

    /**
     * Returns the current schema for {@code scope}, without re-reading the backing source.
     *
     * <p>Authorizes {@link org.sequeless.spi.authz.Operation#BROWSE} against {@link
     * AuthorizationPort#EVERYTHING} before delegating to {@link
     * org.sequeless.ontology.schema.spi.SchemaSource#schema}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the current, non-null schema
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    Schema schema(Scope scope);

    /**
     * Returns the current behaviour for {@code scope}, without re-reading the backing source.
     *
     * <p>Authorizes {@link org.sequeless.spi.authz.Operation#BROWSE} against {@link
     * AuthorizationPort#EVERYTHING} before delegating to {@link
     * org.sequeless.ontology.behaviour.spi.BehaviourSource#behaviour}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the current, non-null behaviour
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    Behaviour behaviour(Scope scope);

    /**
     * Resolves {@code nameOrIri} — a type's short name or its full IRI — against the current schema
     * and returns the type with its state machine and derived properties.
     *
     * <p>Resolution happens <em>before</em> authorization: the operation is authorized against the
     * resolved canonical type IRI, which is only known once the lookup has succeeded. One accepted
     * consequence is that an unauthorized caller can distinguish "no such type" (a {@link
     * TypeNotFoundException}) from "forbidden" (an {@link AuthorizationException}) — this is
     * acceptable because the type vocabulary itself is public by design; per-type visibility arrives
     * with {@code sq:permission} in a later phase. Authorizes {@link
     * org.sequeless.spi.authz.Operation#READ} against the resolved type's IRI.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param nameOrIri the type's short name or full IRI; must not be {@code null}
     * @return the resolved type's description
     * @throws NullPointerException if either argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code nameOrIri}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    TypeDescription describeType(Scope scope, String nameOrIri);

    /**
     * Re-reads and re-validates the ontology from its backing source and returns the resulting
     * report.
     *
     * <p>Authorizes {@link org.sequeless.spi.authz.Operation#ADMIN} against {@link
     * AuthorizationPort#EVERYTHING} before delegating to {@link
     * org.sequeless.ontology.api.OntologyAdministration#reload}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the report on the ontology built fresh from the current backing source
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws org.sequeless.ontology.api.OntologyException if the freshly read ontology is
     *     inconsistent
     */
    OntologyReport reload(Scope scope);
}
