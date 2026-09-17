package org.sequeless.spi.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.spi.object.Page;

/**
 * A single request to {@link QueryPort#query}: which types to search, how to filter, sort, and
 * page the results, and which properties to compute facet buckets for.
 *
 * @param types the resolved set of concrete type IRIs to query; the caller (core) expands an
 *     abstract type such as {@code WorkItem} to its concrete subtypes ({@code Task}, {@code
 *     Project}) using the meta-model snapshot's hierarchy before constructing this {@code Query} —
 *     the port itself never does hierarchy reasoning and never sees an abstract type IRI here;
 *     must not be {@code null}, must not be empty; returned as an unmodifiable copy so callers
 *     cannot mutate this query after construction
 * @param criteria the filter clauses to apply, combined with AND; must not be {@code null}; may be
 *     empty; returned as an unmodifiable copy so callers cannot mutate this query after
 *     construction
 * @param text a free-text search term to match against searchable properties, if any; must not be
 *     {@code null} (the {@link Optional} wrapper itself, not just its contents)
 * @param sorts the ordering clauses to apply, in list order; must not be {@code null}; may be
 *     empty, in which case the default order documented on {@link QueryPort#query} applies;
 *     returned as an unmodifiable copy so callers cannot mutate this query after construction
 * @param page the page of results to return; must not be {@code null}
 * @param facetProperties the property IRIs to compute facet buckets for; must not be {@code null};
 *     may be empty, in which case {@link QueryResult#facets()} is empty; returned as an
 *     unmodifiable copy so callers cannot mutate this query after construction
 * @param includeDeleted whether soft-deleted objects should be included; phase 3 callers always
 *     pass {@code false}
 */
public record Query(
    Set<String> types,
    List<Criterion> criteria,
    Optional<String> text,
    List<Sort> sorts,
    Page page,
    List<String> facetProperties,
    boolean includeDeleted) {

    public Query {
        Objects.requireNonNull(types, "types must not be null");
        types = Set.copyOf(types);
        if (types.isEmpty()) {
            throw new IllegalArgumentException("Query types must not be empty");
        }
        Objects.requireNonNull(criteria, "criteria must not be null");
        criteria = List.copyOf(criteria);
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(sorts, "sorts must not be null");
        sorts = List.copyOf(sorts);
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(facetProperties, "facetProperties must not be null");
        facetProperties = List.copyOf(facetProperties);
    }
}
