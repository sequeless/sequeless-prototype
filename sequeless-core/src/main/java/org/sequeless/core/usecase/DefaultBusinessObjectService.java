package org.sequeless.core.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.BrowseQuery;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.InvalidQueryException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.derivation.DerivationPlanner;
import org.sequeless.core.derivation.DerivationPluginRegistry;
import org.sequeless.core.validation.CoercionResult;
import org.sequeless.core.validation.StructuralValidator;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.core.validation.ValueCoercer;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
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
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;
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
    private final QueryPort queryPort;
    private final Clock clock;
    private final DerivationPlanner derivationPlanner;

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every read and write ultimately goes through; must not be
     *     {@code null}
     * @param validationPort the port consulted for the SHACL check on {@link #add}/{@link #edit};
     *     must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param queryPort the port {@link #browse} delegates to once its {@link BrowseQuery} resolves
     *     cleanly against the current snapshot; must not be {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultBusinessObjectService(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        ValidationPort validationPort,
        AuthorizationPort authorizationPort,
        QueryPort queryPort,
        Clock clock) {
        this(
            ontologyPort,
            objectStorePort,
            validationPort,
            authorizationPort,
            queryPort,
            clock,
            new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader()));
    }

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every read and write ultimately goes through; must not be
     *     {@code null}
     * @param validationPort the port consulted for the SHACL check on {@link #add}/{@link #edit};
     *     must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param queryPort the port {@link #browse} delegates to once its {@link BrowseQuery} resolves
     *     cleanly against the current snapshot, and {@code derivationPlanner} uses to compute
     *     rollups; must not be {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @param derivationPlanner computes every derived property on the object(s) {@link #read} and
     *     {@link #browse} return; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultBusinessObjectService(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        ValidationPort validationPort,
        AuthorizationPort authorizationPort,
        QueryPort queryPort,
        Clock clock,
        DerivationPlanner derivationPlanner) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.validationPort =
            Objects.requireNonNull(validationPort, "validationPort must not be null");
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.derivationPlanner =
            Objects.requireNonNull(derivationPlanner, "derivationPlanner must not be null");
    }

    @Override
    public QueryResult browse(Scope scope, String type, BrowseQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(query, "query must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.BROWSE, resolved.iri());

        Map<String, PropertyDefinition> propertyIndex = propertyIndex(snapshot, resolved.iri());
        List<Violation> violations = new ArrayList<>();
        List<Criterion> criteria = buildCriteria(query.filters(), propertyIndex, violations);
        List<Sort> sorts = buildSorts(query.sorts(), propertyIndex, violations);
        List<String> facetIris = buildFacets(query.facets(), propertyIndex, violations);
        if (!violations.isEmpty()) {
            throw new InvalidQueryException(violations);
        }

        Set<String> concreteTypes = TypeHierarchy.concreteTypeAndSubtypes(snapshot, resolved.iri());
        if (concreteTypes.isEmpty()) {
            return new QueryResult(List.of(), 0, Map.of());
        }

        Query spiQuery =
            new Query(concreteTypes, criteria, query.text(), sorts, query.page(), facetIris, false);
        QueryResult result = queryPort.query(scope, snapshot, spiQuery);
        List<BusinessObject> derived =
            derivationPlanner.apply(scope, snapshot, resolved, result.items());
        return new QueryResult(derived, result.total(), result.facets());
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
        return derivationPlanner.apply(scope, snapshot, resolved, List.of(found)).get(0);
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

    /**
     * Builds the property lookup a {@link BrowseQuery}'s filter/sort/facet property names resolve
     * against: every property (by both IRI and short name) attributed to {@code typeIri} or any of
     * its transitive subtypes — a deliberately wider set than {@link
     * TypeHierarchy#concreteTypeAndSubtypes}, since a filter/sort/facet property declared only on a
     * concrete subtype must still resolve when browsing its abstract supertype.
     */
    private Map<String, PropertyDefinition> propertyIndex(MetaModelSnapshot snapshot, String typeIri) {
        Map<String, PropertyDefinition> index = new HashMap<>();
        for (TypeRef typeRef : TypeHierarchy.typeAndSubtypes(snapshot, typeIri)) {
            TypeDefinition type = snapshot.type(typeRef.iri()).orElseThrow();
            for (PropertyDefinition property : type.properties()) {
                index.put(property.iri(), property);
                index.put(shortName(property.iri()), property);
            }
        }
        return index;
    }

    /**
     * Resolves a filter/sort/facet property reference (short name or full IRI) against {@code
     * index}, rejecting an unknown property or a multi-valued one — every one of the three query
     * clauses this method backs requires a scalar property, per phase-3's explicit scope. Records a
     * {@link Violation} and returns {@code null} on either failure, rather than throwing, so a
     * single {@link BrowseQuery} can report every violation it contains at once.
     */
    private PropertyDefinition resolveQueryableProperty(
        String nameOrIri, Map<String, PropertyDefinition> index, List<Violation> violations, String kind) {
        PropertyDefinition property = index.get(nameOrIri);
        if (property == null) {
            violations.add(new Violation(nameOrIri, "Unknown property '" + nameOrIri + "'"));
            return null;
        }
        if (!isScalar(property)) {
            violations.add(
                new Violation(
                    property.iri(),
                    "Property '" + shortName(property.iri())
                        + "' is multi-valued and cannot be used in a " + kind));
            return null;
        }
        return property;
    }

    private static boolean isScalar(PropertyDefinition property) {
        OptionalInt max = property.cardinality().max();
        return max.isPresent() && max.getAsInt() == 1;
    }

    private List<Criterion> buildCriteria(
        List<BrowseQuery.Filter> filters,
        Map<String, PropertyDefinition> index,
        List<Violation> violations) {
        List<Criterion> criteria = new ArrayList<>();
        for (BrowseQuery.Filter filter : filters) {
            PropertyDefinition property =
                resolveQueryableProperty(filter.property(), index, violations, "filter");
            if (property == null) {
                continue;
            }
            Optional<Operator> operator = parseOperatorToken(filter.operator());
            if (operator.isEmpty()) {
                violations.add(
                    new Violation(
                        filter.property(), "Unknown filter operator '" + filter.operator() + "'"));
                continue;
            }
            if (!operatorApplicable(operator.get(), property)) {
                violations.add(
                    new Violation(
                        property.iri(),
                        "Operator '" + filter.operator() + "' is not applicable to property '"
                            + shortName(property.iri()) + "'"));
                continue;
            }
            Optional<Value> value =
                buildCriterionValue(property, operator.get(), filter.rawValue(), violations);
            criteria.add(new Criterion(property.iri(), operator.get(), value));
        }
        return criteria;
    }

    private Optional<Value> buildCriterionValue(
        PropertyDefinition property,
        Operator operator,
        Optional<String> rawValue,
        List<Violation> violations) {
        if (operator == Operator.IS_NULL || operator == Operator.NOT_NULL) {
            return Optional.empty();
        }
        if (rawValue.isEmpty()) {
            violations.add(
                new Violation(property.iri(), "value required for operator " + operator));
            return Optional.empty();
        }
        String raw = rawValue.get();
        if (operator == Operator.IN) {
            String[] tokens = raw.split(",", -1);
            List<Value> values = new ArrayList<>();
            boolean allSucceeded = true;
            for (String token : tokens) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    violations.add(
                        new Violation(
                            property.iri(), "IN operator candidate value must not be blank"));
                    allSucceeded = false;
                    continue;
                }
                Optional<Value> coerced = coerceFilterScalar(property, trimmed, violations);
                if (coerced.isEmpty()) {
                    allSucceeded = false;
                    continue;
                }
                values.add(coerced.get());
            }
            if (!allSucceeded) {
                return Optional.empty();
            }
            return Optional.of(Value.list(values));
        }
        return coerceFilterScalar(property, raw, violations);
    }

    /**
     * Coerces one raw filter token to a {@link Value} per {@code property}'s datatype, via {@link
     * ValueCoercer#coerceScalarValue}. {@link ValueCoercer#coerceScalarValue}'s {@code INTEGER}/
     * {@code LONG} branch requires a {@link Number}, and its {@code BOOLEAN} branch requires a
     * {@link Boolean} — neither parses a numeric/boolean string itself — but a filter value always
     * arrives here as a raw {@link String}, so those two datatype families are pre-parsed before
     * the call. Every other datatype family (including relationship UUIDs) already accepts a raw
     * {@link String} as-is.
     */
    private Optional<Value> coerceFilterScalar(
        PropertyDefinition property, String raw, List<Violation> violations) {
        Object nativeValue = raw;
        if (property instanceof AttributeDefinition attribute) {
            switch (attribute.datatype()) {
                case INTEGER, LONG -> {
                    try {
                        nativeValue = new BigDecimal(raw);
                    } catch (NumberFormatException e) {
                        violations.add(new Violation(property.iri(), "expected an integer"));
                        return Optional.empty();
                    }
                }
                case BOOLEAN -> {
                    if ("true".equalsIgnoreCase(raw)) {
                        nativeValue = Boolean.TRUE;
                    } else if ("false".equalsIgnoreCase(raw)) {
                        nativeValue = Boolean.FALSE;
                    } else {
                        violations.add(new Violation(property.iri(), "expected a boolean"));
                        return Optional.empty();
                    }
                }
                default -> {
                    // DECIMAL/DOUBLE, STRING, DATE, DATE_TIME, ANY_URI, TIME, DURATION already
                    // accept the raw string as-is; ValueCoercer.coerceScalarValue parses it.
                }
            }
        }
        ValueCoercer.ScalarResult result = ValueCoercer.coerceScalarValue(property, nativeValue);
        if (!result.isSuccess()) {
            violations.add(new Violation(property.iri(), result.errorReason()));
            return Optional.empty();
        }
        return Optional.of(result.value());
    }

    private List<Sort> buildSorts(
        List<BrowseQuery.SortKey> sortKeys,
        Map<String, PropertyDefinition> index,
        List<Violation> violations) {
        List<Sort> sorts = new ArrayList<>();
        for (BrowseQuery.SortKey sortKey : sortKeys) {
            PropertyDefinition property =
                resolveQueryableProperty(sortKey.property(), index, violations, "sort");
            if (property == null) {
                continue;
            }
            Optional<Direction> direction = parseDirectionToken(sortKey.direction());
            if (direction.isEmpty()) {
                violations.add(
                    new Violation(
                        sortKey.property(),
                        "Unknown sort direction '" + sortKey.direction() + "'"));
                continue;
            }
            sorts.add(new Sort(property.iri(), direction.get()));
        }
        return sorts;
    }

    private List<String> buildFacets(
        List<String> facets, Map<String, PropertyDefinition> index, List<Violation> violations) {
        List<String> facetIris = new ArrayList<>();
        for (String nameOrIri : facets) {
            PropertyDefinition property =
                resolveQueryableProperty(nameOrIri, index, violations, "facet");
            if (property == null) {
                continue;
            }
            if (!property.facet()) {
                violations.add(
                    new Violation(
                        property.iri(),
                        "Property '" + shortName(property.iri()) + "' is not a facet property"));
                continue;
            }
            facetIris.add(property.iri());
        }
        return facetIris;
    }

    private static boolean operatorApplicable(Operator operator, PropertyDefinition property) {
        return switch (operator) {
            case EQ, NE, IN, IS_NULL, NOT_NULL -> true;
            case LT, LTE, GT, GTE ->
                property instanceof AttributeDefinition attribute
                    && switch (attribute.datatype()) {
                        case INTEGER, LONG, DECIMAL, DOUBLE, DATE, DATE_TIME -> true;
                        default -> false;
                    };
            case CONTAINS, STARTS_WITH ->
                property instanceof AttributeDefinition attribute
                    && attribute.datatype() == Datatype.STRING;
        };
    }

    private static Optional<Operator> parseOperatorToken(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "eq" -> Optional.of(Operator.EQ);
            case "ne" -> Optional.of(Operator.NE);
            case "in" -> Optional.of(Operator.IN);
            case "lt" -> Optional.of(Operator.LT);
            case "lte" -> Optional.of(Operator.LTE);
            case "gt" -> Optional.of(Operator.GT);
            case "gte" -> Optional.of(Operator.GTE);
            case "contains" -> Optional.of(Operator.CONTAINS);
            case "startswith" -> Optional.of(Operator.STARTS_WITH);
            case "isnull" -> Optional.of(Operator.IS_NULL);
            case "notnull" -> Optional.of(Operator.NOT_NULL);
            default -> Optional.empty();
        };
    }

    private static Optional<Direction> parseDirectionToken(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "asc" -> Optional.of(Direction.ASC);
            case "desc" -> Optional.of(Direction.DESC);
            default -> Optional.empty();
        };
    }

    /**
     * Duplicated from {@link ValueCoercer}'s private identically-named helper rather than shared:
     * this codebase's established convention for this specific short pure function (the substring
     * after an IRI's last {@code #}, else after its last {@code /}, else the whole IRI) is to
     * duplicate it at each use site rather than introduce a shared dependency for one line of logic.
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
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
