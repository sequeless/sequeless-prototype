package org.sequeless.app.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;

/**
 * Maps facet requests to released {@link FieldPath}s, and released facet buckets back to the prototype's, keyed by
 * property IRI. The released result keys its facets by path; for a direct property the path encodes to the IRI
 * ({@link FieldKey#encode}), so the prototype's keys are unchanged.
 */
final class FacetMapper {

    private FacetMapper() {}

    /**
     * @param facetProperties the property IRIs to facet on; must not be {@code null}
     * @param scope the scope resolved for the browse's root class; must not be {@code null}
     * @return one released path per IRI, in the same order
     */
    static List<FieldPath> toReleased(List<String> facetProperties, FieldScope scope) {
        return facetProperties.stream()
                .map(propertyIri -> DirectFieldPaths.of(scope, propertyIri))
                .toList();
    }

    /**
     * @param facets the released facet buckets, keyed by path; must not be {@code null}
     * @return the prototype's facet buckets, keyed by property IRI
     */
    static Map<String, List<org.sequeless.spi.query.FacetBucket>> toPrototype(
            Map<FieldPath, List<org.sequeless.query.api.FacetBucket>> facets) {
        Map<String, List<org.sequeless.spi.query.FacetBucket>> result = new LinkedHashMap<>();
        facets.forEach((path, buckets) -> result.put(
                FieldKey.encode(path),
                buckets.stream()
                        .map(bucket -> new org.sequeless.spi.query.FacetBucket(bucket.value(), bucket.count()))
                        .toList()));
        return result;
    }
}
