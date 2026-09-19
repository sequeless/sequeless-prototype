package org.sequeless.core.derivation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.sequeless.spi.derivation.DerivationPlugin;

/**
 * Every {@link DerivationPlugin} on the classpath, indexed by {@link DerivationPlugin#name()},
 * built once and eagerly at construction. Unlike the Jena adapter's own {@code ServiceLoader}
 * resolution — which is deliberately fresh per call so {@code reload()} sees a newly-registered
 * plug-in's name validated at ontology activation time — the set of {@link DerivationPlugin}s on
 * the classpath is fixed for the process's lifetime once it is running, so a {@link
 * DerivationPlanner} resolving one by name on every {@code read}/{@code browse} does not need to
 * re-walk {@link ServiceLoader} each time.
 */
public final class DerivationPluginRegistry {

    private final Map<String, DerivationPlugin> pluginsByName;

    /**
     * @param pluginsByName every {@link DerivationPlugin} on the classpath, keyed by {@link
     *     DerivationPlugin#name()}; must not be {@code null}; copied defensively
     * @throws NullPointerException if {@code pluginsByName} is {@code null}
     */
    public DerivationPluginRegistry(Map<String, DerivationPlugin> pluginsByName) {
        Objects.requireNonNull(pluginsByName, "pluginsByName must not be null");
        this.pluginsByName = Map.copyOf(pluginsByName);
    }

    /**
     * @return a registry built by loading every {@link DerivationPlugin} via {@link
     *     ServiceLoader#load(Class)}, indexed by {@link DerivationPlugin#name()}
     */
    public static DerivationPluginRegistry fromServiceLoader() {
        Map<String, DerivationPlugin> byName = new HashMap<>();
        for (DerivationPlugin plugin : ServiceLoader.load(DerivationPlugin.class)) {
            byName.put(plugin.name(), plugin);
        }
        return new DerivationPluginRegistry(byName);
    }

    /**
     * @param name the {@link DerivationPlugin#name()} to resolve; must not be {@code null}
     * @return the registered plug-in with that name
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalStateException if no plug-in is registered under {@code name} — should be
     *     unreachable in practice, since ontology activation already rejects an unknown {@code
     *     sq:pluginName} before a {@link org.sequeless.spi.meta.PluginRule} naming it can ever
     *     reach a snapshot; this is a defensive guard, not the primary validation path
     */
    public DerivationPlugin get(String name) {
        Objects.requireNonNull(name, "name must not be null");
        DerivationPlugin plugin = pluginsByName.get(name);
        if (plugin == null) {
            throw new IllegalStateException("No DerivationPlugin registered under name '" + name + "'");
        }
        return plugin;
    }
}
