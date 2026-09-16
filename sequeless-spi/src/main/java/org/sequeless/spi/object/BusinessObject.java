package org.sequeless.spi.object;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.TenantId;

/**
 * An instance of an ontology type, as stored by an {@link ObjectStorePort}: its identity, type,
 * tenant, optimistic-locking version, optional state, property values, audit trail, and whether it
 * has been soft-deleted.
 *
 * @param id the object's identity; must not be {@code null}
 * @param type the IRI of the object's ontology type; must not be {@code null}
 * @param tenant the tenant this object belongs to; must not be {@code null}
 * @param version the optimistic-locking version; a freshly created object always has version 1, so
 *     any value less than 1 is rejected
 * @param state the object's state-machine state, if the type has one; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param properties the object's property values, keyed by property IRI; must not be {@code null};
 *     returned as an unmodifiable copy so callers cannot mutate an object after construction
 * @param audit who created and last modified this object, and when; must not be {@code null}
 * @param deleted whether this object has been soft-deleted
 */
public record BusinessObject(
    ObjectId id,
    TypeRef type,
    TenantId tenant,
    long version,
    Optional<String> state,
    Map<PropertyRef, Value> properties,
    Audit audit,
    boolean deleted) {

    public BusinessObject {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tenant, "tenant must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("BusinessObject version must be at least 1");
        }
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
        Objects.requireNonNull(audit, "audit must not be null");
    }
}
