package org.sequeless.spi.automation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The {@code TimerScheduled}-shaped outbox entry that describes one timer a {@code TransitionFired}
 * row starts, built from a {@link ResolvedTimer} by {@link #of}. An automation adapter passes this
 * entry to {@link TriggerEvaluator#onTimerElapsed} once the timer elapses, so the elapsed-timer call
 * and its staleness check read the same payload the timer was scheduled with.
 *
 * <p>The kind string is {@link #KIND}, the kind that {@code TimerScheduled} rows written before the timer
 * kinds were removed carry.
 * Payload carries {@code objectId}, {@code tenantId}, {@code principalId}, {@code typeIri}, {@code
 * state}, {@code transitionName}, {@code after} (ISO-8601), {@code timerKey} (stable, derived as
 * {@code objectId|stateIri|transitionName}, see {@link OutboxEntry#timerKey}), and {@code
 * ontologyVersion}, the same keys a {@code TimerScheduled} row carries.
 */
public final class TimerScheduledEntry {

    /** The kind string of a timer-scheduled entry, {@code "TimerScheduled"}. */
    public static final String KIND = "TimerScheduled";

    private TimerScheduledEntry() {}

    /**
     * Builds the {@code TimerScheduled}-shaped entry for {@code timer}, a timer that the
     * {@code TransitionFired} row for {@code objectId} starts.
     *
     * @param scope the tenant and principal the timer is started under; must not be {@code null}
     * @param objectId the object the timer belongs to; must not be {@code null}
     * @param typeIri the IRI of the object's type; must not be {@code null}
     * @param ontologyVersion the ontology version the timer was resolved at; the row's stamp
     * @param timer the resolved timer to describe; must not be {@code null}
     * @param occurredAt when the entry is recorded as occurring; must not be {@code null}
     * @return the entry, of kind {@link #KIND}
     */
    public static OutboxEntry of(
            Scope scope,
            ObjectId objectId,
            String typeIri,
            int ontologyVersion,
            ResolvedTimer timer,
            Instant occurredAt) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(timer, "timer must not be null");
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.value().toString());
        payload.put("tenantId", scope.tenantId().value());
        payload.put("principalId", scope.principal().id());
        payload.put("typeIri", typeIri);
        payload.put("state", timer.stateIri());
        payload.put("transitionName", timer.transitionName());
        payload.put("after", timer.after().toString());
        payload.put("timerKey", timer.timerKey());
        payload.put("ontologyVersion", ontologyVersion);
        return new OutboxEntry(UUID.randomUUID(), KIND, payload, occurredAt);
    }
}
