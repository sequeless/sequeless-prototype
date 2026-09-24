package org.sequeless.app.rest;

/**
 * JSON response shape describing how a derived property's value is computed, mapped from {@code
 * org.sequeless.spi.meta.DerivationRule} by {@link TypeResponseMapper}. Present only on properties
 * that carry a {@code sq:derivedBy} rule; every such property is also forced {@code readOnly} at
 * the snapshot level, so {@code readOnly} together with a non-null {@code derivation} always agree.
 *
 * @param kind {@code "rollup"} for a {@code RollupRule}, {@code "plugin"} for a {@code PluginRule}
 * @param summary a human-readable rendering of the rule, e.g. {@code "count(Task via
 *     belongsToProject where status ne 'done')"} or {@code "plugin(workload)"}
 * @param materialised whether this rule's value is also kept correct in {@code sq_object.props}
 *     from domain events ({@code sq:materialised}), rather than computed only on read; {@code
 *     GET}/browse responses always return the freshly recomputed aggregate regardless of this
 *     flag — {@code materialised} only means the stored value additionally exists so {@code
 *     QueryPort} can filter and sort on it
 */
public record DerivationResponse(String kind, String summary, boolean materialised) {}
