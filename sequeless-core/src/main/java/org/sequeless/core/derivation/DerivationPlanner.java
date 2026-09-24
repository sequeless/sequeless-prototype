package org.sequeless.core.derivation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.spi.Scope;
import org.sequeless.spi.derivation.DerivationContext;
import org.sequeless.spi.derivation.DerivationPlugin;
import org.sequeless.spi.meta.DerivationRule;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PluginRule;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.QueryPort;

/**
 * Computes every {@code sq:Rollup}/{@code sq:Plugin}-derived property across a page of {@link
 * BusinessObject}s with a bounded number of {@link QueryPort#aggregate}/{@link
 * DerivationPlugin#derive} calls: one per distinct {@link DerivationRule} on the page, never one
 * per object. {@code read} calls {@link #apply} with a singleton list, so it shares exactly the
 * same code path and the same one-call-per-rule bound.
 *
 * <p>A page may mix subtypes (an abstract {@code browse} request, for example), so each object's
 * derived properties are discovered against its own resolved type — {@link
 * TypeDefinition#properties()} already includes every inherited property, so no separate
 * supertype walk is needed here.
 *
 * <p>{@link RollupRule}/{@link PluginRule} are records, so two properties (possibly on different
 * types) that carry an identical rule collapse into a single {@link Map} entry for free — this is
 * exactly how a page of many objects sharing one rollup rule still issues only one {@link
 * AggregateRequest}.
 */
public final class DerivationPlanner {

    private final QueryPort queryPort;
    private final DerivationPluginRegistry pluginRegistry;

    /**
     * @param queryPort the port used both to resolve {@link RollupRule}s via {@link
     *     QueryPort#aggregate} and to hand to plug-ins through {@link DerivationContext}; must not
     *     be {@code null}
     * @param pluginRegistry the {@link DerivationPlugin} lookup {@link PluginRule}s resolve
     *     through; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public DerivationPlanner(QueryPort queryPort, DerivationPluginRegistry pluginRegistry) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.pluginRegistry =
            Objects.requireNonNull(pluginRegistry, "pluginRegistry must not be null");
    }

    /**
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the type system to resolve each object's own type and every rule against;
     *     must not be {@code null}
     * @param requestType the type the caller originally requested; unused beyond documenting
     *     intent, since a mixed-subtype page resolves each object's own type instead; must not be
     *     {@code null}
     * @param objects the objects to compute derived properties for, in the order to preserve; must
     *     not be {@code null}; may be empty
     * @return a new list, same order as {@code objects}, each a copy with derived properties
     *     merged in
     * @throws NullPointerException if any argument is {@code null}
     */
    public List<BusinessObject> apply(
        Scope scope, MetaModelSnapshot snapshot, TypeDefinition requestType, List<BusinessObject> objects) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(objects, "objects must not be null");

        if (objects.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, TypeDefinition> typeByObjectId = new LinkedHashMap<>();
        for (BusinessObject object : objects) {
            typeByObjectId.put(object.id(), resolveType(snapshot, object));
        }

        Map<DerivationRule, List<PropertyDefinition>> propertiesByRule = new LinkedHashMap<>();
        Map<DerivationRule, Set<ObjectId>> targetIdsByRule = new LinkedHashMap<>();
        for (BusinessObject object : objects) {
            TypeDefinition type = typeByObjectId.get(object.id());
            for (PropertyDefinition property : type.properties()) {
                if (property.derivation().isEmpty()) {
                    continue;
                }
                DerivationRule rule = property.derivation().get();
                List<PropertyDefinition> properties =
                    propertiesByRule.computeIfAbsent(rule, unused -> new ArrayList<>());
                if (!properties.contains(property)) {
                    properties.add(property);
                }
                targetIdsByRule
                    .computeIfAbsent(rule, unused -> new LinkedHashSet<>())
                    .add(object.id());
            }
        }

        Map<String, Map<ObjectId, Value>> computedByPropertyIri = new LinkedHashMap<>();
        for (Map.Entry<DerivationRule, List<PropertyDefinition>> entry : propertiesByRule.entrySet()) {
            DerivationRule rule = entry.getKey();
            List<PropertyDefinition> properties = entry.getValue();
            Set<ObjectId> targetIds = targetIdsByRule.get(rule);

            Map<ObjectId, Value> values = computeRule(scope, snapshot, rule, targetIds, objects);

            for (PropertyDefinition property : properties) {
                computedByPropertyIri.put(property.iri(), values);
            }
        }

        List<BusinessObject> result = new ArrayList<>(objects.size());
        for (BusinessObject object : objects) {
            result.add(merge(object, typeByObjectId.get(object.id()), computedByPropertyIri));
        }
        return result;
    }

    /**
     * Computes a single {@link DerivationRule}'s value for {@code targetIds}, dispatching to {@link
     * #resolveRollup} or {@link #resolvePlugin} exactly as {@link #apply}'s per-rule loop did before
     * this method was extracted from it — {@code apply} now simply calls this once per distinct rule
     * on its page. Extracted so {@link DefaultDerivationRecomputer} (reacting to individual domain
     * events rather than a request's whole page) can share this exact one-rule aggregate code path
     * instead of re-implementing the {@code RollupRule}/{@code PluginRule} dispatch.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the type system {@code rule} and {@code targetIds} are resolved against; must
     *     not be {@code null}
     * @param rule the rule to compute; must not be {@code null}
     * @param targetIds the ids to compute {@code rule}'s value for; must not be {@code null}
     * @param objects the objects {@code targetIds} may be drawn from, needed only for a {@link
     *     PluginRule} (a {@link RollupRule} never inspects this list); must not be {@code null}
     * @return the computed value per target id, per {@link AggregateResult}'s density contract for a
     *     {@link RollupRule}, or whatever {@link org.sequeless.spi.derivation.DerivationPlugin#derive}
     *     returns for a {@link PluginRule}
     * @throws NullPointerException if any argument is {@code null}
     */
    public Map<ObjectId, Value> computeRule(
        Scope scope,
        MetaModelSnapshot snapshot,
        DerivationRule rule,
        Set<ObjectId> targetIds,
        List<BusinessObject> objects) {
        return switch (rule) {
            case RollupRule rollup -> resolveRollup(scope, snapshot, rollup, targetIds);
            case PluginRule pluginRule -> resolvePlugin(scope, snapshot, pluginRule, targetIds, objects);
        };
    }

    private static TypeDefinition resolveType(MetaModelSnapshot snapshot, BusinessObject object) {
        return snapshot
            .type(object.type().iri())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Object " + object.id() + " has unknown type " + object.type().iri()));
    }

    private Map<ObjectId, Value> resolveRollup(
        Scope scope, MetaModelSnapshot snapshot, RollupRule rollup, Set<ObjectId> targetIds) {
        Set<String> sourceTypes = TypeHierarchy.concreteTypeAndSubtypes(snapshot, rollup.sourceTypeIri());
        AggregateRequest request =
            new AggregateRequest(
                sourceTypes,
                rollup.viaIri(),
                targetIds,
                rollup.function(),
                rollup.ofPropertyIri(),
                rollup.criteria());
        AggregateResult result = queryPort.aggregate(scope, snapshot, request);
        return result.values();
    }

    private Map<ObjectId, Value> resolvePlugin(
        Scope scope,
        MetaModelSnapshot snapshot,
        PluginRule pluginRule,
        Set<ObjectId> targetIds,
        List<BusinessObject> objects) {
        DerivationPlugin plugin = pluginRegistry.get(pluginRule.pluginName());
        List<BusinessObject> targetObjects =
            objects.stream().filter(object -> targetIds.contains(object.id())).toList();
        DerivationContext context = new DerivationContext(scope, snapshot, queryPort);
        return plugin.derive(context, targetObjects);
    }

    /**
     * Builds a copy of {@code object} with every derived property IRI known on {@code type}
     * removed from its stored properties first — defensive, since nothing should ever write a
     * value under a derived IRI — then re-populated with whatever value {@code
     * computedByPropertyIri} produced for that object, if any.
     */
    private static BusinessObject merge(
        BusinessObject object,
        TypeDefinition type,
        Map<String, Map<ObjectId, Value>> computedByPropertyIri) {
        Map<PropertyRef, Value> properties = new LinkedHashMap<>(object.properties());
        for (PropertyDefinition property : type.properties()) {
            if (property.derivation().isEmpty()) {
                continue;
            }
            PropertyRef ref = new PropertyRef(property.iri());
            properties.remove(ref);
            Map<ObjectId, Value> values = computedByPropertyIri.get(property.iri());
            if (values == null) {
                continue;
            }
            Value value = values.get(object.id());
            if (value != null) {
                properties.put(ref, value);
            }
        }
        return new BusinessObject(
            object.id(),
            object.type(),
            object.tenant(),
            object.version(),
            object.state(),
            properties,
            object.audit(),
            object.deleted());
    }
}
