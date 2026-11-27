package org.sequeless.app.query;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.api.BrowseQuery;
import org.sequeless.store.core.api.InvalidQueryException;
import org.sequeless.store.core.api.TypeNotFoundException;

/**
 * The query side of the BREAD use cases over ontology-typed business objects: browse and read. These moved out of
 * {@code sequeless-core} into the host in spec 18 U6 (D1). The command side, {@code add}, {@code edit} and
 * {@code delete} with the {@code typeOf} lookup, stays in {@link org.sequeless.store.core.api.BusinessObjectService}.
 *
 * <p>{@code type} parameters accept either a type's short name or its full IRI, resolved against the current schema
 * the same way {@link org.sequeless.app.admin.MetaModelService} resolves its own {@code nameOrIri} argument.
 *
 * @see DefaultObjectQueryService
 */
public interface ObjectQueryService {

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
}
