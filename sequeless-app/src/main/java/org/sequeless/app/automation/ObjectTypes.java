package org.sequeless.app.automation;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.object.TypeRef;

/**
 * Resolves an object id to its type IRI for the automation bindings.
 *
 * <p>The released {@code ObjectReadPort} and {@code StoreCommandPort} carry an object id and no type, while the Command
 * use cases {@code BusinessObjectService.read} and {@code edit} take the type. The lookup goes through
 * {@link BusinessObjectService#typeOf}, so the bindings never import {@code ObjectStorePort} (spec 17 section 10,
 * decision 2). Every read, write and transition still goes through a Command use case, which runs its own
 * authorization and validation.
 */
final class ObjectTypes {

    private final BusinessObjectService objects;

    /**
     * @param objects the Command use case that resolves the type; must not be {@code null}
     */
    ObjectTypes(BusinessObjectService objects) {
        this.objects = Objects.requireNonNull(objects, "objects must not be null");
    }

    /**
     * @param scope the tenant and principal the lookup is made under; must not be {@code null}
     * @param id the object to look up; must not be {@code null}
     * @return the type IRI of the live object {@code id}, or empty if it is absent, soft-deleted or in another tenant
     */
    Optional<String> typeIriOf(Scope scope, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        return objects.typeOf(scope, id).map(TypeRef::iri);
    }
}
