package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.query.FacetBucket;

/**
 * Translates a {@link org.sequeless.spi.query.QueryResult#facets()} map — keyed by property IRI —
 * onto {@link QueryResultResponse#facets()}'s short-name-keyed shape. Factored out of {@link
 * ObjectsController}/{@link QueryResultResponse}, mirroring {@link ObjectPropertyMapper}'s own
 * factoring-out.
 *
 * <p>A requested facet property need not be declared directly on {@code requestType} itself: {@code
 * DefaultBusinessObjectService.browse} resolves a {@link org.sequeless.core.api.BrowseQuery}'s
 * facet names against every property attributed to the resolved type <em>or any of its transitive
 * subtypes</em> — a deliberately wider set than {@code requestType.properties()} — so that a facet
 * declared only on a concrete subtype still resolves when browsing an abstract supertype. {@link
 * #toShortNameKeyed} therefore falls back to {@link #shortName} for any facet IRI not found in
 * {@code requestType.properties()}, exactly the same subtype-only fallback {@link
 * ObjectPropertyMapper#toProperties} documents for an object's own properties.
 *
 * <p>Package-private: nothing outside {@code org.sequeless.app.rest} has a reason to map these
 * types directly.
 */
final class FacetResponseMapper {

    private FacetResponseMapper() {}

    /**
     * @param facetsByIri {@link org.sequeless.spi.query.QueryResult#facets()}, keyed by property
     *     IRI; must not be {@code null}
     * @param requestType the resolved {@link TypeDefinition} for the current request's {@code
     *     type} path variable, used to resolve each facet IRI to a short name; must not be {@code
     *     null}
     * @return {@code facetsByIri}, re-keyed by property short name (falling back to the IRI's own
     *     local name for a subtype-only facet property not declared on {@code requestType}
     *     itself), with each {@link FacetBucket} rendered as a {@link FacetBucketResponse}
     */
    static Map<String, List<FacetBucketResponse>> toShortNameKeyed(
            Map<String, List<FacetBucket>> facetsByIri, TypeDefinition requestType) {
        Map<String, String> namesByIri = new HashMap<>();
        for (PropertyDefinition property : requestType.properties()) {
            namesByIri.put(property.iri(), shortName(property.iri()));
        }
        Map<String, List<FacetBucketResponse>> result = new HashMap<>();
        for (Map.Entry<String, List<FacetBucket>> entry : facetsByIri.entrySet()) {
            String iri = entry.getKey();
            String name = namesByIri.getOrDefault(iri, shortName(iri));
            List<FacetBucketResponse> buckets =
                    entry.getValue().stream()
                            .map(bucket -> new FacetBucketResponse(bucket.value(), bucket.count()))
                            .toList();
            result.put(name, buckets);
        }
        return result;
    }

    /**
     * Duplicates {@code MetaModelSnapshot}'s own short-name rule, for the same reason {@link
     * TypeResponseMapper#shortName}, {@link ObjectPropertyMapper#shortName}, and {@link
     * ApiExceptionAdvice#shortName} do.
     *
     * @param iri the IRI to derive a short name from; must not be {@code null}
     * @return the local name after the IRI's last {@code #}, or after its last {@code /} if there
     *     is no {@code #}, or the whole IRI if neither is present
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
