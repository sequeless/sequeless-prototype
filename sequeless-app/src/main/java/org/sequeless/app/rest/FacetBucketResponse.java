package org.sequeless.app.rest;

/**
 * JSON response body for a single facet value/count pair, rendered by {@link
 * QueryResultResponse#facets()}.
 *
 * @param value the display string this bucket counts — never a raw id, per {@link
 *     org.sequeless.spi.query.FacetBucket}'s own contract: for a reference-typed facet property
 *     this is the target object's {@code sq:displayLabel} value (or the target id as text if the
 *     target type declares no {@code sq:displayLabel} property), and for a scalar facet property
 *     it is that property's lexical value
 * @param count the number of matching objects with this value; never negative
 */
public record FacetBucketResponse(String value, long count) {}
