package org.sequeless.testkit.query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.FacetBucket;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;

/**
 * An in-memory {@link BrowsePort}, the double {@link BrowseContract} is proven against. It is a
 * real query engine over {@link InMemoryAggregateObjectStorePort#everyObject(Scope)}: it applies
 * the tenant and soft-delete filters, the union of types, every operator, the sort and default
 * order, the text search and its ranking, the "conventional multi-select" facet rule, and paging,
 * each as {@link BrowsePort}'s javadoc describes them. Nothing here is faked to pass a clause.
 *
 * <p>Text search follows the Postgres adapter's {@code plainto_tsquery} semantics: every query
 * token must occur in the object's searchable text (an AND), and ranking is the number of token
 * occurrences, best first, tie-broken by {@code (createdAt, id)}. Reference facet buckets are
 * labelled by the target's display-label property, and a target that is missing, soft-deleted, or
 * of another tenant contributes no bucket, matching the Postgres adapter's inner join.
 *
 * <p>Ids, and reference values used as sort keys, compare in {@link BrowsePort}'s unsigned order
 * (see {@code compareIds}), never by the signed {@link UUID#compareTo}.
 */
final class InMemoryBrowsePort implements BrowsePort {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Comparator<BusinessObject> CREATED_AT_THEN_ID = Comparator.comparing(
                    (BusinessObject object) -> object.audit().createdAt())
            .thenComparing(object -> object.id().value(), InMemoryBrowsePort::compareIds);

    private static final Comparator<BusinessObject> ID =
            Comparator.comparing(object -> object.id().value(), InMemoryBrowsePort::compareIds);

    private final InMemoryAggregateObjectStorePort store;

    InMemoryBrowsePort(InMemoryAggregateObjectStorePort store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public QueryResult browse(Scope scope, Schema schema, Query query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(query, "query must not be null");

        List<BusinessObject> matching = matching(scope, schema, query, Optional.empty());
        List<BusinessObject> ordered = new ArrayList<>(matching);
        ordered.sort(comparatorFor(schema, query));

        long from = (long) query.page().number() * query.page().size();
        int start = (int) Math.min(from, ordered.size());
        int end = (int) Math.min(from + query.page().size(), ordered.size());

        Map<String, List<FacetBucket>> facets = new LinkedHashMap<>();
        for (String facetProperty : query.facetProperties()) {
            facets.put(facetProperty, facetBuckets(scope, schema, query, facetProperty));
        }
        List<ObjectView> items =
                ordered.subList(start, end).stream().map(ObjectViews::of).toList();
        return new QueryResult(items, matching.size(), facets);
    }

    /**
     * Every object that the query's filters admit, ignoring any criterion on {@code excludingProperty}
     * (the conventional multi-select rule for facet counts).
     */
    private List<BusinessObject> matching(Scope scope, Schema schema, Query query, Optional<String> excludingProperty) {
        List<String> queryTokens = queryTokens(query);
        List<BusinessObject> result = new ArrayList<>();
        for (BusinessObject object : store.everyObject(scope)) {
            if (!query.types().contains(object.type().iri())) {
                continue;
            }
            if (object.deleted() && !query.includeDeleted()) {
                continue;
            }
            if (!satisfies(object, query.criteria(), excludingProperty)) {
                continue;
            }
            if (query.text().isPresent() && textScore(object, schema, queryTokens) < 0) {
                continue;
            }
            result.add(object);
        }
        return result;
    }

    private static Comparator<BusinessObject> comparatorFor(Schema schema, Query query) {
        if (!query.sorts().isEmpty()) {
            Comparator<BusinessObject> bySorts = null;
            for (Sort sort : query.sorts()) {
                Comparator<BusinessObject> next = sortComparator(sort);
                bySorts = bySorts == null ? next : bySorts.thenComparing(next);
            }
            return bySorts.thenComparing(ID);
        }
        if (query.text().isPresent()) {
            List<String> queryTokens = queryTokens(query);
            return Comparator.comparingLong((BusinessObject object) -> textScore(object, schema, queryTokens))
                    .reversed()
                    .thenComparing(CREATED_AT_THEN_ID);
        }
        return CREATED_AT_THEN_ID;
    }

    /** Ascending puts nulls last and descending puts them first, matching the Postgres adapter. */
    private static Comparator<BusinessObject> sortComparator(Sort sort) {
        PropertyRef property = new PropertyRef(sort.property());
        Comparator<Value> values = Comparator.nullsLast(InMemoryBrowsePort::compareValues);
        Comparator<BusinessObject> ascending =
                Comparator.comparing(object -> object.properties().get(property), values);
        return sort.direction() == Direction.ASC ? ascending : ascending.reversed();
    }

    private List<FacetBucket> facetBuckets(Scope scope, Schema schema, Query query, String facetProperty) {
        PropertyDefinition declared = propertyIndex(schema, query.types()).get(facetProperty);
        Map<ObjectId, BusinessObject> live = store.everyObject(scope).stream()
                .filter(object -> !object.deleted())
                .collect(Collectors.toMap(BusinessObject::id, Function.identity()));

        Map<String, Long> counts = new HashMap<>();
        for (BusinessObject object : matching(scope, schema, query, Optional.of(facetProperty))) {
            Value value = object.properties().get(new PropertyRef(facetProperty));
            if (value == null) {
                continue;
            }
            Optional<String> bucket = declared instanceof RelationshipDefinition relationship
                    ? referenceLabel(schema, live, value, relationship)
                    : Optional.of(lexical(value));
            bucket.ifPresent(label -> counts.merge(label, 1L, Long::sum));
        }
        return counts.entrySet().stream()
                .map(entry -> new FacetBucket(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong((FacetBucket bucket) -> bucket.count())
                        .reversed()
                        .thenComparing(FacetBucket::value))
                .toList();
    }

    private static Optional<String> referenceLabel(
            Schema schema, Map<ObjectId, BusinessObject> live, Value value, RelationshipDefinition relationship) {
        if (!(value instanceof ReferenceValue reference)) {
            return Optional.empty();
        }
        BusinessObject target = live.get(reference.target());
        if (target == null) {
            return Optional.empty();
        }
        Optional<AttributeDefinition> label = displayLabel(schema, relationship.targetTypeIri());
        if (label.isEmpty()) {
            return Optional.of(reference.target().value().toString());
        }
        Value labelValue = target.properties().get(new PropertyRef(label.get().iri()));
        return labelValue == null ? Optional.empty() : Optional.of(lexical(labelValue));
    }

    private static boolean satisfies(
            BusinessObject object, List<Criterion> criteria, Optional<String> excludingProperty) {
        for (Criterion criterion : criteria) {
            if (excludingProperty.isPresent() && excludingProperty.get().equals(criterion.property())) {
                continue;
            }
            if (!matches(object.properties().get(new PropertyRef(criterion.property())), criterion)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Value actual, Criterion criterion) {
        return switch (criterion.operator()) {
            case EQ -> actual != null && sameValue(actual, expected(criterion));
            case NE -> actual == null || !sameValue(actual, expected(criterion));
            case IN ->
                actual != null
                        && ((ListValue) expected(criterion))
                                .values().stream().anyMatch(candidate -> sameValue(actual, candidate));
            case LT -> actual != null && compareValues(actual, expected(criterion)) < 0;
            case LTE -> actual != null && compareValues(actual, expected(criterion)) <= 0;
            case GT -> actual != null && compareValues(actual, expected(criterion)) > 0;
            case GTE -> actual != null && compareValues(actual, expected(criterion)) >= 0;
            case CONTAINS ->
                actual instanceof TextValue text && lower(text.value()).contains(lower(needle(criterion)));
            case STARTS_WITH ->
                actual instanceof TextValue text && lower(text.value()).startsWith(lower(needle(criterion)));
            case IS_NULL -> actual == null;
            case NOT_NULL -> actual != null;
        };
    }

    private static long textScore(BusinessObject object, Schema schema, List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return -1;
        }
        List<String> document = searchableTokens(object, schema);
        long score = 0;
        for (String token : queryTokens) {
            long occurrences = document.stream().filter(token::equals).count();
            if (occurrences == 0) {
                return -1;
            }
            score += occurrences;
        }
        return score;
    }

    private static List<String> searchableTokens(BusinessObject object, Schema schema) {
        List<String> tokens = new ArrayList<>();
        for (PropertyDefinition property : propertiesOf(schema, object.type().iri())) {
            if (property.searchable()
                    && object.properties().get(new PropertyRef(property.iri())) instanceof TextValue text) {
                tokens.addAll(tokens(text.value()));
            }
        }
        return tokens;
    }

    private static List<String> queryTokens(Query query) {
        return query.text().map(InMemoryBrowsePort::tokens).orElse(List.of());
    }

    private static List<String> tokens(String text) {
        return Arrays.stream(NON_WORD.split(lower(text)))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private static List<PropertyDefinition> propertiesOf(Schema schema, String typeIri) {
        return schema.type(typeIri).map(TypeDefinition::properties).orElse(List.of());
    }

    private static Map<String, PropertyDefinition> propertyIndex(Schema schema, Set<String> typeIris) {
        Map<String, PropertyDefinition> index = new HashMap<>();
        for (String typeIri : typeIris) {
            for (PropertyDefinition property : propertiesOf(schema, typeIri)) {
                index.putIfAbsent(property.iri(), property);
            }
        }
        return index;
    }

    private static Optional<AttributeDefinition> displayLabel(Schema schema, String typeIri) {
        return propertiesOf(schema, typeIri).stream()
                .filter(PropertyDefinition::displayLabel)
                .filter(AttributeDefinition.class::isInstance)
                .map(AttributeDefinition.class::cast)
                .findFirst();
    }

    private static Value expected(Criterion criterion) {
        return criterion
                .value()
                .orElseThrow(() -> new IllegalArgumentException(criterion.operator() + " needs a value"));
    }

    private static String needle(Criterion criterion) {
        return ((TextValue) expected(criterion)).value();
    }

    private static boolean sameValue(Value actual, Value expected) {
        if (isNumeric(actual) && isNumeric(expected)) {
            return numeric(actual).compareTo(numeric(expected)) == 0;
        }
        return actual.equals(expected);
    }

    private static int compareValues(Value left, Value right) {
        if (isNumeric(left) && isNumeric(right)) {
            return numeric(left).compareTo(numeric(right));
        }
        if (left instanceof TextValue leftText && right instanceof TextValue rightText) {
            return leftText.value().compareTo(rightText.value());
        }
        if (left instanceof DateValue leftDate && right instanceof DateValue rightDate) {
            return leftDate.value().compareTo(rightDate.value());
        }
        if (left instanceof DateTimeValue leftInstant && right instanceof DateTimeValue rightInstant) {
            return leftInstant.value().compareTo(rightInstant.value());
        }
        if (left instanceof BoolValue leftBool && right instanceof BoolValue rightBool) {
            return Boolean.compare(leftBool.value(), rightBool.value());
        }
        if (left instanceof ReferenceValue leftRef && right instanceof ReferenceValue rightRef) {
            return compareIds(leftRef.target().value(), rightRef.target().value());
        }
        throw new IllegalArgumentException("Cannot order " + left + " against " + right);
    }

    /** BrowsePort's id order: unsigned, most significant half first. */
    private static int compareIds(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private static boolean isNumeric(Value value) {
        return value instanceof IntegerValue || value instanceof DecimalValue;
    }

    private static BigDecimal numeric(Value value) {
        return switch (value) {
            case IntegerValue integer -> BigDecimal.valueOf(integer.value());
            case DecimalValue decimal -> decimal.value();
            default -> throw new IllegalArgumentException("Not a numeric value: " + value);
        };
    }

    private static String lexical(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> Long.toString(integer.value());
            case DecimalValue decimal -> decimal.value().toPlainString();
            case BoolValue bool -> Boolean.toString(bool.value());
            case DateValue date -> date.value().toString();
            case DateTimeValue dateTime -> dateTime.value().toString();
            case ReferenceValue reference -> reference.target().value().toString();
            case ListValue list ->
                throw new UnsupportedOperationException(
                        "Facets over list-valued properties are not exercised by BrowseContract");
        };
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
