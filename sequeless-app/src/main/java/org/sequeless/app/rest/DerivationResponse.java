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
 */
public record DerivationResponse(String kind, String summary) {}
