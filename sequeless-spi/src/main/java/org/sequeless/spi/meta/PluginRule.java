package org.sequeless.spi.meta;

/**
 * A code-backed {@code sq:Plugin}: derive a property's value by name-dispatching to a registered
 * {@link org.sequeless.spi.derivation.DerivationPlugin}, exactly as {@code ex:Person.workload} is
 * computed by a sample plug-in in the testkit. Whether {@link #pluginName()} actually resolves to a
 * registered {@link org.sequeless.spi.derivation.DerivationPlugin} on the classpath is not checked
 * by this record — that is the Jena adapter's job at ontology activation time (startup validation,
 * {@code reload}, and {@code importDocument} all share the same check), exactly as it already
 * validates unresolved imports and reserved terms.
 *
 * @param pluginName the {@link org.sequeless.spi.derivation.DerivationPlugin#name()} this rule
 *     dispatches to ({@code sq:pluginName}); must not be blank
 */
public record PluginRule(String pluginName) implements DerivationRule {

    public PluginRule {
        if (pluginName == null || pluginName.isBlank()) {
            throw new IllegalArgumentException("PluginRule pluginName must not be blank");
        }
    }
}
