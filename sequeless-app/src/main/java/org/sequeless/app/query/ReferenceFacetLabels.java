package org.sequeless.app.query;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldType;

/**
 * Turns a reference facet's buckets from target ids into the target's display label. The prototype's facet contract
 * ({@code spi.query.FacetBucket}) requires that value, and the released facet returns the raw target id, so the host
 * translates at this boundary.
 *
 * <p>For each reference facet whose target type declares a display label, every bucket is read through the released
 * {@link QueryService#read}: its label is taken from that property, and buckets that share a label are summed, which
 * is how the prototype's query grouped them. A bucket whose target is absent (deleted or another tenant) or has no
 * label is dropped, as the prototype's query dropped it. A reference facet whose target type declares no label keeps
 * the target id as text, the prototype's documented fallback. Non-reference facets pass through unchanged.
 *
 * <p>This costs one read per distinct target in the browse, across all its reference facets: a target that two facets
 * share is read once. The released query has no join to do it in one statement.
 */
final class ReferenceFacetLabels {

    private final QueryService queryService;

    /**
     * @param queryService the released query service, used for the reads; must not be {@code null}
     */
    ReferenceFacetLabels(QueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * @param scope the browse's scope; must not be {@code null}
     * @param schema the prototype schema, for the target types' display labels; must not be {@code null}
     * @param fieldScope the scope the browse resolved, for each facet's field type; must not be {@code null}
     * @param facets the released facet buckets, keyed by path; must not be {@code null}
     * @return the same facets, with each reference facet's buckets keyed by display label
     */
    Map<FieldPath, List<FacetBucket>> apply(
            Scope scope, Schema schema, FieldScope fieldScope, Map<FieldPath, List<FacetBucket>> facets) {
        Map<FieldPath, List<FacetBucket>> result = new LinkedHashMap<>();
        Map<ObjectId, Optional<ObjectView>> views = new HashMap<>();
        facets.forEach((path, buckets) -> {
            Optional<String> labelIri = displayLabelOf(path, fieldScope, schema);
            result.put(path, labelIri.isEmpty() ? buckets : labelled(scope, labelIri.get(), buckets, views));
        });
        return result;
    }

    private static Optional<String> displayLabelOf(FieldPath path, FieldScope fieldScope, Schema schema) {
        boolean reference = fieldScope.facets().stream()
                .anyMatch(field -> field.path().equals(path) && field.type() == FieldType.OBJECT_REF);
        if (!reference) {
            return Optional.empty();
        }
        ClassRef target = path.segments().get(0).targetClass();
        if (target == null) {
            return Optional.empty();
        }
        return schema.type(target.iri())
                .flatMap(type -> type.properties().stream()
                        .filter(PropertyDefinition::displayLabel)
                        .filter(AttributeDefinition.class::isInstance)
                        .map(PropertyDefinition::iri)
                        .findFirst());
    }

    private List<FacetBucket> labelled(
            Scope scope, String labelIri, List<FacetBucket> buckets, Map<ObjectId, Optional<ObjectView>> views) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FacetBucket bucket : buckets) {
            labelOf(scope, labelIri, bucket.value(), views)
                    .ifPresent(label -> counts.merge(label, bucket.count(), Long::sum));
        }
        return counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue()
                        .reversed()
                        .thenComparing(java.util.Map.Entry.<String, Long>comparingByKey()))
                .map(entry -> new FacetBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Optional<String> labelOf(
            Scope scope, String labelIri, String targetId, Map<ObjectId, Optional<ObjectView>> views) {
        ObjectId id = new ObjectId(UUID.fromString(targetId));
        return views.computeIfAbsent(id, key -> queryService.read(scope, new ReadRequest(key)))
                .flatMap(view -> Optional.ofNullable(view.properties().get(new PropertyRef(labelIri))))
                .flatMap(ReferenceFacetLabels::lexical);
    }

    /** The text a display label reads as; a reference or a list has no label text. */
    private static Optional<String> lexical(Value value) {
        return switch (value) {
            case TextValue text -> Optional.of(text.value());
            case IntegerValue integer -> Optional.of(Long.toString(integer.value()));
            case DecimalValue decimal -> Optional.of(decimal.value().toPlainString());
            case BoolValue bool -> Optional.of(Boolean.toString(bool.value()));
            case DateValue date -> Optional.of(date.value().toString());
            case DateTimeValue dateTime -> Optional.of(dateTime.value().toString());
            case ReferenceValue reference -> Optional.empty();
            case ListValue list -> Optional.empty();
        };
    }
}
