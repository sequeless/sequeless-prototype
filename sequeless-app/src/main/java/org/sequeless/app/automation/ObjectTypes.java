package org.sequeless.app.automation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.TypeRef;

/**
 * Resolves an object id to its type IRI for the automation bindings.
 *
 * <p>The released {@code ObjectReadPort} and {@code StoreCommandPort} carry an object id and no type, while the
 * Command use cases {@code BusinessObjectService.read} and {@code edit} take the type. This class is the one place
 * the automation bindings touch {@link ObjectStorePort}, and only through {@link ObjectStorePort#typesOf}, a read.
 * Every read, write and transition still goes through a Command use case, which runs its own authorization and
 * validation. Spec 17 section 10, Q3, names this as a point to confirm at the P3 review.
 */
final class ObjectTypes {

    private final ObjectStorePort objectStorePort;

    /**
     * @param objectStorePort the store the type lookup reads; must not be {@code null}
     */
    ObjectTypes(ObjectStorePort objectStorePort) {
        this.objectStorePort = Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
    }

    /**
     * @param scope the tenant and principal the lookup is made under; must not be {@code null}
     * @param id the object to look up; must not be {@code null}
     * @return the type IRI of the live object {@code id}, or empty if it is absent, soft-deleted or in another tenant
     */
    Optional<String> typeIriOf(Scope scope, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        TypeRef type = objectStorePort.typesOf(scope, Set.of(id)).get(id);
        return Optional.ofNullable(type).map(TypeRef::iri);
    }
}
