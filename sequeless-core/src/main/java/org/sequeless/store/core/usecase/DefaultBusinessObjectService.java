package org.sequeless.store.core.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.api.BusinessObjectService;
import org.sequeless.store.core.ontology.SchemaAndBehaviour;
import org.sequeless.store.core.statemachine.PayloadValueCodec;
import org.sequeless.store.core.validation.CoercionResult;
import org.sequeless.store.core.validation.StructuralValidator;
import org.sequeless.store.core.validation.TypeHierarchy;
import org.sequeless.store.core.validation.ValidationException;
import org.sequeless.store.core.validation.ValueCoercer;
import org.sequeless.store.spi.AuthorizationPort;
import org.sequeless.store.spi.ObjectStorePort;
import org.sequeless.store.spi.ValidationPort;
import org.sequeless.store.spi.Violation;

/**
 * Reference implementation of {@link BusinessObjectService}.
 *
 * <p>Every method resolves {@code type} against the current schema first — the same order {@code
 * DefaultMetaModelService#describeType} uses — because authorization happens against the resolved
 * canonical type IRI, which is only known once resolution succeeds. Consulting the injected {@link
 * AuthorizationPort} is never optional, never cached, and never short-circuited.
 *
 * <p>{@link #edit} and {@link #delete} both read the existing object before doing anything else,
 * even though {@link #delete}'s {@code expectedVersion} may be absent: this is not an
 * optimisation, it is required to preserve {@code createdAt}/{@code createdBy} on edit and to know
 * what "the current version" even is for a version-less delete. Both also treat "found, but of an
 * unrelated type" identically to "not found", using {@link TypeHierarchy#isSubtypeOf} — the same
 * helper {@link BusinessObjectGuards#requireMatchingType} applies, and {@code StructuralValidator} uses for
 * reference-target checking — rather than a hand-rolled equality check.
 *
 * <p>{@code state} on a freshly {@link #add added} object is {@link Optional#of} the resolved
 * type's {@link org.sequeless.ontology.behaviour.api.StateMachineDefinition#initialState()} IRI when the
 * resolved type has a state machine, or {@link Optional#empty()} otherwise; {@link #edit} never
 * changes an existing object's {@code state} itself — moving an object through its state machine
 * after creation is {@link org.sequeless.store.core.usecase.DefaultTransitionService}'s job, not this
 * class's. Every {@link Instant} this class produces — {@code Audit.createdAt}/{@code updatedAt},
 * {@code Delete.at}, {@code OutboxEntry.occurredAt} — is truncated to {@link ChronoUnit#MICROS},
 * since the object store may only round-trip microsecond precision.
 */
public final class DefaultBusinessObjectService implements BusinessObjectService {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final ObjectStorePort objectStorePort;
    private final ValidationPort validationPort;
    private final AuthorizationPort authorizationPort;
    private final Clock clock;

    /**
     * Builds the service over its schema and behaviour ports, the object store, the validation port, the
     * authorization port, and the clock every {@link Instant} this class produces is drawn from.
     *
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param objectStorePort the port every write, and every existence check before a write, goes
     *     through; must not be {@code null}
     * @param validationPort the port consulted for the SHACL check on {@link #add}/{@link #edit};
     *     must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultBusinessObjectService(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            ObjectStorePort objectStorePort,
            ValidationPort validationPort,
            AuthorizationPort authorizationPort,
            Clock clock) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.objectStorePort = Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.validationPort = Objects.requireNonNull(validationPort, "validationPort must not be null");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<TypeRef> typeOf(Scope scope, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        // The store's own tenant filter decides: an object of another tenant is absent from the result.
        return Optional.ofNullable(objectStorePort.typesOf(scope, Set.of(id)).get(id));
    }

    @Override
    public BusinessObject add(Scope scope, String type, Map<String, Object> properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        return add(scope, type, ObjectId.random(), properties);
    }

    @Override
    public BusinessObject add(Scope scope, String type, ObjectId id, Map<String, Object> properties) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        TypeDefinition resolved = BusinessObjectGuards.resolveType(schema, type);
        BusinessObjectGuards.authorize(authorizationPort, scope, Operation.ADD, resolved.iri());

        Map<PropertyRef, Value> coerced = coerceOrThrow(resolved, behaviour, properties);
        List<Violation> structural =
                StructuralValidator.validateForCreate(scope, schema, behaviour, resolved, coerced, objectStorePort);
        if (!structural.isEmpty()) {
            throw new ValidationException(ValidationException.Source.STRUCTURAL, structural);
        }

        Instant now = now();
        String by = scope.principal().id();
        Optional<String> initialState =
                behaviour.stateMachine(resolved.iri()).map(m -> m.initialState().iri());
        BusinessObject candidate = new BusinessObject(
                id,
                new TypeRef(resolved.iri()),
                scope.tenantId(),
                1,
                initialState,
                coerced,
                new Audit(now, by, now, by),
                false);

        List<Violation> shaclViolations = validationPort.validate(scope, schema, candidate);
        if (!shaclViolations.isEmpty()) {
            throw new ValidationException(ValidationException.Source.SHACL, shaclViolations);
        }

        OutboxEntry outboxEntry = new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_CREATED,
                outboxPayload(
                        candidate.id(),
                        1L,
                        candidate.type().iri(),
                        schema.ontologyVersion().value()),
                now);
        ChangeSet changeSet = new ChangeSet(List.<Mutation>of(new Create(candidate)), List.of(outboxEntry));

        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    @Override
    public BusinessObject edit(
            Scope scope, String type, ObjectId id, long expectedVersion, Map<String, Object> properties) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        TypeDefinition resolved = BusinessObjectGuards.resolveType(schema, type);
        BusinessObjectGuards.authorize(authorizationPort, scope, Operation.EDIT, resolved.iri());

        BusinessObject existing = objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        BusinessObjectGuards.requireMatchingType(schema, existing.type(), resolved, id);

        Map<PropertyRef, Value> coerced = coerceOrThrow(resolved, behaviour, properties);
        List<Violation> structural =
                StructuralValidator.validateForUpdate(scope, schema, behaviour, resolved, coerced, objectStorePort);
        if (!structural.isEmpty()) {
            throw new ValidationException(ValidationException.Source.STRUCTURAL, structural);
        }

        Instant now = now();
        String by = scope.principal().id();
        Audit audit = new Audit(existing.audit().createdAt(), existing.audit().createdBy(), now, by);
        BusinessObject candidate = new BusinessObject(
                id, existing.type(), existing.tenant(), expectedVersion + 1, existing.state(), coerced, audit, false);

        List<Violation> shaclViolations = validationPort.validate(scope, schema, candidate);
        if (!shaclViolations.isEmpty()) {
            throw new ValidationException(ValidationException.Source.SHACL, shaclViolations);
        }

        OutboxEntry outboxEntry = new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_UPDATED,
                outboxPayload(
                        id,
                        expectedVersion + 1,
                        existing.type().iri(),
                        schema.ontologyVersion().value()),
                now);
        ChangeSet changeSet =
                new ChangeSet(List.<Mutation>of(new Update(candidate, expectedVersion)), List.of(outboxEntry));

        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    @Override
    public void delete(Scope scope, String type, ObjectId id, OptionalLong expectedVersion) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(expectedVersion, "expectedVersion must not be null");

        Schema schema = schemaSource.schema(scope.tenantId());
        TypeDefinition resolved = BusinessObjectGuards.resolveType(schema, type);
        BusinessObjectGuards.authorize(authorizationPort, scope, Operation.DELETE, resolved.iri());

        BusinessObject existing = objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        BusinessObjectGuards.requireMatchingType(schema, existing.type(), resolved, id);

        long versionToDelete = expectedVersion.orElse(existing.version());
        Instant now = now();
        String by = scope.principal().id();
        Delete deletion = new Delete(id, versionToDelete, now, by);
        Map<String, Object> payload = outboxPayload(
                id,
                versionToDelete + 1,
                existing.type().iri(),
                schema.ontologyVersion().value());
        payload.put("properties", propertiesSnapshot(existing));
        OutboxEntry outboxEntry = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_DELETED, payload, now);
        ChangeSet changeSet = new ChangeSet(List.<Mutation>of(deletion), List.of(outboxEntry));

        objectStorePort.commit(scope, changeSet);
    }

    private Map<PropertyRef, Value> coerceOrThrow(
            TypeDefinition type, Behaviour behaviour, Map<String, Object> rawProperties) {
        CoercionResult coercion = ValueCoercer.coerce(type, behaviour, rawProperties);
        if (!coercion.isSuccess()) {
            throw new ValidationException(ValidationException.Source.STRUCTURAL, coercion.violations());
        }
        return coercion.properties();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Builds the payload of an object-lifecycle Store event. {@code ontologyVersion} is the version of
     * the schema the write validated against, so the event records which ontology it was committed under.
     */
    private static Map<String, Object> outboxPayload(ObjectId id, long version, String typeIri, int ontologyVersion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", id.value().toString());
        payload.put("version", version);
        payload.put("typeIri", typeIri);
        payload.put("ontologyVersion", ontologyVersion);
        return payload;
    }

    /**
     * Encodes {@code object}'s stored properties, keyed by their <b>full IRI</b> (not short name),
     * into the tagged, JSON-compatible shape {@link PayloadValueCodec#toPayload} produces — the same
     * scheme {@code DefaultTransitionService.selfPayload} uses for a {@code TransitionFired} row's {@code
     * self} map, except keyed differently on purpose: {@code self} is short-name-keyed because it
     * feeds an {@link org.sequeless.expression.api.ExpressionContext}, whereas this snapshot is
     * consulted by {@code DefaultTriggerEvaluator}/{@code DefaultDerivationRecomputer} matching
     * against {@code sq:watch}/{@code sq:via} IRIs directly, so short names would force those
     * consumers to re-derive full IRIs from a name they don't otherwise need.
     *
     * <p>This snapshot exists solely because {@link ObjectStorePort#find} contractually returns
     * {@link Optional#empty()} for a soft-deleted object (see that method's own javadoc) — once
     * {@link #delete} commits, there is no port method left that can read the deleted object's
     * properties. Without this snapshot embedded in the {@code ObjectDeleted} payload itself, a
     * consumer reacting to a Task's deletion could never learn which Project it belonged to. {@code
     * ObjectCreated}/{@code ObjectUpdated} consumers do not need this: they read the still-live
     * object straight from {@link ObjectStorePort#find}, which is the intended, simpler, convergent
     * path when the object still exists.
     */
    private static Map<String, Object> propertiesSnapshot(BusinessObject object) {
        Map<String, Object> snapshot = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : object.properties().entrySet()) {
            snapshot.put(entry.getKey().iri(), PayloadValueCodec.toPayload(entry.getValue()));
        }
        return snapshot;
    }
}
