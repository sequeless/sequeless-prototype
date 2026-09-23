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
 * #KIND_OBJECT_DELETED} for the mutations this SPI defines, {@link #KIND_TRANSITION_FIRED} or
 * {@link #KIND_ACTION_REQUEST} for a fired state-machine transition and the actions it dispatches,
 * or {@link #KIND_TIMER_SCHEDULED}, {@link #KIND_TIMER_CANCELLED}, or {@link
 * #KIND_SIGNAL_RECEIVED} for the automation events introduced alongside {@link
 * org.sequeless.spi.meta.TriggerSpec} — though the type itself does not restrict {@code kind} to
 * those values — a future kind can reuse this record without a new one being minted.
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

    /**
     * The event kind recorded when a transition whose {@link org.sequeless.spi.meta.TriggerSpec}
     * is a {@link org.sequeless.spi.meta.TimerTrigger} is entered — that is, when the object
     * transitions into that timer transition's {@code fromStateIri} — exactly as entering {@code
     * ex:OnHold} schedules the {@code expireHold} timer. Payload carries {@code objectId}, {@code
     * tenantId}, {@code principalId}, {@code typeIri}, {@code state}, {@code transitionName},
     * {@code after} (ISO-8601), and {@code timerKey} (stable, derived as {@code
     * objectId|stateIri|transitionName}), used directly as both the Temporal workflow id and the
     * in-process scheduler key.
     */
    public static final String KIND_TIMER_SCHEDULED = "TimerScheduled";

    /**
     * The event kind recorded when an object leaves the state a previously scheduled {@link
     * #KIND_TIMER_SCHEDULED} entry was waiting out, whether because the timer transition itself
     * fired or because some other transition departed that state first — exactly as leaving {@code
     * ex:OnHold} for any reason cancels the pending {@code expireHold} timer. Payload carries
     * {@code objectId}, {@code tenantId}, and the same {@code timerKey} the corresponding {@link
     * #KIND_TIMER_SCHEDULED} entry carried, which is how the automation adapter finds the timer to
     * cancel.
     */
    public static final String KIND_TIMER_CANCELLED = "TimerCancelled";

    /**
     * The event kind recorded when a client calls {@code POST
     * /objects/{type}/{id}/signals/{name}}, for an automation adapter to dispatch to whichever
     * transition on the object's current state has a matching {@link
     * org.sequeless.spi.meta.ExternalSignalTrigger#signalName()} — exactly as a {@code "reopen"}
     * signal revives a {@code ex:Closed} {@code ex:Project}. Payload carries {@code objectId},
     * {@code tenantId}, {@code principalId}, and {@code signalName}; the request body is recorded
     * for audit but is not bound into the guard's {@code ExpressionContext} this phase.
     */
    public static final String KIND_SIGNAL_RECEIVED = "SignalReceived";

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
