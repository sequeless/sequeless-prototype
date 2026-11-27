package org.sequeless.app.query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.DerivationRule;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.spi.query.ReadRequest;
import org.sequeless.spi.query.Sort;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.api.BrowseQuery;
import org.sequeless.store.core.api.InvalidQueryException;
import org.sequeless.store.core.derivation.DerivationPlanner;
import org.sequeless.store.core.ontology.SchemaAndBehaviour;
import org.sequeless.store.core.usecase.BusinessObjectGuards;
import org.sequeless.store.core.validation.TypeHierarchy;
import org.sequeless.store.core.validation.ValueCoercer;
import org.sequeless.store.spi.AuthorizationPort;
import org.sequeless.store.spi.Criterion;
import org.sequeless.store.spi.Operator;
import org.sequeless.store.spi.Violation;

/**
 * Reference implementation of {@link ObjectQueryService}: the browse and read use cases over ontology-typed business
 * objects. Both moved out of {@code sequeless-core} in spec 18 U6 (D1), with the helpers that resolve and coerce a
 * {@link BrowseQuery} against the schema.
 *
 * <p>Both methods resolve {@code type} against the current schema first, then authorize {@link Operation#BROWSE} or
 * {@link Operation#READ} against the resolved type's IRI before any port is called. The three checks they share with
 * the command use cases (type resolution, authorization and the stored-type match) are {@link BusinessObjectGuards},
 * so their logic stays in core.
 */
public final class DefaultObjectQueryService implements ObjectQueryService {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final AuthorizationPort authorizationPort;
    private final ReadPort readPort;
    private final BrowsePort browsePort;
    private final DerivationPlanner derivationPlanner;

    /**
     * Builds the service.
     *
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param readPort the port {@link #read} resolves a single object through; must not be {@code null}
     * @param browsePort the port {@link #browse} runs its query through once its {@link BrowseQuery} resolves
     *     cleanly against the current schema; must not be {@code null}
     * @param derivationPlanner computes every derived property on the object(s) {@link #read} and {@link #browse}
     *     return; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultObjectQueryService(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            AuthorizationPort authorizationPort,
            ReadPort readPort,
            BrowsePort browsePort,
            DerivationPlanner derivationPlanner) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.readPort = Objects.requireNonNull(readPort, "readPort must not be null");
        this.browsePort = Objects.requireNonNull(browsePort, "browsePort must not be null");
        this.derivationPlanner = Objects.requireNonNull(derivationPlanner, "derivationPlanner must not be null");
    }

    @Override
    public QueryResult browse(Scope scope, String type, BrowseQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(query, "query must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        TypeDefinition resolved = BusinessObjectGuards.resolveType(schema, type);
        BusinessObjectGuards.authorize(authorizationPort, scope, Operation.BROWSE, resolved.iri());

        Map<String, PropertyDefinition> propertyIndex = propertyIndex(schema, resolved.iri());
        List<Violation> violations = new ArrayList<>();
        List<Criterion> criteria = buildCriteria(query.filters(), behaviour, propertyIndex, violations);
        List<Sort> sorts = buildSorts(query.sorts(), behaviour, propertyIndex, violations);
        List<String> facetIris = buildFacets(query.facets(), behaviour, propertyIndex, violations);
        if (!violations.isEmpty()) {
            throw new InvalidQueryException(violations);
        }

        Set<String> concreteTypes = TypeHierarchy.concreteTypeAndSubtypes(schema, resolved.iri());
        if (concreteTypes.isEmpty()) {
            return new QueryResult(List.of(), 0, Map.of());
        }

        Query spiQuery =
                new Query(concreteTypes, criteria, query.text(), sorts, query.page(), facetIris, false, resolved.iri());
        QueryResult result = browsePort.browse(scope, schema, spiQuery);
        List<BusinessObject> stored =
                result.items().stream().map(ObjectViewMapper::toBusinessObject).toList();
        List<ObjectView> derived = derivationPlanner.apply(scope, schema, behaviour, resolved, stored).stream()
                .map(ObjectViewMapper::toView)
                .toList();
        return new QueryResult(derived, result.total(), result.facets());
    }

    @Override
    public ObjectView read(Scope scope, String type, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        TypeDefinition resolved = BusinessObjectGuards.resolveType(schema, type);
        BusinessObjectGuards.authorize(authorizationPort, scope, Operation.READ, resolved.iri());

        ObjectView found = readPort.read(scope, new ReadRequest(id)).orElseThrow(() -> new ObjectNotFoundException(id));
        BusinessObjectGuards.requireMatchingType(schema, found.type(), resolved, id);
        BusinessObject stored = ObjectViewMapper.toBusinessObject(found);
        return ObjectViewMapper.toView(derivationPlanner
                .apply(scope, schema, halves.behaviour(), resolved, List.of(stored))
                .get(0));
    }

    /**
     * Builds the property lookup a {@link BrowseQuery}'s filter/sort/facet property names resolve
     * against: every property (by both IRI and short name) attributed to {@code typeIri} or any of
     * its transitive subtypes — a deliberately wider set than {@link
     * TypeHierarchy#concreteTypeAndSubtypes}, since a filter/sort/facet property declared only on a
     * concrete subtype must still resolve when browsing its abstract supertype.
     */
    private Map<String, PropertyDefinition> propertyIndex(Schema schema, String typeIri) {
        Map<String, PropertyDefinition> index = new HashMap<>();
        for (TypeRef typeRef : TypeHierarchy.typeAndSubtypes(schema, typeIri)) {
            TypeDefinition type = schema.type(typeRef.iri()).orElseThrow();
            for (PropertyDefinition property : type.properties()) {
                index.put(property.iri(), property);
                index.put(shortName(property.iri()), property);
            }
        }
        return index;
    }

    /**
     * Resolves a filter/sort/facet property reference (short name or full IRI) against {@code
     * index}, rejecting an unknown property, a multi-valued one — every one of the three query
     * clauses this method backs requires a scalar property, per phase-3's explicit scope — or a
     * derived property whose rule is not materialised, since only a materialised rule's value ever
     * reaches {@code sq_object.props} for {@code BrowsePort} to filter, sort, or facet on. Records a
     * {@link Violation} and returns {@code null} on any failure, rather than throwing, so a
     * single {@link BrowseQuery} can report every violation it contains at once.
     */
    private PropertyDefinition resolveQueryableProperty(
            String nameOrIri,
            Behaviour behaviour,
            Map<String, PropertyDefinition> index,
            List<Violation> violations,
            String kind) {
        PropertyDefinition property = index.get(nameOrIri);
        if (property == null) {
            violations.add(new Violation(nameOrIri, "Unknown property '" + nameOrIri + "'"));
            return null;
        }
        if (!isScalar(property)) {
            violations.add(new Violation(
                    property.iri(),
                    "Property '" + shortName(property.iri()) + "' is multi-valued and cannot be used in a " + kind));
            return null;
        }
        Optional<DerivationRule> derivation = behaviour.derivation(property.iri());
        if (derivation.isPresent() && !derivation.get().materialised()) {
            violations.add(new Violation(
                    property.iri(),
                    "Property '" + shortName(property.iri()) + "' is a derived property that is not materialised "
                            + "and cannot be used in a " + kind));
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
            Behaviour behaviour,
            Map<String, PropertyDefinition> index,
            List<Violation> violations) {
        List<Criterion> criteria = new ArrayList<>();
        for (BrowseQuery.Filter filter : filters) {
            PropertyDefinition property =
                    resolveQueryableProperty(filter.property(), behaviour, index, violations, "filter");
            if (property == null) {
                continue;
            }
            Optional<Operator> operator = parseOperatorToken(filter.operator());
            if (operator.isEmpty()) {
                violations.add(new Violation(filter.property(), "Unknown filter operator '" + filter.operator() + "'"));
                continue;
            }
            if (!operatorApplicable(operator.get(), property)) {
                violations.add(new Violation(
                        property.iri(),
                        "Operator '" + filter.operator() + "' is not applicable to property '"
                                + shortName(property.iri()) + "'"));
                continue;
            }
            Optional<Value> value = buildCriterionValue(property, operator.get(), filter.rawValue(), violations);
            criteria.add(new Criterion(property.iri(), operator.get(), value));
        }
        return criteria;
    }

    private Optional<Value> buildCriterionValue(
            PropertyDefinition property, Operator operator, Optional<String> rawValue, List<Violation> violations) {
        if (operator == Operator.IS_NULL || operator == Operator.NOT_NULL) {
            return Optional.empty();
        }
        if (rawValue.isEmpty()) {
            violations.add(new Violation(property.iri(), "value required for operator " + operator));
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
                    violations.add(new Violation(property.iri(), "IN operator candidate value must not be blank"));
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
    private Optional<Value> coerceFilterScalar(PropertyDefinition property, String raw, List<Violation> violations) {
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
            Behaviour behaviour,
            Map<String, PropertyDefinition> index,
            List<Violation> violations) {
        List<Sort> sorts = new ArrayList<>();
        for (BrowseQuery.SortKey sortKey : sortKeys) {
            PropertyDefinition property =
                    resolveQueryableProperty(sortKey.property(), behaviour, index, violations, "sort");
            if (property == null) {
                continue;
            }
            Optional<Direction> direction = parseDirectionToken(sortKey.direction());
            if (direction.isEmpty()) {
                violations.add(
                        new Violation(sortKey.property(), "Unknown sort direction '" + sortKey.direction() + "'"));
                continue;
            }
            sorts.add(new Sort(property.iri(), direction.get()));
        }
        return sorts;
    }

    private List<String> buildFacets(
            List<String> facets,
            Behaviour behaviour,
            Map<String, PropertyDefinition> index,
            List<Violation> violations) {
        List<String> facetIris = new ArrayList<>();
        for (String nameOrIri : facets) {
            PropertyDefinition property = resolveQueryableProperty(nameOrIri, behaviour, index, violations, "facet");
            if (property == null) {
                continue;
            }
            if (!property.facet()) {
                violations.add(new Violation(
                        property.iri(), "Property '" + shortName(property.iri()) + "' is not a facet property"));
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
                property instanceof AttributeDefinition attribute && attribute.datatype() == Datatype.STRING;
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
}
