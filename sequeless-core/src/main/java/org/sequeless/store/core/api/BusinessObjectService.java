package org.sequeless.store.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.validation.ValidationException;
import org.sequeless.store.spi.ObjectStorePort;

/**
 * The BREAD (browse/read/edit/add/delete) use case over ontology-typed business objects. Every
 * method authorizes its own {@link Operation} against the resolved type's IRI before touching the
 * {@link ObjectStorePort} — the same never-cached, never-short-circuited idiom {@link
 * org.sequeless.store.core.usecase.DefaultWhoAmI} and {@link MetaModelService} establish — and every
 * write funnels its structural checks ({@code ValueCoercer}, {@code StructuralValidator}) through
 * before the shape-based {@code ValidationPort} SHACL check runs.
 *
 * <p>{@code type} parameters accept either a type's short name or its full IRI, resolved against
 * the current {@link org.sequeless.ontology.schema.api.Schema} the same way {@link
 * MetaModelService#describeType} resolves its own {@code nameOrIri} argument.
 *
 * @see org.sequeless.store.core.usecase.DefaultBusinessObjectService
 */
public interface BusinessObjectService {

    /**
     * Lists non-deleted objects of {@code type} and its subtypes, one page at a time, with
     * filtering, free-text search, sorting, and facet counts.
     *
     * <p>Authorizes {@link Operation#BROWSE} against the resolved type's IRI, then resolves {@code
     * type} to its concrete subtype IRIs against the current {@link
     * org.sequeless.ontology.schema.api.Schema} — the set a {@link
     * org.sequeless.spi.query.Query#types()} is built from, since a query port never sees an
     * abstract type IRI — validates {@code query}'s filter, sort, and facet property names (each
     * given as a property short name or full IRI) and operator applicability against that
     * schema, coerces raw filter values to the properties' declared datatypes, and delegates to
     * {@link org.sequeless.spi.query.BrowsePort#browse}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param query the filter, free-text, sort, paging, and facet request to run; must not be
     *     {@code null}
     * @return a non-null result containing the matching page of objects, the total match count, and
     *     the computed facet buckets
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws InvalidQueryException if a filter, sort, or facet property name does not resolve, an
     *     operator is not applicable to its property, or a multi-valued property is used somewhere
     *     only a scalar is supported
     */
    QueryResult browse(Scope scope, String type, BrowseQuery query);

    /**
     * Reads a single object of {@code type} (or a subtype of it) by id, returned as its {@link
     * ObjectView} with the derived properties computed.
     *
     * <p>Authorizes {@link Operation#READ} against the resolved type's IRI. An id that does not
     * exist, is soft-deleted, or belongs to an unrelated type — one that is not {@code type} itself
     * or a subtype of it — is reported identically as {@link ObjectNotFoundException}, so a caller
     * cannot distinguish "no such object" from "object exists but is a different type" by probing.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the object's id; must not be {@code null}
     * @return the matching object's view
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     */
    ObjectView read(Scope scope, String type, ObjectId id);

    /**
     * Returns the type of the live object {@code id} in the tenant of {@code scope}, or empty if there is none. An id
     * that is absent, soft-deleted, or belongs to another tenant gives the same empty result, so the lookup never
     * reveals an object of another tenant.
     *
     * <p>This is a lookup, not a read, and it authorizes nothing: the type is what an authorization decision needs.
     * {@link #read}, {@link #edit} and {@link #delete} each authorize their own operation against the type. The
     * automation bindings use it to resolve the type of an object id (spec 17, section 10), since the released
     * automation ports carry an id and no type.
     *
     * @param scope the tenant the lookup is made in; must not be {@code null}
     * @param id the object's id; must not be {@code null}
     * @return the object's type, or empty if no live object of the tenant has this id
     * @throws NullPointerException if any argument is {@code null}
     */
    Optional<TypeRef> typeOf(Scope scope, ObjectId id);

    /**
     * Creates a new object of {@code type} with {@code properties}.
     *
     * <p>Authorizes {@link Operation#ADD} against the resolved type's IRI, coerces {@code
     * properties} ({@code ValueCoercer}), runs the structural checks ({@code
     * StructuralValidator#validateForCreate}, which also rejects an abstract {@code type}), then
     * the {@code ValidationPort} SHACL check — only reached once the structural checks pass — and
     * commits a {@code Create} mutation plus an {@code ObjectCreated} outbox entry.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param properties JSON-like raw property values, keyed by property short name or full IRI;
     *     must not be {@code null}
     * @return the newly created object, at version 1
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ValidationException if coercion, the structural checks, or the SHACL check finds a
     *     violation
     */
    BusinessObject add(Scope scope, String type, Map<String, Object> properties);

    /**
     * Creates a new object of {@code type} with the caller's id {@code id} and {@code properties}. Runs the
     * same checks and writes the same {@code ObjectCreated} outbox entry as {@link #add(Scope, String, Map)}.
     *
     * <p>The caller chooses the id, so a retry that derives the same id can detect the object it already
     * created (spec 17, section 3: the automation CreateObject command). The id must not already belong to a
     * live object; if it does, the Store's commit rejects the create.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the new object; must not be {@code null}
     * @param properties JSON-like raw property values, keyed by property short name or full IRI; must not be
     *     {@code null}
     * @return the newly created object, at version 1
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ValidationException if coercion, the structural checks, or the SHACL check finds a violation
     */
    BusinessObject add(Scope scope, String type, ObjectId id, Map<String, Object> properties);

    /**
     * Fully replaces the properties of an existing object of {@code type} (or a subtype of it).
     *
     * <p>Authorizes {@link Operation#EDIT} against the resolved type's IRI, reads the existing
     * object first (so {@code createdAt}/{@code createdBy} can be preserved and so a type mismatch
     * can be detected), coerces and structurally validates {@code properties} ({@code
     * StructuralValidator#validateForUpdate}, which — unlike {@link #add} — never rejects an
     * abstract type), runs the {@code ValidationPort} SHACL check, and commits an {@code Update}
     * mutation plus an {@code ObjectUpdated} outbox entry.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the object to edit; must not be {@code null}
     * @param expectedVersion the version the caller expects the stored object to currently be at
     * @param properties JSON-like raw property values that fully replace the object's current
     *     properties, keyed by property short name or full IRI; must not be {@code null}
     * @return the updated object, at version {@code expectedVersion + 1}
     * @throws NullPointerException if any reference-typed argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws ValidationException if coercion, the structural checks, or the SHACL check finds a
     *     violation
     * @throws StaleObjectException if the stored object is not currently at {@code expectedVersion}
     */
    BusinessObject edit(Scope scope, String type, ObjectId id, long expectedVersion, Map<String, Object> properties);

    /**
     * Soft-deletes an existing object of {@code type} (or a subtype of it).
     *
     * <p>Authorizes {@link Operation#DELETE} against the resolved type's IRI, reads the existing
     * object first — even when {@code expectedVersion} is absent, since the currently stored
     * version is needed both to know what "current" means and to pass to the {@code ObjectStorePort}
     * — and commits a {@code Delete} mutation plus an {@code ObjectDeleted} outbox entry.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the object to delete; must not be {@code null}
     * @param expectedVersion the version the caller expects the stored object to currently be at,
     *     or {@link OptionalLong#empty()} to use whatever version is currently stored; must not be
     *     {@code null} (the wrapper itself)
     * @throws NullPointerException if any reference-typed argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws StaleObjectException if {@code expectedVersion} is present and the stored object is
     *     not currently at it
     */
    void delete(Scope scope, String type, ObjectId id, OptionalLong expectedVersion);
}
