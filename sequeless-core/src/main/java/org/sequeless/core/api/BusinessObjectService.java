package org.sequeless.core.api;

import java.util.Map;
import java.util.OptionalLong;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.StaleObjectException;

/**
 * The BREAD (browse/read/edit/add/delete) use case over ontology-typed business objects. Every
 * method authorizes its own {@link Operation} against the resolved type's IRI before touching the
 * {@link ObjectStorePort} — the same never-cached, never-short-circuited idiom {@link
 * org.sequeless.core.usecase.DefaultWhoAmI} and {@link MetaModelService} establish — and every
 * write funnels its structural checks ({@code ValueCoercer}, {@code StructuralValidator}) through
 * before the shape-based {@code ValidationPort} SHACL check runs.
 *
 * <p>{@code type} parameters accept either a type's short name or its full IRI, resolved against
 * the current {@link org.sequeless.spi.meta.MetaModelSnapshot} the same way {@link
 * MetaModelService#describeType} resolves its own {@code nameOrIri} argument.
 *
 * @see org.sequeless.core.usecase.DefaultBusinessObjectService
 */
public interface BusinessObjectService {

    /**
     * Lists non-deleted objects of {@code type} and its subtypes, one page at a time.
     *
     * <p>Authorizes {@link Operation#BROWSE} against the resolved type's IRI, then delegates to
     * {@link ObjectStorePort#browse} with the resolved type plus every transitive subtype.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param page the page to return; must not be {@code null}
     * @return a non-null page of matching objects
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    PageResult<BusinessObject> browse(Scope scope, String type, Page page);

    /**
     * Reads a single object of {@code type} (or a subtype of it) by id.
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
     * @return the matching object
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     */
    BusinessObject read(Scope scope, String type, ObjectId id);

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
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ValidationException if coercion, the structural checks, or the SHACL check finds a
     *     violation
     */
    BusinessObject add(Scope scope, String type, Map<String, Object> properties);

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
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws ValidationException if coercion, the structural checks, or the SHACL check finds a
     *     violation
     * @throws StaleObjectException if the stored object is not currently at {@code expectedVersion}
     */
    BusinessObject edit(
        Scope scope, String type, ObjectId id, long expectedVersion, Map<String, Object> properties);

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
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws StaleObjectException if {@code expectedVersion} is present and the stored object is
     *     not currently at it
     */
    void delete(Scope scope, String type, ObjectId id, OptionalLong expectedVersion);
}
