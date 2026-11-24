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
import java.util.function.Supplier;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference implementation of {@link ActionExecutor}, called back into by an automation adapter
 * (the in-process relay, or a Temporal activity) once a fired transition's {@code TransitionFired}
 * outbox row has been dispatched. The {@code TransitionFired} row carries the {@code self} map and
 * the transition's keys (its machine IRI, from state and name), and the single-action methods read
 * them from it. Every mutation this class performs goes through {@link ObjectStorePort#commit}, and
 * every {@code value}/{@code expression}/template field of an action is resolved through {@link
 * ExpressionPort} — never evaluated by hand here — exactly mirroring how {@code
 * DefaultBusinessObjectService} and {@code DefaultTransitionService} apply every other write.
 *
 * <p>{@link #applyAction} is designed to be safely retried. A {@code SetProperty} action only commits
 * when the target value actually differs from the property's current value, and is a silent no-op if
 * the target object no longer exists. A {@code CreateObject} action derives the new object's id
 * deterministically from the row's id and the action's index, and is a no-op if an object already
 * exists at that id. {@link #resolveWebhookAction} and a {@code Log} action do not touch {@link
 * ObjectStorePort} at all — they only render templated text through {@link ExpressionPort}, leaving
 * any I/O (an HTTP call, a log sink) to the caller.
 *
 * <p>The write and render logic is shared through private helpers. The single-action methods take
 * the typed {@link Action} and read the row's context from a {@code TransitionFired} row.
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
    public DefaultActionExecutor(ExpressionPort expressionPort, ObjectStorePort objectStorePort, Clock clock) {
        this.expressionPort = Objects.requireNonNull(expressionPort, "expressionPort must not be null");
        this.objectStorePort = Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(transitionFired, "transitionFired must not be null");
        Objects.requireNonNull(action, "action must not be null");

        Map<String, Object> row = transitionFired.payload();
        switch (action) {
            case SetPropertyAction setProperty -> {
                ObjectId objectId = ObjectId.parse((String) row.get("objectId"));
                commitSetProperty(
                        scope,
                        objectId,
                        setProperty.propertyIri(),
                        transitionFired,
                        () -> resolveTarget(
                                setProperty.value().orElse(null),
                                setProperty.expression().orElse(null),
                                buildContext(row, "toState")));
            }
            case CreateObjectAction createObject ->
                commitCreateObject(
                        scope,
                        transitionFired,
                        actionIndex,
                        createObject.typeIri(),
                        () -> resolveAssignments(createObject.properties(), buildContext(row, "toState")));
            case LogAction log -> logMessage(buildContext(row, "toState"), log.message());
            case WebhookAction webhook ->
                throw new IllegalArgumentException(
                        "A webhook action is resolved through resolveWebhookAction, not applyAction");
        }
    }

    @Override
    public ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(transitionFired, "transitionFired must not be null");
        Objects.requireNonNull(action, "action must not be null");

        return renderWebhook(
                buildContext(transitionFired.payload(), "toState"), action.url(), action.method(), action.body());
    }

    /**
     * The one SetProperty write {@link #applyAction} makes. Does
     * nothing if the target object no longer exists. Otherwise resolves {@code target} (only now,
     * once the object is known to exist) and commits an update only if the value differs from the
     * property's current value.
     */
    private void commitSetProperty(
            Scope scope, ObjectId objectId, String propertyIri, OutboxEntry sourceRow, Supplier<Value> target) {
        Optional<BusinessObject> found = objectStorePort.find(scope, objectId);
        if (found.isEmpty()) {
            // The object may have been deleted since the transition fired; this is a safe,
            // retryable no-op, never a failure.
            return;
        }
        BusinessObject current = found.get();

        Value value = target.get();
        PropertyRef propertyRef = new PropertyRef(propertyIri);
        if (Objects.equals(current.properties().get(propertyRef), value)) {
            return;
        }

        Map<PropertyRef, Value> properties = new HashMap<>(current.properties());
        properties.put(propertyRef, value);

        Instant now = now();
        String by = scope.principal().id();
        Audit audit = new Audit(current.audit().createdAt(), current.audit().createdBy(), now, by);
        BusinessObject candidate = new BusinessObject(
                objectId,
                current.type(),
                current.tenant(),
                current.version() + 1,
                current.state(),
                properties,
                audit,
                false);

        OutboxEntry outboxEntry = new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_UPDATED,
                outboxPayload(objectId, current.version() + 1, current.type().iri(), sourceRow),
                now);
        ChangeSet changeSet =
                new ChangeSet(List.<Mutation>of(new Update(candidate, current.version())), List.of(outboxEntry));
        objectStorePort.commit(scope, changeSet);
    }

    /**
     * The one CreateObject write {@link #applyAction} makes.
     * Derives the new object's id from {@code sourceRow}'s id and {@code actionIndex}, and does
     * nothing if an object already exists at that id. Otherwise resolves {@code properties} (only
     * now, once that check has passed) and creates the object.
     */
    private void commitCreateObject(
            Scope scope,
            OutboxEntry sourceRow,
            int actionIndex,
            String createType,
            Supplier<Map<PropertyRef, Value>> properties) {
        ObjectId newId = derivedCreateId(sourceRow.id(), actionIndex);

        if (objectStorePort.find(scope, newId).isPresent()) {
            // Idempotent no-op: a retried activity must never create a second object.
            return;
        }

        Map<PropertyRef, Value> resolved = properties.get();
        Instant now = now();
        String by = scope.principal().id();
        BusinessObject candidate = new BusinessObject(
                newId,
                new TypeRef(createType),
                scope.tenantId(),
                1,
                Optional.empty(),
                resolved,
                new Audit(now, by, now, by),
                false);

        OutboxEntry outboxEntry = new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_CREATED,
                outboxPayload(newId, 1L, createType, sourceRow),
                now);
        ChangeSet changeSet = new ChangeSet(List.<Mutation>of(new Create(candidate)), List.of(outboxEntry));
        objectStorePort.commit(scope, changeSet);
    }

    /** Resolves a {@code CreateObject} action's property assignments against {@code context}. */
    private Map<PropertyRef, Value> resolveAssignments(
            List<PropertyAssignment> assignments, ExpressionContext context) {
        Map<PropertyRef, Value> properties = new HashMap<>();
        for (PropertyAssignment assignment : assignments) {
            Value value = resolveTarget(
                    assignment.value().orElse(null), assignment.expression().orElse(null), context);
            properties.put(new PropertyRef(assignment.propertyIri()), value);
        }
        return properties;
    }

    /**
     * Renders a webhook's url, and its body if one is present, against {@code context}. {@code
     * method} is used verbatim. Used by {@link #resolveWebhookAction}.
     */
    private ResolvedWebhookRequest renderWebhook(
            ExpressionContext context, String urlTemplate, String method, Optional<String> bodyTemplate) {
        String url = expressionPort.renderTemplate(urlTemplate, context);
        Optional<String> body = bodyTemplate.map(template -> expressionPort.renderTemplate(template, context));
        return new ResolvedWebhookRequest(url, method, body);
    }

    /**
     * Renders a {@code Log} action's message against {@code context} and logs it. Used by {@link
     * #applyAction}.
     */
    private void logMessage(ExpressionContext context, String messageTemplate) {
        String message = expressionPort.renderTemplate(messageTemplate, context);
        LOG.info(message);
    }

    /**
     * Resolves the {@code value}/{@code expression} pair a {@code SetProperty} action or a single
     * {@code CreateObject} property assignment carries: a literal {@code value} if present, else an
     * {@code expression} evaluated through {@link ExpressionPort#evaluate} against {@code context}.
     */
    private Value resolveTarget(Value value, String expression, ExpressionContext context) {
        return value != null ? value : expressionPort.evaluate(expression, context);
    }

    /**
     * Builds the {@link ExpressionContext} every {@code value}/{@code expression}/template field is
     * resolved against: {@code self} decodes directly into {@link ExpressionContext#selfProperties()}
     * with zero re-keying, since both are keyed by property short name. {@code stateKey} names the
     * row's state, which the single-action methods pass as {@code "toState"}: the state the
     * transition moved the object to.
     */
    private ExpressionContext buildContext(Map<String, Object> row, String stateKey) {
        @SuppressWarnings("unchecked")
        Map<String, Object> self = (Map<String, Object>) row.get("self");
        Map<String, Value> selfProperties = new HashMap<>();
        self.forEach((name, taggedValue) -> selfProperties.put(name, PayloadValueCodec.fromPayload(taggedValue)));
        Optional<String> selfState = Optional.of((String) row.get(stateKey));
        String principalId = (String) row.get("principalId");
        return new ExpressionContext(selfProperties, selfState, principalId);
    }

    /**
     * Derives a {@code CreateObject} action's new object id deterministically from the outbox
     * row's own id and its {@code actionIndex}, so a retried call resolves to the exact same id
     * every time and {@link #commitCreateObject}'s {@code find}-first check can detect a duplicate.
     */
    private static ObjectId derivedCreateId(UUID rowId, int actionIndex) {
        return new ObjectId(
                UUID.nameUUIDFromBytes((rowId.toString() + ":" + actionIndex).getBytes(StandardCharsets.UTF_8)));
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
     *
     * <p>The {@code ontologyVersion} is copied from {@code sourceRow}, the row this action was
     * applied from (a {@code TransitionFired} row), which carries the
     * version its transition fired under. This executor reads no ontology snapshot of its own. A
     * row written before the stamp existed has no version, so the key is left out for it.
     */
    private static Map<String, Object> outboxPayload(ObjectId id, long version, String typeIri, OutboxEntry sourceRow) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", id.value().toString());
        payload.put("version", version);
        payload.put("typeIri", typeIri);
        Object ontologyVersion = sourceRow.payload().get("ontologyVersion");
        if (ontologyVersion != null) {
            payload.put("ontologyVersion", ontologyVersion);
        }
        return payload;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
