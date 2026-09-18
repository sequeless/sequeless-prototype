package org.sequeless.core.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.object.Page;

/**
 * A request to list objects of a type with filtering, free-text search, sorting, paging, and
 * facet counts — the query shape behind the browse use case (Phase 3, {@code
 * org.sequeless.core.usecase}, out of scope here).
 *
 * <p>Nothing in this record is checked against a {@link org.sequeless.spi.meta.MetaModelSnapshot}:
 * that resolution — is {@code property} a real property on the type, is {@code operator}
 * applicable to its datatype, is a requested facet actually facetable, and so on — is the
 * responsibility of the use case that consumes a {@code BrowseQuery}, which raises {@link
 * InvalidQueryException} when resolution fails. This record only enforces the shape-level
 * invariants (non-null lists, non-blank tokens) that hold regardless of any meta-model.
 *
 * @param filters the filters to apply, ANDed together; must not be {@code null}; may be empty
 * @param text a free-text search term to apply across the type's searchable properties, or {@link
 *     Optional#empty()} for no text search; must not be {@code null} (the wrapper itself)
 * @param sorts the sort keys to apply, in priority order (first is the primary sort); must not be
 *     {@code null}; may be empty
 * @param page the page to return; must not be {@code null}
 * @param facets the properties to compute facet counts for, given as a property short name or
 *     full IRI; must not be {@code null}; may be empty
 */
public record BrowseQuery(
    List<Filter> filters,
    Optional<String> text,
    List<SortKey> sorts,
    Page page,
    List<String> facets) {

    public BrowseQuery {
        Objects.requireNonNull(filters, "filters must not be null");
        filters = List.copyOf(filters);
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(sorts, "sorts must not be null");
        sorts = List.copyOf(sorts);
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(facets, "facets must not be null");
        facets = List.copyOf(facets);
    }

    /**
     * A single filter clause: a property, an operator, and — for operators that need one — a raw
     * value.
     *
     * <p>Neither {@code property} nor {@code operator} is validated against a meta-model here.
     * An unrecognized {@code operator} token, or a {@code property} that does not resolve, becomes
     * an {@link InvalidQueryException} violation raised by the use case that consumes this filter,
     * not a constructor-time crash — the compact constructor below only rejects blank strings.
     *
     * @param property the property to filter on, given as a property short name or full IRI; must
     *     not be blank
     * @param operator a raw operator token — {@code eq}, {@code ne}, {@code in}, {@code lt},
     *     {@code lte}, {@code gt}, {@code gte}, {@code contains}, {@code startswith}, {@code
     *     isnull}, or {@code notnull}, case-insensitive — deliberately not validated against this
     *     list here; must not be blank
     * @param rawValue the raw value to compare against, or {@link Optional#empty()}. Empty for
     *     {@code isnull} and {@code notnull} (a value present there is ignored, not rejected, by
     *     the use case). For {@code in}, a single comma-separated string of candidate values,
     *     split by the use case. For every other operator, the single raw value. Must not be
     *     {@code null} (the wrapper itself)
     */
    public record Filter(String property, String operator, Optional<String> rawValue) {
        public Filter {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("Filter property must not be blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("Filter operator must not be blank");
            }
            Objects.requireNonNull(rawValue, "rawValue must not be null");
        }
    }

    /**
     * A single sort key: a property and a direction.
     *
     * <p>Neither field is validated beyond non-blank here; an unrecognized {@code direction} or a
     * {@code property} that does not resolve becomes an {@link InvalidQueryException} violation
     * raised by the use case that consumes this sort key, not a constructor-time crash.
     *
     * @param property the property to sort on, given as a property short name or full IRI; must
     *     not be blank
     * @param direction {@code "asc"} or {@code "desc"}, case-insensitive, not validated here; must
     *     not be blank
     */
    public record SortKey(String property, String direction) {
        public SortKey {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("SortKey property must not be blank");
            }
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("SortKey direction must not be blank");
            }
        }
    }
}
