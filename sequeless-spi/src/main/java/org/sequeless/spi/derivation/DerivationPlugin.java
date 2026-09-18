package org.sequeless.spi.derivation;

import java.util.List;
import java.util.Map;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Value;

/**
 * Computes a {@code sq:Plugin}-derived property's value for a batch of objects, named by {@link
 * #name()} and matched against a type's {@code sq:pluginName}. This is itself the {@link
 * java.util.ServiceLoader} service type adapters register under {@code
 * META-INF/services/org.sequeless.spi.derivation.DerivationPlugin} — unlike {@link
 * org.sequeless.spi.AdapterDescriptorProvider}, which exists only as an indirection because {@link
 * org.sequeless.spi.AdapterDescriptor} is a record and {@code ServiceLoader} cannot instantiate a
 * record reflectively. A {@link DerivationPlugin} implementation is an ordinary class with a no-arg
 * constructor, so it can be the service type directly; there is no analogous reason to add a
 * pointless provider wrapper here.
 *
 * <p>The planner (core) groups every property in a page whose rule is a {@code
 * org.sequeless.spi.meta.PluginRule} by {@link #name()} and calls {@link #derive} once per plug-in
 * per page with every target object across that page — never once per object — exactly as it groups
 * {@code org.sequeless.spi.meta.RollupRule}s into batched {@link
 * org.sequeless.spi.query.AggregateRequest}s. A plug-in that needs to look up related objects
 * itself does so through {@link DerivationContext#queryPort()}, which is subject to the same
 * bounded-query expectation.
 */
public interface DerivationPlugin {

    /**
     * @return the name matched against a type's {@code sq:pluginName}; must never be blank, and
     *     must be stable across the plug-in's lifetime since it is the sole key {@code
     *     ServiceLoader}-based lookup and ontology activation's unknown-plug-in-name check resolve
     *     it by
     */
    String name();

    /**
     * Computes this plug-in's derived value for each of {@code objects}.
     *
     * @param context the tenant, type system, and port access this call runs under; must not be
     *     {@code null}
     * @param objects the objects to compute a value for, all of a type whose {@code sq:pluginName}
     *     matches {@link #name()}; must not be {@code null}; may be empty
     * @return the computed value per object id; an id absent from the returned map means this
     *     plug-in has no value for that object, mirroring the empty-result contract documented on
     *     {@link org.sequeless.spi.query.QueryPort#aggregate} for {@code sum}/{@code min}/{@code
     *     max}/{@code avg}; must never be {@code null}
     */
    Map<ObjectId, Value> derive(DerivationContext context, List<BusinessObject> objects);
}
