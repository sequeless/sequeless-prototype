package org.sequeless.spi.query;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;

/**
 * The Query side's view of one stored object, as {@link ReadPort#read} and {@link BrowsePort#browse}
 * present it. It carries the same eight components as {@link org.sequeless.spi.object.BusinessObject},
 * so a stored object can be rebuilt from its view without loss.
 *
 * @param id the object's identity; must not be {@code null}
 * @param type the object's ontology type; must not be {@code null}
 * @param tenant the tenant this object belongs to; must not be {@code null}
 * @param version the optimistic-locking version; must be at least 1
 * @param state the object's state-machine state, if the type has one; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param properties the object's property values, keyed by property IRI; must not be {@code null};
 *     returned as an unmodifiable copy so callers cannot mutate a view after construction
 * @param audit who created and last modified this object, and when; must not be {@code null}
 * @param deleted whether this object has been soft-deleted
 */
public record ObjectView(
        ObjectId id,
        TypeRef type,
        TenantId tenant,
        long version,
        Optional<String> state,
        Map<PropertyRef, Value> properties,
        Audit audit,
        boolean deleted) {

    public ObjectView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tenant, "tenant must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("ObjectView version must be at least 1");
        }
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
        Objects.requireNonNull(audit, "audit must not be null");
    }
}
