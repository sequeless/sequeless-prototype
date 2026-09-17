package org.sequeless.spi.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.spi.object.BusinessObject;

/**
 * The result of a single {@link QueryPort#query} call: the matching objects on the requested
 * page, the total number of matches across every page, and the computed facet buckets.
 *
 * @param items the objects on the requested page; must not be {@code null}; may be empty;
 *     returned as an unmodifiable copy so callers cannot mutate this result after construction
 * @param total the total number of matching objects across every page; must not be negative
 * @param facets the computed facet buckets, keyed by property IRI matching the IRIs in the
 *     originating {@link Query#facetProperties()}; must not be {@code null}; returned as an
 *     unmodifiable copy so callers cannot mutate this result's key set after construction — but
 *     each value list is expected to already be unmodifiable when constructed by an adapter, since
 *     this record does not deep-copy nested lists, matching the rest of the SPI's shallow-copy
 *     convention for nested collections
 */
public record QueryResult(List<BusinessObject> items, long total, Map<String, List<FacetBucket>> facets) {

    public QueryResult {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("QueryResult total must not be negative");
        }
        Objects.requireNonNull(facets, "facets must not be null");
        facets = Map.copyOf(facets);
    }
}
