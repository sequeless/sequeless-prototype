/**
 * Computing derived properties on read: {@link org.sequeless.core.derivation.DerivationPlanner}
 * batches every {@code sq:Rollup}/{@code sq:Plugin}-derived property across a page of {@link
 * org.sequeless.spi.object.BusinessObject}s into a bounded number of {@link
 * org.sequeless.spi.query.QueryPort#aggregate} / {@link
 * org.sequeless.spi.derivation.DerivationPlugin#derive} calls — one per distinct {@link
 * org.sequeless.spi.meta.DerivationRule} per page, never one per object — and merges the computed
 * values back into fresh copies of those objects. {@link
 * org.sequeless.core.derivation.DerivationPluginRegistry} is the {@link java.util.ServiceLoader}
 * -backed lookup {@link org.sequeless.spi.meta.PluginRule}s resolve their named plug-in through.
 *
 * <p>This sits beside {@link org.sequeless.core.validation} as another stateless-helper package
 * {@code usecase} classes call into, rather than inside {@code usecase} itself, since it is not a
 * {@code Default...} implementation of an inbound use-case contract.
 */
package org.sequeless.core.derivation;
