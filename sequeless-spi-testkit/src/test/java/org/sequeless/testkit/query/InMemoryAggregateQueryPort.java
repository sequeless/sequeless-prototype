package org.sequeless.testkit.query;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;

/**
 * A minimal {@link QueryPort} test double, existing purely to prove {@link AggregateContract}
 * itself is correct before T7's real Postgres implementation extends it. {@link #query} and
 * {@link #ensureIndexes} are not exercised by {@link AggregateContract} — that is {@link
 * QueryContract}'s job, and this module has no in-memory {@code QueryPort} implementation
 * complete enough to pass that broader contract — so they are left unimplemented here rather than
 * faked into something that would silently pass a contract this double was never meant to satisfy.
 *
 * <p>{@link #aggregate} is implemented honestly against {@link InMemoryAggregateObjectStorePort}'s
 * {@link InMemoryAggregateObjectStorePort#snapshot(Scope, java.util.Set)}: it reproduces the exact
 * density rule {@link QueryPort#aggregate} documents (dense, zero-filled {@code COUNT}; {@code
 * SUM}/{@code MIN}/{@code MAX}/{@code AVG} simply absent for a target with no matching source
 * rows), tenant scoping, and soft-delete exclusion — the same semantics a real adapter's contract
 * test must satisfy, so this double cannot mask a bug the Postgres contract IT would otherwise
 * catch.
 */
final class InMemoryAggregateQueryPort implements QueryPort {

    private final InMemoryAggregateObjectStorePort store;

    InMemoryAggregateQueryPort(InMemoryAggregateObjectStorePort store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query) {
        throw new UnsupportedOperationException(
            "query is not exercised by AggregateContract; use QueryContract against a real "
                + "QueryPort implementation for that surface");
    }

    @Override
    public void ensureIndexes(Scope scope, MetaModelSnapshot snapshot) {
        throw new UnsupportedOperationException(
            "ensureIndexes is not exercised by AggregateContract");
    }

    @Override
    public AggregateResult aggregate(Scope scope, MetaModelSnapshot snapshot, AggregateRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(request, "request must not be null");

        List<BusinessObject> sources = store.snapshot(scope, request.sourceTypes());
        PropertyRef via = new PropertyRef(request.viaIri());

        Map<ObjectId, List<BusinessObject>> bySource = new HashMap<>();
        for (BusinessObject source : sources) {
            if (!matchesCriteria(source, request.criteria())) {
                continue;
            }
            Value viaValue = source.properties().get(via);
            if (!(viaValue instanceof ReferenceValue reference)) {
                continue;
            }
            if (!request.targetIds().contains(reference.target())) {
                continue;
            }
            bySource.computeIfAbsent(reference.target(), key -> new java.util.ArrayList<>()).add(source);
        }

        Map<ObjectId, Value> values = new HashMap<>();
        switch (request.function()) {
            case COUNT -> {
                for (ObjectId target : request.targetIds()) {
                    values.put(target, new IntegerValue(bySource.getOrDefault(target, List.of()).size()));
                }
            }
            case SUM -> {
                for (Map.Entry<ObjectId, List<BusinessObject>> entry : bySource.entrySet()) {
                    List<BigDecimal> amounts = amountsOf(entry.getValue(), request.ofPropertyIri());
                    if (amounts.isEmpty()) {
                        continue;
                    }
                    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    values.put(entry.getKey(), new DecimalValue(sum));
                }
            }
            case MIN -> {
                for (Map.Entry<ObjectId, List<BusinessObject>> entry : bySource.entrySet()) {
                    List<BigDecimal> amounts = amountsOf(entry.getValue(), request.ofPropertyIri());
                    amounts.stream()
                        .min(BigDecimal::compareTo)
                        .ifPresent(min -> values.put(entry.getKey(), new DecimalValue(min)));
                }
            }
            case MAX -> {
                for (Map.Entry<ObjectId, List<BusinessObject>> entry : bySource.entrySet()) {
                    List<BigDecimal> amounts = amountsOf(entry.getValue(), request.ofPropertyIri());
                    amounts.stream()
                        .max(BigDecimal::compareTo)
                        .ifPresent(max -> values.put(entry.getKey(), new DecimalValue(max)));
                }
            }
            case AVG -> {
                for (Map.Entry<ObjectId, List<BusinessObject>> entry : bySource.entrySet()) {
                    List<BigDecimal> amounts = amountsOf(entry.getValue(), request.ofPropertyIri());
                    if (amounts.isEmpty()) {
                        continue;
                    }
                    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal average =
                        sum.divide(BigDecimal.valueOf(amounts.size()), 10, java.math.RoundingMode.HALF_UP);
                    values.put(entry.getKey(), new DecimalValue(average));
                }
            }
        }
        return new AggregateResult(values);
    }

    private static List<BigDecimal> amountsOf(List<BusinessObject> sources, Optional<String> ofPropertyIri) {
        String ofIri =
            ofPropertyIri.orElseThrow(
                () -> new IllegalArgumentException("ofPropertyIri required for non-COUNT aggregate"));
        PropertyRef of = new PropertyRef(ofIri);
        List<BigDecimal> amounts = new java.util.ArrayList<>();
        for (BusinessObject source : sources) {
            Value value = source.properties().get(of);
            if (value == null) {
                continue;
            }
            amounts.add(toBigDecimal(value));
        }
        return amounts;
    }

    private static BigDecimal toBigDecimal(Value value) {
        return switch (value) {
            case DecimalValue decimal -> decimal.value();
            case IntegerValue integer -> BigDecimal.valueOf(integer.value());
            default -> throw new IllegalArgumentException("Cannot aggregate non-numeric value: " + value);
        };
    }

    private static boolean matchesCriteria(BusinessObject source, List<Criterion> criteria) {
        for (Criterion criterion : criteria) {
            if (!matches(source, criterion)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(BusinessObject source, Criterion criterion) {
        Value actual = source.properties().get(new PropertyRef(criterion.property()));
        Optional<Value> expected = criterion.value();
        return switch (criterion.operator()) {
            case EQ -> expected.isPresent() && valuesEqual(actual, expected.get());
            case NE -> expected.isPresent() && !valuesEqual(actual, expected.get());
            case IS_NULL -> actual == null;
            case NOT_NULL -> actual != null;
            default ->
                throw new UnsupportedOperationException(
                    "Operator " + criterion.operator() + " is not needed by AggregateContract's "
                        + "scenarios and is not implemented in this test double");
        };
    }

    private static boolean valuesEqual(Value actual, Value expected) {
        if (actual instanceof TextValue actualText && expected instanceof TextValue expectedText) {
            return actualText.value().equals(expectedText.value());
        }
        return Objects.equals(actual, expected);
    }
}
