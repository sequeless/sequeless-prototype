package org.sequeless.adapter.persistence.postgres;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.FacetBucket;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;

/**
 * The PostgreSQL-backed {@link QueryPort}: filtered, sorted, paged, faceted, and free-text queries
 * over {@code sq_object}'s tagged JSONB {@code props} column, sharing the same {@link JdbcClient}
 * wiring convention as {@link PostgresObjectStore}. See {@code plan.md} "PostgreSQL adapter design"
 * for the tagged-JSONB extraction/cast table this class implements, and {@link QueryPort}'s
 * interface-level javadoc for the full behavioural contract ({@code QueryContract} asserts every
 * clause of it mechanically).
 *
 * <p>{@link #ensureIndexes} is implemented by a later task ([T6]); this class is not yet a
 * complete {@link QueryPort} adapter until that lands.
 */
public final class PostgresQueryStore implements QueryPort {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcClient the client to run reads through; must not be {@code null}
     * @param transactionManager the transaction manager backing {@link #transactionTemplate},
     *     mirroring {@link PostgresObjectStore}'s constructor even though {@link #query} itself
     *     does not currently need a transaction; must not be {@code null}
     */
    public PostgresQueryStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(query, "query must not be null");

        String tenantId = scope.tenantId().value();
        Map<String, PropertyDefinition> propertyIndex = indexProperties(snapshot, query.types());

        WhereClause mainWhere =
            buildWhere(
                tenantId, query.types(), query.criteria(), query.includeDeleted(), Optional.empty(),
                query.text(), "", propertyIndex);

        long total =
            jdbcClient
                .sql("SELECT COUNT(*) FROM sq_object WHERE " + mainWhere.sql())
                .params(mainWhere.params())
                .query(Long.class)
                .single();

        SqlFragment orderBy = buildOrderBy(query.sorts(), query.text(), propertyIndex);

        Map<String, Object> itemParams = new HashMap<>(mainWhere.params());
        itemParams.putAll(orderBy.params());
        itemParams.put("limit", query.page().size());
        itemParams.put("offset", query.page().number() * query.page().size());

        List<BusinessObject> items =
            jdbcClient
                .sql(
                    "SELECT id, tenant_id, type_iri, version, state, props, created_at, created_by, "
                        + "updated_at, updated_by, deleted_at FROM sq_object WHERE "
                        + mainWhere.sql()
                        + " ORDER BY "
                        + orderBy.sql()
                        + " LIMIT :limit OFFSET :offset")
                .params(itemParams)
                .query(PostgresObjectStore::mapRow)
                .list();

        Map<String, List<FacetBucket>> facets = new LinkedHashMap<>();
        for (String facetProperty : query.facetProperties()) {
            facets.put(
                facetProperty, computeFacet(tenantId, query, facetProperty, propertyIndex, snapshot));
        }

        return new QueryResult(items, total, facets);
    }

    @Override
    public void ensureIndexes(Scope scope, MetaModelSnapshot snapshot) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        // Implemented by a later task ([T6]): registers sq:indexed properties in
        // sq_index_registry and creates their expression indexes, and registers sq:searchable
        // properties in sq_searchable_property with a search_vector backfill. Left unimplemented
        // here so this class compiles as a complete QueryPort while T5 scopes only #query.
        throw new UnsupportedOperationException(
            "PostgresQueryStore.ensureIndexes is not yet implemented; see [T6]");
    }

    // -- Property resolution -----------------------------------------------------------------

    private static Map<String, PropertyDefinition> indexProperties(
        MetaModelSnapshot snapshot, Set<String> typeIris) {
        Map<String, PropertyDefinition> result = new LinkedHashMap<>();
        for (String typeIri : typeIris) {
            snapshot
                .type(typeIri)
                .ifPresent(
                    type -> type.properties().forEach(p -> result.putIfAbsent(p.iri(), p)));
        }
        return result;
    }

    // -- WHERE clause building ---------------------------------------------------------------

    private record WhereClause(String sql, Map<String, Object> params) {}

    private record SqlFragment(String sql, Map<String, Object> params) {}

    /**
     * Builds {@code <alias>tenant_id = :tenantId AND <alias>type_iri IN (:types) [AND
     * <alias>deleted_at IS NULL] [AND <criterion SQL> ...] [AND <alias>search_vector @@
     * plainto_tsquery('simple', :searchText)]}.
     *
     * @param excludingProperty a criterion's property IRI to drop from {@code criteria} (a facet's
     *     own property, for multi-select semantics), or {@link Optional#empty()} for the main query
     * @param tableAlias {@code ""} for a plain query, {@code "o."} for the reference-facet join
     */
    private WhereClause buildWhere(
        String tenantId,
        Set<String> typeIris,
        List<Criterion> criteria,
        boolean includeDeleted,
        Optional<String> excludingProperty,
        Optional<String> text,
        String tableAlias,
        Map<String, PropertyDefinition> propertyIndex) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append(tableAlias).append("tenant_id = :tenantId AND ").append(tableAlias)
            .append("type_iri IN (:types)");
        params.put("tenantId", tenantId);
        params.put("types", List.copyOf(typeIris));

        if (!includeDeleted) {
            sql.append(" AND ").append(tableAlias).append("deleted_at IS NULL");
        }

        int i = 0;
        for (Criterion criterion : criteria) {
            if (excludingProperty.isPresent() && excludingProperty.get().equals(criterion.property())) {
                continue;
            }
            String propParam = "wp" + i;
            String valueParamPrefix = "wv" + i;
            i++;

            PropertyDefinition property = propertyIndex.get(criterion.property());
            String expr = castExpression(property, tableAlias, propParam);
            params.put(propParam, criterion.property());

            sql.append(" AND ").append(criterionSql(criterion, expr, valueParamPrefix, params));
        }

        if (text.isPresent()) {
            sql.append(" AND ").append(tableAlias)
                .append("search_vector @@ plainto_tsquery('simple', :searchText)");
            params.put("searchText", text.get());
        }

        return new WhereClause(sql.toString(), params);
    }

    private String criterionSql(
        Criterion criterion, String expr, String valueParam, Map<String, Object> params) {
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
                List<Object> values = list.values().stream().map(PostgresQueryStore::toSqlParam).toList();
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

    // -- ORDER BY building --------------------------------------------------------------------

    private static SqlFragment buildOrderBy(
        List<Sort> sorts, Optional<String> text, Map<String, PropertyDefinition> propertyIndex) {
        if (!sorts.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            List<String> parts = new ArrayList<>();
            int i = 0;
            for (Sort sort : sorts) {
                String propParam = "op" + i++;
                params.put(propParam, sort.property());
                String expr = castExpression(propertyIndex.get(sort.property()), "", propParam);
                parts.add(expr + (sort.direction() == Direction.ASC ? " ASC" : " DESC"));
            }
            parts.add("id"); // stable tiebreak
            return new SqlFragment(String.join(", ", parts), params);
        }
        if (text.isPresent()) {
            return new SqlFragment(
                "ts_rank(search_vector, plainto_tsquery('simple', :searchText)) DESC, created_at, id",
                Map.of("searchText", text.get()));
        }
        return new SqlFragment("created_at, id", Map.of());
    }

    // -- Datatype-aware cast expressions -------------------------------------------------------

    /**
     * Query-time cast expression over a bind-parameterized property IRI (used in WHERE/ORDER BY).
     * See {@link #castExpressionLiteral(PropertyDefinition)} for the DDL-time, literal-embedded
     * variant [T6] uses for {@code CREATE INDEX}.
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

    /**
     * DDL-time cast expression with the property IRI embedded as an escaped string literal, for use
     * in a {@code CREATE INDEX ... ON sq_object ((<expr>))} statement, which cannot take bind
     * parameters. Package-private so [T6]'s {@code ensureIndexes} can reuse the same per-datatype
     * switch logic as {@link #castExpression} rather than re-deriving it.
     */
    static String castExpressionLiteral(PropertyDefinition property) {
        String iri = property.iri().replace("'", "''");
        if (property instanceof RelationshipDefinition) {
            return "(props -> '" + iri + "' ->> 'ref')::uuid";
        }
        return switch (((AttributeDefinition) property).datatype()) {
            case STRING, ANY_URI, TIME, DURATION -> "props -> '" + iri + "' ->> 'text'";
            case INTEGER, LONG -> "(props -> '" + iri + "' ->> 'integer')::bigint";
            case DECIMAL, DOUBLE -> "(props -> '" + iri + "' ->> 'decimal')::numeric";
            case BOOLEAN -> "(props -> '" + iri + "' ->> 'bool')::boolean";
            case DATE -> "(props -> '" + iri + "' ->> 'date')::date";
            case DATE_TIME -> "(props -> '" + iri + "' ->> 'dateTime')::timestamptz";
        };
    }

    private static String tagFor(Datatype datatype) {
        return switch (datatype) {
            case STRING, ANY_URI, TIME, DURATION -> "text";
            case INTEGER, LONG -> "integer";
            case DECIMAL, DOUBLE -> "decimal";
            case BOOLEAN -> "bool";
            case DATE -> "date";
            case DATE_TIME -> "dateTime";
        };
    }

    // -- Facet computation --------------------------------------------------------------------

    private List<FacetBucket> computeFacet(
        String tenantId,
        Query query,
        String facetProperty,
        Map<String, PropertyDefinition> propertyIndex,
        MetaModelSnapshot snapshot) {
        PropertyDefinition property = propertyIndex.get(facetProperty);
        return property instanceof RelationshipDefinition rel
            ? computeReferenceFacet(tenantId, query, facetProperty, rel, snapshot, propertyIndex)
            : computeScalarFacet(tenantId, query, facetProperty, (AttributeDefinition) property, propertyIndex);
    }

    private List<FacetBucket> computeScalarFacet(
        String tenantId,
        Query query,
        String facetProperty,
        AttributeDefinition attribute,
        Map<String, PropertyDefinition> propertyIndex) {
        WhereClause excluding =
            buildWhere(
                tenantId, query.types(), query.criteria(), query.includeDeleted(),
                Optional.of(facetProperty), query.text(), "", propertyIndex);

        String tag = tagFor(attribute.datatype());
        String valueExpr = "props -> :facetProp ->> '" + tag + "'";

        String sql =
            "SELECT (" + valueExpr + ") AS bucket_value, COUNT(*) AS bucket_count "
                + "FROM sq_object WHERE "
                + excluding.sql()
                + " AND ("
                + valueExpr
                + ") IS NOT NULL GROUP BY 1 ORDER BY bucket_count DESC";

        Map<String, Object> params = new HashMap<>(excluding.params());
        params.put("facetProp", facetProperty);

        return jdbcClient
            .sql(sql)
            .params(params)
            .query((rs, rowNum) -> new FacetBucket(rs.getString("bucket_value"), rs.getLong("bucket_count")))
            .list();
    }

    private List<FacetBucket> computeReferenceFacet(
        String tenantId,
        Query query,
        String facetProperty,
        RelationshipDefinition rel,
        MetaModelSnapshot snapshot,
        Map<String, PropertyDefinition> propertyIndex) {
        WhereClause excluding =
            buildWhere(
                tenantId, query.types(), query.criteria(), query.includeDeleted(),
                Optional.of(facetProperty), query.text(), "o.", propertyIndex);

        Optional<AttributeDefinition> labelProperty = displayLabelProperty(snapshot, rel.targetTypeIri());
        String valueExpr =
            labelProperty.isPresent()
                ? "target.props -> :labelProp ->> '" + tagFor(labelProperty.get().datatype()) + "'"
                : "target.id::text";

        String sql =
            "SELECT ("
                + valueExpr
                + ") AS bucket_value, COUNT(*) AS bucket_count "
                + "FROM sq_object o "
                + "JOIN sq_object target ON target.id = (o.props -> :facetProp ->> 'ref')::uuid "
                + "AND target.tenant_id = :tenantId AND target.deleted_at IS NULL "
                + "WHERE "
                + excluding.sql()
                + " GROUP BY 1 ORDER BY bucket_count DESC";

        Map<String, Object> params = new HashMap<>(excluding.params());
        params.put("facetProp", facetProperty);
        labelProperty.ifPresent(p -> params.put("labelProp", p.iri()));

        return jdbcClient
            .sql(sql)
            .params(params)
            .query((rs, rowNum) -> new FacetBucket(rs.getString("bucket_value"), rs.getLong("bucket_count")))
            .list();
    }

    private static Optional<AttributeDefinition> displayLabelProperty(
        MetaModelSnapshot snapshot, String targetTypeIri) {
        return snapshot
            .type(targetTypeIri)
            .flatMap(
                type ->
                    type.properties().stream()
                        .filter(PropertyDefinition::displayLabel)
                        .filter(AttributeDefinition.class::isInstance)
                        .map(AttributeDefinition.class::cast)
                        .findFirst());
    }
}
