package org.sequeless.spi.meta;

/**
 * Placeholder for the derivation-rule shape ({@code sq:Rollup} and {@code sq:Plugin}) that Phase 4
 * (derived properties, DR-08) will define. {@link PropertyDefinition#derivation()} already carries
 * an {@code Optional<DerivationRule>} so the snapshot shape does not need to change again once
 * Phase 4 lands; this type exists only so that slot compiles today, genuinely empty until then.
 */
public record DerivationRule() {
}
