package org.sequeless.app.rest;

import java.util.List;
import java.util.Map;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.query.QueryResult;

/**
 * JSON response body for {@code GET /objects/{type}}.
 *
 * <p>{@code page} — not {@code number} — is the field name, a deliberate wire-vs-SPI divergence:
 * {@code org.sequeless.spi.object.Page} uses {@code number}, but plan.md §8's wire shape
 * explicitly spells this field {@code page}, and this record keeps that same convention from
 * {@link PageResultResponse}, the plain-paging shape this record replaces now that {@code GET
 * /objects/{type}} also filters, sorts, free-text searches, and facets.
 *
 * @param items the objects on this page
 * @param page the 0-based page number this result answers
 * @param size the page size this result was computed with (after the 200 cap)
 * @param totalItems the total number of items across every page
 * @param totalPages the total number of pages needed to hold {@code totalItems} at {@code size}
 *     each, rounded up
 * @param facets the computed facet buckets requested via {@code facets=}, keyed by property short
 *     name (translated from {@link QueryResult#facets()}'s IRI keys by {@link
 *     FacetResponseMapper#toShortNameKeyed}); empty if no {@code facets=} parameter was given
 */
public record QueryResultResponse(
        List<BusinessObjectResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        Map<String, List<FacetBucketResponse>> facets) {

    /**
     * @param result the use case's result: the matching page of objects, the total match count,
     *     and the computed facet buckets; must not be {@code null}
     * @param requestType the resolved {@link TypeDefinition} for the current request's {@code
     *     type} path variable, used to resolve property IRIs (on both items and facets) to short
     *     names; must not be {@code null}
     * @param page the 0-based page number the request asked for
     * @param size the page size actually used (after the 200 cap)
     * @return the {@link QueryResultResponse} rendering of {@code result}
     */
    static QueryResultResponse from(QueryResult result, TypeDefinition requestType, int page, int size) {
        List<BusinessObjectResponse> items =
                result.items().stream()
                        .map(object -> ObjectPropertyMapper.toResponse(object, requestType))
                        .toList();
        long totalItems = result.total();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        Map<String, List<FacetBucketResponse>> facetsByShortName =
                FacetResponseMapper.toShortNameKeyed(result.facets(), requestType);
        return new QueryResultResponse(items, page, size, totalItems, totalPages, facetsByShortName);
    }
}
