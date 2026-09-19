package org.sequeless.spi.object;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A domain event recorded alongside a {@link Mutation} in the same atomic {@link ObjectStorePort#commit}
 * that produced it, following the transactional-outbox pattern: a downstream consumer polls or is
 * notified of these rows independently of the object store's own read path.
 *
 * <p>{@code kind} is one of {@link #KIND_OBJECT_CREATED}, {@link #KIND_OBJECT_UPDATED}, or {@link
 * #KIND_OBJECT_DELETED} for the mutations this SPI defines, though the type itself does not
 * restrict {@code kind} to those three values — a future mutation kind can reuse this record
 * without a new one being minted.
 *
 * <p>{@code payload} must be JSON-compatible end to end: every value, at any depth, is a {@code
 * String}, a {@code Number}, a {@code Boolean}, a {@code List<?>} of such values, or a {@code
 * Map<?, ?>} of such values, and {@code null} never appears anywhere in it (as a top-level value,
 * a list element, or a nested map value). This is enforced eagerly, in the constructor, precisely
 * so that a later JSON-serializing adapter fails fast here with a clear message rather than with a
 * {@code ClassCastException} deep inside a codec. {@code payload} must additionally contain an
 * {@code "objectId"} entry whose value is a {@code String} holding the affected object's UUID —
 * this is how {@code sq_outbox.object_id} is populated by a JDBC adapter.
 *
 * @param id this outbox row's own identity; must not be {@code null}
 * @param kind the kind of event this entry records; must not be blank
 * @param payload the event's JSON-compatible payload, keyed by field name; must not be {@code
 *     null}; must contain a {@code String}-valued {@code "objectId"} entry; returned as an
 *     unmodifiable copy so callers cannot mutate an entry after construction
 * @param occurredAt when the event occurred; must not be {@code null}
 */
public record OutboxEntry(UUID id, String kind, Map<String, Object> payload, Instant occurredAt) {

    /** The event kind recorded when a {@link Create} mutation succeeds. */
    public static final String KIND_OBJECT_CREATED = "ObjectCreated";

    /** The event kind recorded when an {@link Update} mutation succeeds. */
    public static final String KIND_OBJECT_UPDATED = "ObjectUpdated";

    /** The event kind recorded when a {@link Delete} mutation succeeds. */
    public static final String KIND_OBJECT_DELETED = "ObjectDeleted";

    /**
     * The event kind recorded, as an audit trail, alongside every {@link #KIND_ACTION_REQUEST} row
     * a state-machine transition produces. A relay or consumer that dispatches actions filters
     * strictly on {@link #KIND_ACTION_REQUEST} and ignores rows of this kind.
     */
    public static final String KIND_TRANSITION_FIRED = "TransitionFired";

    /**
     * The event kind recorded once per {@code sq:action} a fired state-machine transition defines,
     * carrying a self-contained payload (frozen at transition-fire time) that a durable automation
     * adapter dispatches. Deliberately declared here, beside {@link #KIND_OBJECT_CREATED} and its
     * siblings, rather than on the core use case that produces it: an adapter that consumes these
     * rows (a relay, an automation port) must be able to filter on this constant without depending
     * on {@code sequeless-core}, which the {@code noAdapterDependsOnCore} architecture rule
     * forbids.
     */
    public static final String KIND_ACTION_REQUEST = "ActionRequest";

    private static final String OBJECT_ID_KEY = "objectId";

    public OutboxEntry {
        Objects.requireNonNull(id, "id must not be null");
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("OutboxEntry kind must not be blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        payload.forEach((key, value) -> requireJsonCompatible(key, value));
        if (!(payload.get(OBJECT_ID_KEY) instanceof String)) {
            throw new IllegalArgumentException(
                "OutboxEntry payload must contain a String '" + OBJECT_ID_KEY + "' entry");
        }
        payload = Map.copyOf(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Recursively checks that {@code value} (found at {@code path} within the payload) is
     * JSON-compatible, throwing an {@link IllegalArgumentException} that names the offending
     * path and type otherwise, rather than letting an incompatible value surface later as a bare
     * {@link ClassCastException} from a JSON codec.
     *
     * @param path the payload path {@code value} was found at, for the exception message
     * @param value the value to check; rejected if {@code null}
     */
    private static void requireJsonCompatible(String path, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("OutboxEntry payload value at '" + path + "' must not be null");
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                requireJsonCompatible(path + "[" + i + "]", list.get(i));
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                requireJsonCompatible(path + "." + entry.getKey(), entry.getValue());
            }
            return;
        }
        throw new IllegalArgumentException(
            "OutboxEntry payload value at '" + path + "' is not JSON-compatible: " + value.getClass());
    }
}
