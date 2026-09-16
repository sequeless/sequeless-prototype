package org.sequeless.spi.object;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sequeless.spi.Scope;

/**
 * The outbound port every instance of an ontology type is stored, read, and mutated through.
 * Exactly one implementation is wired into the running application at a time, selected by
 * configuration property (see the app's {@code PortRegistry}); use cases never choose between
 * implementations themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code ObjectStoreContract} asserts every clause of it
 * mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code
 *       null}. A conforming implementation throws {@link NullPointerException} when any argument,
 *       or any element of a {@link Set} argument, is {@code null}. It must never substitute a
 *       default or silently skip a null element: missing input is a programming error in the
 *       caller, not a fact about what is stored.
 *   <li><b>Tenant scoping.</b> Every method filters by {@code scope.tenantId()}; nothing else on
 *       this port is tenant-aware. An object created under one tenant is invisible to every method
 *       called with a {@link Scope} whose {@code tenantId} differs, as if it did not exist.
 *   <li><b>{@link #find(Scope, ObjectId)} never throws for "not found."</b> It returns {@link
 *       Optional#empty()} for an id that does not exist, that belongs to an object that has been
 *       soft-deleted, or that belongs to a different tenant than {@code scope.tenantId()}. These
 *       three cases are indistinguishable to the caller by design.
 *   <li><b>{@link #browse(Scope, Set, Page)} is non-deleted, tenant-filtered, and ordered.</b>
 *       Results are ordered by {@code (createdAt, id)} ascending, contain only objects that are not
 *       soft-deleted, and are restricted to {@code scope.tenantId()}. {@code types} may contain more
 *       than one {@link TypeRef}: matching objects are the union across every type in the set, not
 *       their intersection. A {@link Page} whose {@link Page#number()} is past the last page
 *       returns a {@link PageResult} with an empty {@link PageResult#items()} but the correct
 *       {@link PageResult#totalItems()} and {@link PageResult#totalPages()} — it never throws for
 *       overrun.
 *   <li><b>{@link #typesOf(Scope, Set)} is a best-effort lookup, not a validating one.</b> It
 *       returns an entry only for ids that exist, are not soft-deleted, and belong to {@code
 *       scope.tenantId()}; an id that is unknown, deleted, or belongs to another tenant is silently
 *       absent from the result map rather than causing a throw or a missing-key placeholder. Calling
 *       it with an empty {@code ids} set returns an empty map. This method exists for
 *       reference-integrity checks — "if this id exists, what type is it" — not for a lookup that
 *       must succeed.
 *   <li><b>{@link #commit(Scope, ChangeSet)} is atomic.</b> Either every {@link Mutation} and every
 *       {@link OutboxEntry} in the {@link ChangeSet} is persisted, or none is. If any {@link Update}
 *       or {@link Delete} in the changeset has an {@code expectedVersion} that does not match the
 *       object's currently stored version, the whole call throws {@link StaleObjectException} and
 *       nothing in the changeset is persisted — including any {@link Create} that appeared earlier
 *       in {@link ChangeSet#mutations()}. If any {@link Update} or {@link Delete} targets an {@link
 *       ObjectId} that does not exist or is already soft-deleted, the whole call throws {@link
 *       ObjectNotFoundException} with the same all-or-nothing rollback guarantee. On success, {@link
 *       CommitResult#objects()} reflects each affected object at its post-commit version (1 for
 *       every {@link Create}, {@code expectedVersion + 1} for every {@link Update} or {@link
 *       Delete}), and {@link CommitResult#outboxIds()} is exactly the ids of the persisted outbox
 *       rows, in the same order as {@link ChangeSet#outbox()}.
 * </ul>
 */
public interface ObjectStorePort {

    /**
     * Looks up a single object by id. See the interface-level javadoc for the full contract this
     * method must satisfy, including the three cases that all return an empty result.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param id the id of the object to look up; must not be {@code null}
     * @return the object, or {@link Optional#empty()} if it does not exist, has been soft-deleted,
     *     or belongs to a different tenant than {@code scope.tenantId()}
     * @throws NullPointerException if either argument is {@code null}
     */
    Optional<BusinessObject> find(Scope scope, ObjectId id);

    /**
     * Lists non-deleted objects of the given types, one page at a time. See the interface-level
     * javadoc for the full contract this method must satisfy, including ordering, tenant scoping,
     * and page-overrun behaviour.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param types the types to include, matched as a union; must not be {@code null}, must not
     *     contain {@code null}
     * @param page the page to return; must not be {@code null}
     * @return a non-null page of matching objects, ordered by {@code (createdAt, id)} ascending
     * @throws NullPointerException if any argument, or any element of {@code types}, is {@code
     *     null}
     */
    PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page);

    /**
     * Looks up the type of every id in {@code ids} that currently exists, is not soft-deleted, and
     * belongs to {@code scope.tenantId()}. See the interface-level javadoc for the full contract
     * this method must satisfy, including why unknown ids are absent rather than causing a throw.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param ids the ids to look up; must not be {@code null}, must not contain {@code null}; may
     *     be empty
     * @return a non-null map from each resolvable id to its type; ids that do not resolve are
     *     absent from the map, never mapped to {@code null}
     * @throws NullPointerException if any argument, or any element of {@code ids}, is {@code null}
     */
    Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids);

    /**
     * Applies every {@link Mutation} and persists every {@link OutboxEntry} in {@code changeSet}
     * atomically. See the interface-level javadoc for the full contract this method must satisfy,
     * including the all-or-nothing rollback guarantee and what {@link CommitResult} contains on
     * success.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param changeSet the mutations and outbox entries to apply; must not be {@code null}
     * @return a non-null result describing the post-commit state of every affected object and the
     *     ids of every persisted outbox row
     * @throws NullPointerException if either argument is {@code null}
     * @throws StaleObjectException if any {@link Update} or {@link Delete} in {@code changeSet}
     *     has an {@code expectedVersion} that does not match the object's currently stored
     *     version; nothing in {@code changeSet} is persisted
     * @throws ObjectNotFoundException if any {@link Update} or {@link Delete} in {@code changeSet}
     *     targets an id that does not exist or is already soft-deleted; nothing in {@code
     *     changeSet} is persisted
     */
    CommitResult commit(Scope scope, ChangeSet changeSet);

    /**
     * Returns the companion port through which versioned ontology documents are persisted and
     * activated. An implementation typically shares its underlying storage between object records
     * and ontology documents, which is why the two are reached from the same port rather than wired
     * as two independent beans.
     *
     * @return a non-null {@link OntologyDocumentStore}
     */
    OntologyDocumentStore ontologyDocuments();
}
