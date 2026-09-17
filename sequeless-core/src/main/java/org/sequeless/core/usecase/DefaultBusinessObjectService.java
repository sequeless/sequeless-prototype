package org.sequeless.core.usecase;

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
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.validation.CoercionResult;
import org.sequeless.core.validation.StructuralValidator;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.core.validation.ValueCoercer;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;

/**
 * Reference implementation of {@link BusinessObjectService}.
 *
 * <p>Every method resolves {@code type} against the current {@link
 * OntologyPort#snapshot(Scope) snapshot} first — the same order {@link
 * DefaultMetaModelService#describeType} uses — because authorization happens against the resolved
 * canonical type IRI, which is only known once resolution succeeds. Consulting the injected {@link
 * AuthorizationPort} is never optional, never cached, and never short-circuited.
 *
 * <p>{@link #edit} and {@link #delete} both read the existing object before doing anything else,
 * even though {@link #delete}'s {@code expectedVersion} may be absent: this is not an
 * optimisation, it is required to preserve {@code createdAt}/{@code createdBy} on edit and to know
 * what "the current version" even is for a version-less delete. Both also treat "found, but of an
 * unrelated type" identically to "not found", using {@link TypeHierarchy#isSubtypeOf} — the same
 * helper {@link #browse} uses to list a type's subtypes and {@code StructuralValidator} uses for
 * reference-target checking — rather than a hand-rolled equality check.
 *
 * <p>{@code state} is always {@link Optional#empty()} on every object this class builds: no state
 * machine is in scope for this phase. Every {@link Instant} this class produces — {@code
 * Audit.createdAt}/{@code updatedAt}, {@code Delete.at}, {@code OutboxEntry.occurredAt} — is
 * truncated to {@link ChronoUnit#MICROS}, since the object store may only round-trip microsecond
 * precision.
 */
public final class DefaultBusinessObjectService implements BusinessObjectService {

    private final OntologyPort ontologyPort;
    private final ObjectStorePort objectStorePort;
    private final ValidationPort validationPort;
    private final AuthorizationPort authorizationPort;
    private final Clock clock;

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every read and write ultimately goes through; must not be
     *     {@code null}
     * @param validationPort the port consulted for the SHACL check on {@link #add}/{@link #edit};
     *     must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultBusinessObjectService(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        ValidationPort validationPort,
        AuthorizationPort authorizationPort,
        Clock clock) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.validationPort =
            Objects.requireNonNull(validationPort, "validationPort must not be null");
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PageResult<BusinessObject> browse(Scope scope, String type, Page page) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(page, "page must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.BROWSE, resolved.iri());

        Set<TypeRef> types = TypeHierarchy.typeAndSubtypes(snapshot, resolved.iri());
        return objectStorePort.browse(scope, types, page);
    }

    @Override
    public BusinessObject read(Scope scope, String type, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.READ, resolved.iri());

        BusinessObject found =
            objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(snapshot, found, resolved, id);
        return found;
    }

    @Override
    public BusinessObject add(Scope scope, String type, Map<String, Object> properties) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.ADD, resolved.iri());

        Map<PropertyRef, Value> coerced = coerceOrThrow(resolved, properties);
        List<Violation> structural =
            StructuralValidator.validateForCreate(
                scope, snapshot, resolved, coerced, objectStorePort);
        if (!structural.isEmpty()) {
            throw new ValidationException(ValidationException.Source.STRUCTURAL, structural);
        }

        Instant now = now();
        String by = scope.principal().id();
        BusinessObject candidate =
            new BusinessObject(
                ObjectId.random(),
                new TypeRef(resolved.iri()),
                scope.tenantId(),
                1,
                Optional.empty(),
                coerced,
                new Audit(now, by, now, by),
                false);

        List<Violation> shaclViolations = validationPort.validate(scope, snapshot, candidate);
        if (!shaclViolations.isEmpty()) {
            throw new ValidationException(ValidationException.Source.SHACL, shaclViolations);
        }

        OutboxEntry outboxEntry =
            new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_CREATED,
                outboxPayload(candidate.id(), 1L),
                now);
        ChangeSet changeSet =
            new ChangeSet(List.<Mutation>of(new Create(candidate)), List.of(outboxEntry));

        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    @Override
    public BusinessObject edit(
        Scope scope,
        String type,
        ObjectId id,
        long expectedVersion,
        Map<String, Object> properties) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.EDIT, resolved.iri());

        BusinessObject existing =
            objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(snapshot, existing, resolved, id);

        Map<PropertyRef, Value> coerced = coerceOrThrow(resolved, properties);
        List<Violation> structural =
            StructuralValidator.validateForUpdate(
                scope, snapshot, resolved, coerced, objectStorePort);
        if (!structural.isEmpty()) {
            throw new ValidationException(ValidationException.Source.STRUCTURAL, structural);
        }

        Instant now = now();
        String by = scope.principal().id();
        Audit audit =
            new Audit(existing.audit().createdAt(), existing.audit().createdBy(), now, by);
        BusinessObject candidate =
            new BusinessObject(
                id,
                existing.type(),
                existing.tenant(),
                expectedVersion + 1,
                Optional.empty(),
                coerced,
                audit,
                false);

        List<Violation> shaclViolations = validationPort.validate(scope, snapshot, candidate);
        if (!shaclViolations.isEmpty()) {
            throw new ValidationException(ValidationException.Source.SHACL, shaclViolations);
        }

        OutboxEntry outboxEntry =
            new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_UPDATED,
                outboxPayload(id, expectedVersion + 1),
                now);
        ChangeSet changeSet =
            new ChangeSet(
                List.<Mutation>of(new Update(candidate, expectedVersion)), List.of(outboxEntry));

        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    @Override
    public void delete(Scope scope, String type, ObjectId id, OptionalLong expectedVersion) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(expectedVersion, "expectedVersion must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.DELETE, resolved.iri());

        BusinessObject existing =
            objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(snapshot, existing, resolved, id);

        long versionToDelete = expectedVersion.orElse(existing.version());
        Instant now = now();
        String by = scope.principal().id();
        Delete deletion = new Delete(id, versionToDelete, now, by);
        OutboxEntry outboxEntry =
            new OutboxEntry(
                UUID.randomUUID(),
                OutboxEntry.KIND_OBJECT_DELETED,
                outboxPayload(id, versionToDelete + 1),
                now);
        ChangeSet changeSet = new ChangeSet(List.<Mutation>of(deletion), List.of(outboxEntry));

        objectStorePort.commit(scope, changeSet);
    }

    private TypeDefinition resolveType(MetaModelSnapshot snapshot, String nameOrIri) {
        return snapshot
            .typeByName(nameOrIri)
            .or(() -> snapshot.type(nameOrIri))
            .orElseThrow(() -> new TypeNotFoundException(nameOrIri));
    }

    private void requireMatchingType(
        MetaModelSnapshot snapshot, BusinessObject object, TypeDefinition resolved, ObjectId id) {
        if (!TypeHierarchy.isSubtypeOf(snapshot, object.type().iri(), resolved.iri())) {
            throw new ObjectNotFoundException(id);
        }
    }

    private Map<PropertyRef, Value> coerceOrThrow(
        TypeDefinition type, Map<String, Object> rawProperties) {
        CoercionResult coercion = ValueCoercer.coerce(type, rawProperties);
        if (!coercion.isSuccess()) {
            throw new ValidationException(
                ValidationException.Source.STRUCTURAL, coercion.violations());
        }
        return coercion.properties();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Map<String, Object> outboxPayload(ObjectId id, long version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", id.value().toString());
        payload.put("version", version);
        return payload;
    }

    private void authorize(Scope scope, Operation operation, String resource) {
        AccessDecision decision = authorizationPort.decide(scope, operation, resource);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
