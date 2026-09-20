package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.DerivationRule;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PluginRule;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.State;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * Maps sequeless-core's {@code org.sequeless.spi.meta} types onto their {@link TypesController}
 * wire representations. Factored out of {@link TypesController} itself, mirroring {@link
 * WhoAmIResponse#from(org.sequeless.core.api.WhoAmIResult)}, because both {@link
 * TypeSummaryResponse} and {@link TypeDetailResponse} need the same superTypes-to-short-names
 * rendering.
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
    private static final Map<Operator, String> OPERATOR_TOKENS =
            Map.ofEntries(
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
     * @param snapshot the snapshot to render; must not be {@code null}
     * @return one {@link TypeSummaryResponse} per {@link MetaModelSnapshot#types()}, in the same
     *     order, with {@code superTypes} resolved to short names via a {@code Map<iri, name>}
     *     built once for this call
     */
    static List<TypeSummaryResponse> toSummaries(MetaModelSnapshot snapshot) {
        Map<String, String> namesByIri = namesByIri(snapshot);
        return snapshot.types().stream().map(type -> toSummary(type, namesByIri)).toList();
    }

    /**
     * @param type the resolved, authorized type to render; must not be {@code null}
     * @return the {@link TypeDetailResponse} rendering of {@code type}, with {@code properties}
     *     left in the order {@link TypeDefinition#properties()} already provides
     */
    static TypeDetailResponse toDetail(TypeDefinition type) {
        List<String> superTypeNames =
                type.superTypes().stream().map(TypeResponseMapper::shortName).toList();
        List<PropertyResponse> properties =
                type.properties().stream().map(TypeResponseMapper::toProperty).toList();
        return new TypeDetailResponse(
                type.iri(),
                shortName(type.iri()),
                type.label(),
                type.isAbstract(),
                superTypeNames,
                type.displayHints().group().orElse(null),
                type.displayHints().hidden(),
                properties,
                type.stateMachine().map(TypeResponseMapper::toStateMachineResponse).orElse(null));
    }

    /**
     * Maps a {@link StateMachineDefinition} onto its {@link StateMachineResponse}.
     *
     * <p>{@code machine.states()} is rendered without re-sorting: it already arrives sorted by
     * {@code (displayOrder, iri)} — {@code SnapshotMapper}'s own contract, mirroring {@link
     * #toDetail(TypeDefinition)}'s identical reliance on {@link TypeDefinition#properties()}'s
     * pre-sorted order.
     */
    private static StateMachineResponse toStateMachineResponse(StateMachineDefinition machine) {
        List<StateResponse> states = machine.states().stream().map(TypeResponseMapper::toState).toList();
        List<TransitionSummaryResponse> transitions =
                machine.transitions().stream().map(TypeResponseMapper::toTransitionSummary).toList();
        return new StateMachineResponse(
                machine.iri(), states, shortName(machine.initialState().iri()), transitions);
    }

    private static StateResponse toState(State state) {
        return new StateResponse(state.iri(), shortName(state.iri()), state.label(), state.displayOrder());
    }

    private static TransitionSummaryResponse toTransitionSummary(Transition transition) {
        return new TransitionSummaryResponse(
                transition.name(),
                shortName(transition.fromStateIri()),
                shortName(transition.toStateIri()),
                transition.guard().isPresent());
    }

    private static TypeSummaryResponse toSummary(TypeDefinition type, Map<String, String> namesByIri) {
        List<String> superTypeNames =
                type.superTypes().stream()
                        .map(iri -> namesByIri.getOrDefault(iri, shortName(iri)))
                        .toList();
        return new TypeSummaryResponse(
                type.iri(), namesByIri.get(type.iri()), type.label(), type.isAbstract(), superTypeNames);
    }

    private static Map<String, String> namesByIri(MetaModelSnapshot snapshot) {
        List<TypeDefinition> types = snapshot.types();
        List<String> names = snapshot.names();
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
     */
    private static PropertyResponse toProperty(PropertyDefinition property) {
        return switch (property) {
            case AttributeDefinition attribute -> new AttributePropertyResponse(
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
                    attribute.derivation().map(TypeResponseMapper::toDerivationResponse).orElse(null));
            case RelationshipDefinition relationship -> new RelationshipPropertyResponse(
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
                    relationship
                            .derivation()
                            .map(TypeResponseMapper::toDerivationResponse)
                            .orElse(null));
        };
    }

    /**
     * Maps one {@link DerivationRule} onto its {@link DerivationResponse}, an exhaustive {@code
     * switch} over the sealed {@link DerivationRule} hierarchy with no {@code default} branch,
     * mirroring {@link #toProperty(PropertyDefinition)}'s own exhaustive switch.
     */
    private static DerivationResponse toDerivationResponse(DerivationRule rule) {
        return switch (rule) {
            case RollupRule rollup -> new DerivationResponse("rollup", rollupSummary(rollup));
            case PluginRule plugin -> new DerivationResponse(
                    "plugin", "plugin(" + plugin.pluginName() + ")");
        };
    }

    private static String rollupSummary(RollupRule rollup) {
        StringBuilder summary = new StringBuilder();
        summary
                .append(rollup.function().name().toLowerCase(Locale.ROOT))
                .append('(')
                .append(shortName(rollup.sourceTypeIri()))
                .append(" via ")
                .append(shortName(rollup.viaIri()));
        if (rollup.ofPropertyIri().isPresent()) {
            summary.append(" of ").append(shortName(rollup.ofPropertyIri().get()));
        }
        if (!rollup.criteria().isEmpty()) {
            summary
                    .append(" where ")
                    .append(
                            rollup.criteria().stream()
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
            case ListValue list -> list.values().stream()
                    .map(TypeResponseMapper::valueSummary)
                    .collect(Collectors.joining(", ", "[", "]"));
        };
    }

    /**
     * Duplicates {@code MetaModelSnapshot}'s own short-name rule (local name after an IRI's last
     * {@code #}, or after its last {@code /} if there is no {@code #}) rather than calling back
     * into a snapshot, because {@link #toDetail(TypeDefinition)} renders a single, already
     * resolved {@link TypeDefinition} — fetching a whole snapshot just to look up one supertype's
     * short name would mean an extra, redundantly-authorized {@code OntologyPort} round trip for
     * no benefit: the rule is a pure function of the IRI string, not of what else happens to be in
     * the snapshot.
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
