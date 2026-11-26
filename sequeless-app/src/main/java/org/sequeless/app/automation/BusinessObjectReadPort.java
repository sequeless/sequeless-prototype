package org.sequeless.app.automation;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.api.BusinessObjectService;

/**
 * The host's {@link ObjectReadPort}: reads an object through {@link BusinessObjectService#read}, so the READ
 * authorization of the Command side applies to automation reads.
 *
 * <p>An absent or soft-deleted object is an empty result, a no-op for the caller. A denied read throws
 * {@link ObjectReadDeniedException}, never an empty result (spec 15, decision O-6). Its message does not name the
 * object, so it does not reveal whether the object exists.
 */
final class BusinessObjectReadPort implements ObjectReadPort {

    private final ObjectTypes types;
    private final BusinessObjectService objects;

    /**
     * @param types the id-to-type lookup; must not be {@code null}
     * @param objects the Command use case the read goes through; must not be {@code null}
     */
    BusinessObjectReadPort(ObjectTypes types, BusinessObjectService objects) {
        this.types = Objects.requireNonNull(types, "types must not be null");
        this.objects = Objects.requireNonNull(objects, "objects must not be null");
    }

    @Override
    public Optional<ObjectSnapshot> find(Scope scope, ObjectId objectId) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Optional<String> typeIri = types.typeIriOf(scope, objectId);
        if (typeIri.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ObjectSnapshots.from(objects.read(scope, typeIri.get(), objectId)));
        } catch (ObjectNotFoundException absent) {
            return Optional.empty();
        } catch (AuthorizationException denied) {
            throw new ObjectReadDeniedException("the automation principal may not read this object", denied);
        }
    }
}
