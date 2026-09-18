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
 */
public sealed interface DerivationRule permits RollupRule, PluginRule {
}
