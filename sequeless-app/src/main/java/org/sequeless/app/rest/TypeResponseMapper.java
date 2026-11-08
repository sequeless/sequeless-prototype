package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.sequeless.core.api.TypeDescription;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.meta.behaviour.DerivationRule;
import org.sequeless.spi.meta.behaviour.ExternalSignalTrigger;
import org.sequeless.spi.meta.behaviour.OnChangeTrigger;
import org.sequeless.spi.meta.behaviour.PluginRule;
import org.sequeless.spi.meta.behaviour.RollupRule;
import org.sequeless.spi.meta.behaviour.State;
import org.sequeless.spi.meta.behaviour.StateMachineDefinition;
import org.sequeless.spi.meta.behaviour.TimerTrigger;
import org.sequeless.spi.meta.behaviour.Transition;
import org.sequeless.spi.meta.behaviour.TriggerKind;
import org.sequeless.spi.meta.behaviour.TriggerSpec;
import org.sequeless.spi.meta.behaviour.UserActionTrigger;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.RelationshipDefinition;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * Maps the {@code org.sequeless.spi.meta} schema and behaviour types, and core's {@link
 * TypeDescription}, onto their {@link TypesController} wire representations. Factored out of {@link
 * TypesController} itself, mirroring {@link WhoAmIResponse#from(org.sequeless.core.api.WhoAmIResult)},
 * because both {@link TypeSummaryResponse} and {@link TypeDetailResponse} need the same
 * superTypes-to-short-names rendering.
 *
 * <p>Package-private: nothing outside {@code org.sequeless.app.rest} has a reason to map these
 * types directly.
 */
final class TypeResponseMapper {

    /**
     * Operator tokens as the REST filter grammar spells them (see {@code
     * DefaultBusinessObjectService.parseOperatorToken}), keyed by {@link Operator}. Three operators
     * do not lowercase their enum name verbatim ({@code STARTS_WITH} -&gt; {@code startswith},
     * {@code IS_NULL} -&gt; {@code isnull}, {@code NOT_NULL} -&gt; {@code notnull}); the rest do
     * (e.g. {@code EQ} -&gt; {@code eq}).
     */
    private static final Map<Operator, String> OPERATOR_TOKENS = Map.ofEntries(
            Map.entry(Operator.EQ, "eq"),
            Map.entry(Operator.NE, "ne"),
            Map.entry(Operator.IN, "in"),
            Map.entry(Operator.LT, "lt"),
            Map.entry(Operator.LTE, "lte"),
            Map.entry(Operator.GT, "gt"),
            Map.entry(Operator.GTE, "gte"),
            Map.entry(Operator.CONTAINS, "contains"),
            Map.entry(Operator.STARTS_WITH, "startswith"),
            Map.entry(Operator.IS_NULL, "isnull"),
            Map.entry(Operator.NOT_NULL, "notnull"));

    private TypeResponseMapper() {}

    /**
     * @param schema the schema to render; must not be {@code null}
     * @return one {@link TypeSummaryResponse} per {@link Schema#types()}, in the same order, with
     *     {@code superTypes} resolved to short names via a {@code Map<iri, name>} built once for
     *     this call
     */
    static List<TypeSummaryResponse> toSummaries(Schema schema) {
        Map<String, String> namesByIri = namesByIri(schema);
        return schema.types().stream().map(type -> toSummary(type, namesByIri)).toList();
    }

    /**
     * @param description the resolved, authorized type with its state machine and derivations; must
     *     not be {@code null}
     * @return the {@link TypeDetailResponse} rendering of {@code description}, with {@code
     *     properties} left in the order {@link TypeDefinition#properties()} already provides
     */
    static TypeDetailResponse toDetail(TypeDescription description) {
        TypeDefinition type = description.type();
        List<String> superTypeNames =
                type.superTypes().stream().map(TypeResponseMapper::shortName).toList();
        List<PropertyResponse> properties = type.properties().stream()
                .map(property -> toProperty(property, description.derivations()))
                .toList();
        return new TypeDetailResponse(
                type.iri(),
                shortName(type.iri()),
                type.label(),
                type.isAbstract(),
                superTypeNames,
                type.displayHints().group().orElse(null),
                type.displayHints().hidden(),
                properties,
                description
                        .stateMachine()
                        .map(TypeResponseMapper::toStateMachineResponse)
                        .orElse(null));
    }

    /**
     * Maps a {@link StateMachineDefinition} onto its {@link StateMachineResponse}.
     *
     * <p>{@code machine.states()} is rendered without re-sorting: it already arrives sorted by
     * {@code (displayOrder, iri)} — {@code SnapshotMapper}'s own contract, mirroring {@link
     * #toDetail(TypeDefinition)}'s identical reliance on {@link TypeDefinition#properties()}'s
     * pre-sorted order.
     *
     * <p>{@code machine.transitions()} is rendered <em>un</em>filtered: every transition of every
     * {@link TriggerKind} — {@code USER_ACTION}, {@code ON_CHANGE}, {@code TIMER}, {@code
     * EXTERNAL_SIGNAL} — is mapped, because this descriptor backs {@code GET
     * /types/{nameOrIri}}, a read-only type-level view a UI consumes to diagram the complete state
     * machine, including the automated transitions a client can never itself fire. This is
     * deliberately asymmetric with {@link
     * org.sequeless.app.rest.ObjectsController#transitions}: that per-object endpoint still filters
     * to {@code USER_ACTION}-only transitions, because it lists the transitions a client may
     * actually fire via {@code POST .../transitions/{name}}, whereas the automated kinds can only
     * ever be fired by the engine itself ({@code fireAutomated}).
     */
    private static StateMachineResponse toStateMachineResponse(StateMachineDefinition machine) {
        List<StateResponse> states =
                machine.states().stream().map(TypeResponseMapper::toState).toList();
        List<TransitionSummaryResponse> transitions = machine.transitions().stream()
                .map(TypeResponseMapper::toTransitionSummary)
                .toList();
        return new StateMachineResponse(
                machine.iri(), states, shortName(machine.initialState().iri()), transitions);
    }

    private static StateResponse toState(State state) {
        return new StateResponse(state.iri(), shortName(state.iri()), state.label(), state.displayOrder());
    }

    private static TransitionSummaryResponse toTransitionSummary(Transition transition) {
        TriggerSpec trigger = transition.trigger();
        return new TransitionSummaryResponse(
                transition.name(),
                shortName(transition.fromStateIri()),
                shortName(transition.toStateIri()),
                transition.guard().isPresent(),
                trigger.kind().name(),
                triggerAfter(trigger),
                triggerSignalName(trigger),
                triggerWatch(trigger));
    }

    /**
     * Renders {@link TransitionSummaryResponse#after()} from a transition's {@link TriggerSpec}, an
     * exhaustive {@code switch} over the sealed hierarchy with no {@code default} branch, mirroring
     * {@link #toDerivationResponse(DerivationRule)}'s own exhaustive switch.
     */
    private static String triggerAfter(TriggerSpec trigger) {
        return switch (trigger) {
            case TimerTrigger timer -> timer.after().toString();
            case UserActionTrigger ignored -> null;
            case OnChangeTrigger ignored -> null;
            case ExternalSignalTrigger ignored -> null;
        };
    }

    /** Renders {@link TransitionSummaryResponse#signalName()} from a transition's {@link TriggerSpec}. */
    private static String triggerSignalName(TriggerSpec trigger) {
        return switch (trigger) {
            case ExternalSignalTrigger signal -> signal.signalName();
            case UserActionTrigger ignored -> null;
            case OnChangeTrigger ignored -> null;
            case TimerTrigger ignored -> null;
        };
    }

    /**
     * Renders {@link TransitionSummaryResponse#watch()} from a transition's {@link TriggerSpec}: the
     * watched properties' short names for an {@link OnChangeTrigger}, empty (never {@code null}) for
     * every other trigger kind.
     */
    private static List<String> triggerWatch(TriggerSpec trigger) {
        return switch (trigger) {
            case OnChangeTrigger onChange ->
                onChange.watchIris().stream().map(TypeResponseMapper::shortName).toList();
            case UserActionTrigger ignored -> List.of();
            case TimerTrigger ignored -> List.of();
            case ExternalSignalTrigger ignored -> List.of();
        };
    }

    private static TypeSummaryResponse toSummary(TypeDefinition type, Map<String, String> namesByIri) {
        List<String> superTypeNames = type.superTypes().stream()
                .map(iri -> namesByIri.getOrDefault(iri, shortName(iri)))
                .toList();
        return new TypeSummaryResponse(
                type.iri(), namesByIri.get(type.iri()), type.label(), type.isAbstract(), superTypeNames);
    }

    private static Map<String, String> namesByIri(Schema schema) {
        List<TypeDefinition> types = schema.types();
        List<String> names = schema.names();
        Map<String, String> namesByIri = new HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            namesByIri.put(types.get(i).iri(), names.get(i));
        }
        return namesByIri;
    }

    /**
     * Maps one {@link PropertyDefinition} onto its {@link PropertyResponse}. The {@code switch} is
     * exhaustive over the sealed {@link PropertyDefinition} hierarchy with no {@code default}
     * branch, so a third permitted implementation arriving later would fail this compilation unit
     * rather than silently falling through unmapped.
     *
     * @param property the property to render; must not be {@code null}
     * @param derivations the derivation of each derived property of the type, keyed by property
     *     IRI; a property with no entry has no {@code derivation} in its response
     */
    private static PropertyResponse toProperty(PropertyDefinition property, Map<String, DerivationRule> derivations) {
        return switch (property) {
            case AttributeDefinition attribute ->
                new AttributePropertyResponse(
                        "attribute",
                        attribute.iri(),
                        shortName(attribute.iri()),
                        attribute.label(),
                        attribute.order(),
                        attribute.displayHints().group().orElse(null),
                        attribute.displayHints().hidden(),
                        attribute.facet(),
                        attribute.indexed(),
                        attribute.searchable(),
                        attribute.readOnly(),
                        attribute.cardinality().min(),
                        attribute.cardinality().max().isPresent()
                                ? attribute.cardinality().max().getAsInt()
                                : null,
                        attribute.datatype().name(),
                        derivationOf(attribute.iri(), derivations));
            case RelationshipDefinition relationship ->
                new RelationshipPropertyResponse(
                        "relationship",
                        relationship.iri(),
                        shortName(relationship.iri()),
                        relationship.label(),
                        relationship.order(),
                        relationship.displayHints().group().orElse(null),
                        relationship.displayHints().hidden(),
                        relationship.facet(),
                        relationship.indexed(),
                        relationship.searchable(),
                        relationship.readOnly(),
                        relationship.cardinality().min(),
                        relationship.cardinality().max().isPresent()
                                ? relationship.cardinality().max().getAsInt()
                                : null,
                        relationship.targetTypeIri(),
                        relationship.inverseIri().orElse(null),
                        relationship.transitive(),
                        derivationOf(relationship.iri(), derivations));
        };
    }

    /**
     * @param propertyIri the IRI of the property being rendered
     * @param derivations the derivation of each derived property of the type, keyed by property IRI
     * @return the {@link DerivationResponse} of {@code propertyIri}, or {@code null} if the property
     *     is not derived
     */
    private static DerivationResponse derivationOf(String propertyIri, Map<String, DerivationRule> derivations) {
        DerivationRule rule = derivations.get(propertyIri);
        return rule == null ? null : toDerivationResponse(rule);
    }

    /**
     * Maps one {@link DerivationRule} onto its {@link DerivationResponse}, an exhaustive {@code
     * switch} over the sealed {@link DerivationRule} hierarchy with no {@code default} branch,
     * mirroring {@link #toProperty(PropertyDefinition, Map)}'s own exhaustive switch.
     */
    private static DerivationResponse toDerivationResponse(DerivationRule rule) {
        return switch (rule) {
            case RollupRule rollup -> new DerivationResponse("rollup", rollupSummary(rollup), rollup.materialised());
            case PluginRule plugin ->
                new DerivationResponse("plugin", "plugin(" + plugin.pluginName() + ")", plugin.materialised());
        };
    }

    private static String rollupSummary(RollupRule rollup) {
        StringBuilder summary = new StringBuilder();
        summary.append(rollup.function().name().toLowerCase(Locale.ROOT))
                .append('(')
                .append(shortName(rollup.sourceTypeIri()))
                .append(" via ")
                .append(shortName(rollup.viaIri()));
        if (rollup.ofPropertyIri().isPresent()) {
            summary.append(" of ").append(shortName(rollup.ofPropertyIri().get()));
        }
        if (!rollup.criteria().isEmpty()) {
            summary.append(" where ")
                    .append(rollup.criteria().stream()
                            .map(TypeResponseMapper::criterionSummary)
                            .collect(Collectors.joining(" and ")));
        }
        summary.append(')');
        return summary.toString();
    }

    private static String criterionSummary(Criterion criterion) {
        String property = shortName(criterion.property());
        String operator = OPERATOR_TOKENS.get(criterion.operator());
        if (criterion.value().isEmpty()) {
            return property + " " + operator;
        }
        return property + " " + operator + " " + valueSummary(criterion.value().get());
    }

    /**
     * Renders a {@link Criterion}'s value for a rollup summary, an exhaustive {@code switch} over
     * the sealed {@link Value} hierarchy with no {@code default} branch.
     */
    private static String valueSummary(Value value) {
        return switch (value) {
            case TextValue text -> "'" + text.value() + "'";
            case IntegerValue integer -> Long.toString(integer.value());
            case DecimalValue decimal -> decimal.value().toString();
            case BoolValue bool -> Boolean.toString(bool.value());
            case DateValue date -> date.value().toString();
            case DateTimeValue dateTime -> dateTime.value().toString();
            case ReferenceValue reference -> reference.target().toString();
            case ListValue list ->
                list.values().stream()
                        .map(TypeResponseMapper::valueSummary)
                        .collect(Collectors.joining(", ", "[", "]"));
        };
    }

    /**
     * Duplicates {@code Schema}'s own short-name rule (local name after an IRI's last {@code #}, or
     * after its last {@code /} if there is no {@code #}) rather than calling back into a schema,
     * because {@link #toDetail(TypeDescription)} renders a single, already resolved {@link
     * TypeDefinition} — reading a whole schema just to look up one supertype's short name would
     * mean an extra read for no benefit: the rule is a pure function of the IRI string, not of what
     * else happens to be in the schema.
     *
     * @param iri the IRI to derive a short name from; must not be {@code null}
     * @return the local name after the IRI's last {@code #}, or after its last {@code /} if there
     *     is no {@code #}, or the whole IRI if neither is present
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
