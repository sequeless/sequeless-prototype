package org.sequeless.spi.object;

import java.util.Optional;
import org.sequeless.spi.Scope;
import org.sequeless.spi.ontology.OntologyDocument;

/**
 * The companion port {@link ObjectStorePort#ontologyDocuments()} exposes: versioned persistence for
 * the ontology itself, so an imported ontology survives a restart instead of being re-read from a
 * classpath file every time.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy, asserted
 * mechanically by {@code sequeless-spi-testkit}'s {@code ObjectStoreContract} the same way {@link
 * ObjectStorePort}'s own contract is.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code
 *       null}. A conforming implementation throws {@link NullPointerException} when any argument is
 *       {@code null}.
 *   <li><b>Tenant scoping.</b> Versions are per tenant: {@link #active(Scope)} and {@link
 *       #activate(Scope, OntologyDocument)} only ever see or affect documents stored under {@code
 *       scope.tenantId()}.
 *   <li><b>{@link #active(Scope)} before anything has ever been activated.</b> Returns {@link
 *       Optional#empty()} when {@link #activate(Scope, OntologyDocument)} has never been called for
 *       {@code scope.tenantId()}. It never throws to signal "nothing stored yet."
 *   <li><b>{@link #activate(Scope, OntologyDocument)} assigns the next version and deactivates the
 *       previous one, atomically.</b> The first call for a tenant is stored as version 1; every
 *       subsequent call for the same tenant is stored as the previous version's number plus one.
 *       Activating a new version and deactivating whatever was previously active for that tenant
 *       happen in one transaction: a conforming adapter never leaves two documents simultaneously
 *       active for the same tenant, and a caller of {@link #active(Scope)} never observes a moment
 *       where none is active once at least one has ever been activated.
 * </ul>
 */
public interface OntologyDocumentStore {

    /**
     * Returns the currently active ontology document for {@code scope.tenantId()}, if any has ever
     * been activated. See the interface-level javadoc for the full contract this method must
     * satisfy.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return the active document, or {@link Optional#empty()} if none has ever been activated for
     *     this tenant
     * @throws NullPointerException if {@code scope} is {@code null}
     */
    Optional<StoredOntologyDocument> active(Scope scope);

    /**
     * Stores {@code document} as the next version for {@code scope.tenantId()} and makes it the
     * active one, deactivating whatever was previously active. See the interface-level javadoc for
     * the full contract this method must satisfy, including the versioning and atomicity
     * guarantees.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param document the ontology document to store and activate; must not be {@code null}
     * @return the newly stored, newly active version
     * @throws NullPointerException if either argument is {@code null}
     */
    StoredOntologyDocument activate(Scope scope, OntologyDocument document);
}
