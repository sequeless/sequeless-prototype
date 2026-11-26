package org.sequeless.adapter.persistence.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.QueryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The PostgreSQL-backed {@link QueryPort}: aggregates over {@code sq_object}'s tagged JSONB {@code props}
 * column, with the same {@link JdbcClient} wiring convention as {@link PostgresObjectStore}. See
 * {@code plan.md} "PostgreSQL adapter design" for the tagged-JSONB extraction/cast table this class
 * implements.
 *
 * <p>Spec 17 P4 deleted this class's browse, read and index preparation methods. The released
 * {@code query-postgres-jsonb-adapter} replaces them: the host's Query contract ({@code ReadPort},
 * {@code BrowsePort}, {@code IndexPreparationPort}) is implemented by the released adapters in
 * {@code sequeless-app}.
 */
public final class PostgresQueryStore implements QueryPort {

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient the client to run aggregates through; must not be {@code null}
     * @param transactionManager checked for {@code null} and kept so the constructor keeps its shape for
     *     the autoconfiguration; the aggregate runs one statement and does not open a transaction
     */
    public PostgresQueryStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    }

    /**
     * Computes {@code request.function()} in one {@code GROUP BY} query over {@code sq_object},
     * reusing {@link #castExpression} and {@link #criterionSql} for the criteria. See {@link QueryPort#aggregate}'s javadoc for the full density contract:
     * {@link AggregateFunction#COUNT} is zero-filled for every requested
     * target id, while {@code SUM}/{@code MIN}/{@code MAX}/{@code AVG} simply omit a target with no
     * matching source rows (a {@code GROUP BY} naturally emits no row for an empty group, so no
     * special-casing is needed for those).
     */
    @Override
    public AggregateResult aggregate(Scope scope, Schema schema, AggregateRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.targetIds().isEmpty()) {
            return new AggregateResult(Map.of());
        }

        String tenantId = scope.tenantId().value();
        Map<String, PropertyDefinition> propertyIndex = indexProperties(schema, request.sourceTypes());

        Optional<AttributeDefinition> ofProperty =
                request.ofPropertyIri().map(iri -> (AttributeDefinition) propertyIndex.get(iri));

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("SELECT (props -> :via ->> 'ref')::uuid AS target_id, ");
        params.put("via", request.viaIri());

        if (request.function() == AggregateFunction.COUNT) {
            sql.append("COUNT(*) AS agg_value ");
        } else {
            AttributeDefinition attribute = ofProperty.orElseThrow(() -> new IllegalArgumentException(
                    "AggregateRequest.ofPropertyIri() is required for " + request.function()));
            String aggExpr = castExpression(attribute, "", "of");
            params.put("of", attribute.iri());
            String aggFunction = switch (request.function()) {
                case SUM -> "SUM";
                case MIN -> "MIN";
                case MAX -> "MAX";
                case AVG -> "AVG";
                case COUNT -> throw new IllegalStateException("unreachable");
            };
            sql.append(aggFunction).append('(').append(aggExpr).append(") AS agg_value ");
        }

        sql.append("FROM sq_object WHERE tenant_id = :tenantId AND type_iri IN (:sourceTypes) "
                + "AND deleted_at IS NULL AND (props -> :via ->> 'ref')::uuid IN (:targetIds)");
        params.put("tenantId", tenantId);
        params.put("sourceTypes", List.copyOf(request.sourceTypes()));
        params.put(
                "targetIds", request.targetIds().stream().map(ObjectId::value).toList());

        int i = 0;
        for (Criterion criterion : request.criteria()) {
            String propParam = "cp" + i;
            String valueParamPrefix = "cv" + i;
            i++;
            PropertyDefinition property = propertyIndex.get(criterion.property());
            String expr = castExpression(property, "", propParam);
            params.put(propParam, criterion.property());
            sql.append(" AND ").append(criterionSql(criterion, expr, valueParamPrefix, params));
        }

        sql.append(" GROUP BY 1");

        if (request.function() == AggregateFunction.COUNT) {
            Map<ObjectId, Long> counts = jdbcClient
                    .sql(sql.toString())
                    .params(params)
                    .query((rs, rowNum) -> Map.entry(objectIdOf(rs), rs.getLong("agg_value")))
                    .list()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<ObjectId, Value> result = new LinkedHashMap<>();
            for (ObjectId targetId : request.targetIds()) {
                result.put(targetId, new IntegerValue(counts.getOrDefault(targetId, 0L)));
            }
            return new AggregateResult(result);
        }

        Datatype datatype = ofProperty.orElseThrow().datatype();
        Map<ObjectId, Value> result = jdbcClient
                .sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> Map.entry(objectIdOf(rs), aggregateValue(rs, datatype)))
                .list()
                .stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

        return new AggregateResult(result);
    }

    private static ObjectId objectIdOf(ResultSet rs) throws SQLException {
        return new ObjectId(rs.getObject("target_id", UUID.class));
    }

    /**
     * Maps {@code rs}'s {@code agg_value} column to a {@link Value} typed per {@code datatype},
     * mirroring the same per-datatype shapes {@link #toSqlParam} and {@link #castExpression} encode
     * for scalar properties elsewhere in this class. Only reachable for {@code
     * SUM}/{@code MIN}/{@code MAX}/{@code AVG} — {@code COUNT} always yields an {@link
     * IntegerValue} regardless of {@code ofPropertyIri}, handled separately in {@link #aggregate}.
     */
    private static Value aggregateValue(ResultSet rs, Datatype datatype) throws SQLException {
        return switch (datatype) {
            case DECIMAL, DOUBLE -> new DecimalValue(rs.getBigDecimal("agg_value"));
            case INTEGER, LONG -> new IntegerValue(rs.getLong("agg_value"));
            case STRING, ANY_URI, TIME, DURATION, BOOLEAN, DATE, DATE_TIME ->
                throw new IllegalArgumentException("Aggregate function is not supported for datatype " + datatype);
        };
    }

    // -- Property resolution -----------------------------------------------------------------

    private static Map<String, PropertyDefinition> indexProperties(Schema schema, Set<String> typeIris) {
        Map<String, PropertyDefinition> result = new LinkedHashMap<>();
        for (String typeIri : typeIris) {
            schema.type(typeIri).ifPresent(type -> type.properties().forEach(p -> result.putIfAbsent(p.iri(), p)));
        }
        return result;
    }

    private String criterionSql(Criterion criterion, String expr, String valueParam, Map<String, Object> params) {
        return switch (criterion.operator()) {
            case EQ -> {
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " = :" + valueParam;
            }
            case NE -> {
                // IS DISTINCT FROM, not <>: a plain <> against a NULL JSONB extraction evaluates
                // to UNKNOWN and silently excludes rows that should match.
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " IS DISTINCT FROM :" + valueParam;
            }
            case IN -> {
                ListValue list = (ListValue) criterion.value().orElseThrow();
                List<Object> values = list.values().stream()
                        .map(PostgresQueryStore::toSqlParam)
                        .toList();
                params.put(valueParam, values);
                yield expr + " IN (:" + valueParam + ")";
            }
            case LT -> {
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " < :" + valueParam;
            }
            case LTE -> {
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " <= :" + valueParam;
            }
            case GT -> {
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " > :" + valueParam;
            }
            case GTE -> {
                params.put(valueParam, toSqlParam(criterion.value().orElseThrow()));
                yield expr + " >= :" + valueParam;
            }
            case CONTAINS -> {
                String text = ((TextValue) criterion.value().orElseThrow()).value();
                params.put(valueParam, "%" + escapeLike(text) + "%");
                yield expr + " ILIKE :" + valueParam + " ESCAPE '\\'";
            }
            case STARTS_WITH -> {
                String text = ((TextValue) criterion.value().orElseThrow()).value();
                params.put(valueParam, escapeLike(text) + "%");
                yield expr + " ILIKE :" + valueParam + " ESCAPE '\\'";
            }
            case IS_NULL -> expr + " IS NULL";
            case NOT_NULL -> expr + " IS NOT NULL";
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Object toSqlParam(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> integer.value();
            case DecimalValue decimal -> decimal.value();
            case BoolValue bool -> bool.value();
            case DateValue date -> date.value();
            case DateTimeValue dateTime -> Timestamp.from(dateTime.value());
            case ReferenceValue ref -> ref.target().value();
            case ListValue list ->
                throw new IllegalStateException(
                        "ListValue must be unwrapped by the caller; only valid nested inside IN");
        };
    }

    // -- Datatype-aware cast expressions -------------------------------------------------------

    /**
     * Query-time cast expression over a bind-parameterized property IRI (used in the aggregate's WHERE).
     */
    private static String castExpression(PropertyDefinition property, String tableAlias, String propParam) {
        String propsCol = tableAlias + "props";
        if (property instanceof RelationshipDefinition) {
            return "(" + propsCol + " -> :" + propParam + " ->> 'ref')::uuid";
        }
        return switch (((AttributeDefinition) property).datatype()) {
            case STRING, ANY_URI, TIME, DURATION -> propsCol + " -> :" + propParam + " ->> 'text'";
            case INTEGER, LONG -> "(" + propsCol + " -> :" + propParam + " ->> 'integer')::bigint";
            case DECIMAL, DOUBLE -> "(" + propsCol + " -> :" + propParam + " ->> 'decimal')::numeric";
            case BOOLEAN -> "(" + propsCol + " -> :" + propParam + " ->> 'bool')::boolean";
            case DATE -> "(" + propsCol + " -> :" + propParam + " ->> 'date')::date";
            case DATE_TIME -> "(" + propsCol + " -> :" + propParam + " ->> 'dateTime')::timestamptz";
        };
    }
}
