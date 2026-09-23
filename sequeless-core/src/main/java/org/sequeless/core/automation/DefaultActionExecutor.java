package org.sequeless.core.automation;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference implementation of {@link ActionExecutor}, called back into by an automation adapter
 * (the in-process relay, or a Temporal activity) once a fired transition's {@code ActionRequest}
 * outbox row has been dispatched. Every mutation this class performs goes through {@link
 * ObjectStorePort#commit}, and every {@code value}/{@code expression}/template field an {@code
 * ActionRequest} payload carries is resolved through {@link ExpressionPort} — never evaluated by
 * hand here — exactly mirroring how {@code DefaultBusinessObjectService} and {@code
 * DefaultTransitionService} apply every other write.
 *
 * <p>Both {@link #applySetProperty} and {@link #applyCreateObject} are designed to be safely
 * retried: {@link #applySetProperty} only commits when the target value actually differs from the
 * property's current value, and is a silent no-op if the target object no longer exists; {@link
 * #applyCreateObject} derives the new object's id deterministically from {@code entry.id()} and
 * {@code actionIndex} and is a no-op if an object already exists at that id. Neither {@link
 * #resolveWebhook} nor {@link #applyLog} touches {@link ObjectStorePort} at all — they only
 * render templated text through {@link ExpressionPort}, leaving any I/O (an HTTP call, a log
 * sink) to the caller.
 */
public final class DefaultActionExecutor implements ActionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultActionExecutor.class);

    private final ExpressionPort expressionPort;
    private final ObjectStorePort objectStorePort;
    private final Clock clock;

    /**
     * @param expressionPort the port every {@code value}/{@code expression}/template field is
     *     resolved through; must not be {@code null}
     * @param objectStorePort the port every mutation this class performs goes through; must not be
     *     {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultActionExecutor(
        ExpressionPort expressionPort, ObjectStorePort objectStorePort, Clock clock) {
        this.expressionPort =
            Objects.requireNonNull(expressionPort, "expressionPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void applySetProperty(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        Map<String, Object> payload = entry.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String property = (String) payload.get("property");

        Optional<BusinessObject> found = objectStorePort.find(scope, objectId);
        if (found.isEmpty()) {
            // The object may have been deleted since the transition fired; this is a safe,
            // retryable no-op, never a failure.
            return;
        }
        BusinessObject current = found.get();

        Value target = resolveTargetValue(payload, buildContext(payload));
        PropertyRef propertyRef = new PropertyRef(property);
        if (Objects.equals(current.properties().get(propertyRef), target)) {
            return;
        }

        Map<PropertyRef, Value> properties = new HashMap<>(current.properties());
        properties.put(propertyRef, target);

        Instant now = now();
        String by = scope.principal().id();
        Audit audit =
            new Audit(current.audit().createdAt(), current.audit().createdBy(), now, by);
        BusinessObject candidate =
            new BusinessObject(
                objectId,
                current.type(),
                current.tenant(),
                current.version() + 1,
                current.state(),
                properties,
                audit,
                false);

        OutboxEntry outboxEntry =
            new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_UPDATED,
                outboxPayload(objectId, current.version() + 1, current.type().iri()),
                now);
        ChangeSet changeSet =
            new ChangeSet(
                List.<Mutation>of(new Update(candidate, current.version())), List.of(outboxEntry));
        objectStorePort.commit(scope, changeSet);
    }

    @Override
    public void applyCreateObject(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        Map<String, Object> payload = entry.payload();
        int actionIndex = ((Number) payload.get("actionIndex")).intValue();
        ObjectId newId = derivedCreateId(entry.id(), actionIndex);

        if (objectStorePort.find(scope, newId).isPresent()) {
            // Idempotent no-op: a retried activity must never create a second object.
            return;
        }

        String createType = (String) payload.get("createType");
        @SuppressWarnings("unchecked")
        Map<String, Object> createProperties = (Map<String, Object>) payload.get("createProperties");

        ExpressionContext context = buildContext(payload);
        Map<PropertyRef, Value> properties = new HashMap<>();
        for (Map.Entry<String, Object> entry2 : createProperties.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueOrExpression = (Map<String, Object>) entry2.getValue();
            Value value = resolveTargetValue(valueOrExpression, context);
            properties.put(new PropertyRef(entry2.getKey()), value);
        }

        Instant now = now();
        String by = scope.principal().id();
        BusinessObject candidate =
            new BusinessObject(
                newId,
                new TypeRef(createType),
                scope.tenantId(),
                1,
                Optional.empty(),
                properties,
                new Audit(now, by, now, by),
                false);

        OutboxEntry outboxEntry =
            new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_CREATED,
                outboxPayload(newId, 1L, createType),
                now);
        ChangeSet changeSet =
            new ChangeSet(List.<Mutation>of(new Create(candidate)), List.of(outboxEntry));
        objectStorePort.commit(scope, changeSet);
    }

    @Override
    public ResolvedWebhookRequest resolveWebhook(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        Map<String, Object> payload = entry.payload();
        ExpressionContext context = buildContext(payload);
        String url = expressionPort.renderTemplate((String) payload.get("url"), context);
        String method = (String) payload.get("method");
        Optional<String> body =
            payload.containsKey("body")
                ? Optional.of(expressionPort.renderTemplate((String) payload.get("body"), context))
                : Optional.empty();
        return new ResolvedWebhookRequest(url, method, body);
    }

    @Override
    public void applyLog(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        Map<String, Object> payload = entry.payload();
        ExpressionContext context = buildContext(payload);
        String message = expressionPort.renderTemplate((String) payload.get("message"), context);
        LOG.info(message);
    }

    /**
     * Resolves the {@code value}/{@code expression} pair a {@code SetProperty} action or a single
     * {@code createProperties} entry carries: a literal {@code value} (tagged map, decoded via
     * {@link PayloadValueCodec#fromPayload}) if present, else an {@code expression} evaluated
     * through {@link ExpressionPort#evaluate} against {@code context}.
     */
    private Value resolveTargetValue(Map<String, Object> valueOrExpression, ExpressionContext context) {
        Object value = valueOrExpression.get("value");
        if (value != null) {
            return PayloadValueCodec.fromPayload(value);
        }
        String expression = (String) valueOrExpression.get("expression");
        return expressionPort.evaluate(expression, context);
    }

    /**
     * Builds the {@link ExpressionContext} every {@code value}/{@code expression}/template field in
     * an {@code ActionRequest} payload is resolved against: {@code self} decodes directly into
     * {@link ExpressionContext#selfProperties()} with zero re-keying, since both are keyed by
     * property short name.
     */
    private ExpressionContext buildContext(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> self = (Map<String, Object>) payload.get("self");
        Map<String, Value> selfProperties = new HashMap<>();
        self.forEach((name, taggedValue) -> selfProperties.put(name, PayloadValueCodec.fromPayload(taggedValue)));
        Optional<String> selfState = Optional.of((String) payload.get("state"));
        String principalId = (String) payload.get("principalId");
        return new ExpressionContext(selfProperties, selfState, principalId);
    }

    /**
     * Derives a {@code CreateObject} action's new object id deterministically from the {@code
     * ActionRequest} outbox entry's own id and its {@code actionIndex}, so a retried call resolves
     * to the exact same id every time and {@link #applyCreateObject}'s {@code find}-first check can
     * detect a duplicate.
     */
    private static ObjectId derivedCreateId(UUID entryId, int actionIndex) {
        return new ObjectId(
            UUID.nameUUIDFromBytes(
                (entryId.toString() + ":" + actionIndex).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Duplicated from {@code DefaultBusinessObjectService}'s identically-named private helper —
     * this codebase's established convention for this specific short pure function — widened here
     * exactly as that copy was, to carry {@code typeIri}: the {@code ObjectUpdated}/{@code
     * ObjectCreated} entries this class writes are ordinary domain events flowing through the same
     * dispatch path as {@code DefaultBusinessObjectService}'s own, so {@code
     * DefaultTriggerEvaluator}/{@code DefaultDerivationRecomputer} need the changed object's type
     * from these payloads exactly as much as from those. Without it, a write made by an action
     * (a {@code SetProperty} or {@code CreateObject}) would silently fail to retrigger automation.
     */
    private static Map<String, Object> outboxPayload(ObjectId id, long version, String typeIri) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", id.value().toString());
        payload.put("version", version);
        payload.put("typeIri", typeIri);
        return payload;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
