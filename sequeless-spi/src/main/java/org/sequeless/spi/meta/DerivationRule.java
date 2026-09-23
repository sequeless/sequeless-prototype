package org.sequeless.spi.meta;

/**
 * The rule that computes a derived property's value on read, per {@link
 * PropertyDefinition#derivation()}: either a {@link RollupRule} (a declarative aggregate over a
 * related type, {@code sq:Rollup}) or a {@link PluginRule} (a named implementation looked up by
 * {@code ServiceLoader}, {@code sq:Plugin}). Sealed to exactly these two permitted
 * implementations, mirroring {@link PropertyDefinition} itself, so a {@code switch} over {@code
 * DerivationRule} is exhaustive without a default case.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because {@link RollupRule}
 * and {@link PluginRule} live in their own files, not nested inside this one; implicit permits
 * only works for subtypes nested in the sealed type's own file.
 *
 * <p>{@link #materialised()} ({@code sq:materialised}) decides whether this rule's value is, in
 * addition to being recomputed on every read exactly as before, also kept correct in {@code
 * sq_object.props} from the domain events that could change it — so {@code
 * filter[openTaskCount][eq]=0} matches real rows in {@code sq_object} rather than silently matching
 * nothing, exactly as {@code ex:openTaskCount} does once its rule is marked materialised.
 * Materialising a rule never changes what a {@code GET}/browse response returns for that property
 * — it is always the freshly recomputed aggregate, never the stored value — it only makes the
 * stored value exist so {@code QueryPort} can filter and sort on it. Filtering or sorting on a
 * derived property whose rule is not materialised is rejected with a {@code 400}, rather than
 * silently matching nothing as it once did.
 */
public sealed interface DerivationRule permits RollupRule, PluginRule {

    /**
     * @return {@code true} if this rule's value is kept correct in {@code sq_object.props} from
     *     domain events, making it filterable and sortable; {@code false} if it is computed only
     *     on read, as every derived property was before this phase
     */
    boolean materialised();
}
