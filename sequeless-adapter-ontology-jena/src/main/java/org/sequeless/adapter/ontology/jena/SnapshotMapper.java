package org.sequeless.adapter.ontology.jena;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntDataProperty;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.ontapi.model.OntObject;
import org.apache.jena.ontapi.model.OntObjectProperty;
import org.apache.jena.ontapi.model.OntRelationalProperty;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.sequeless.spi.meta.Action;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.CreateObjectAction;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DerivationRule;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.LogAction;
import org.sequeless.spi.meta.PluginRule;
import org.sequeless.spi.meta.PropertyAssignment;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.SetPropertyAction;
import org.sequeless.spi.meta.State;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.WebhookAction;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * The outcome of {@link SnapshotMapper#map(OntModel)}: every {@link TypeDefinition} the model
 * contains, deterministically ordered, plus every {@link OntologyIssue} found while mapping.
 * {@code WARNING} issues (currently only the unmapped-datatype fallback) describe a degraded but
 * still-usable mapping; {@code ERROR} issues describe a malformed {@code sq:derivedBy} rule (a
 * node typed as both/neither {@code sq:Rollup}/{@code sq:Plugin}, an unknown {@code sq:function}/
 * {@code sq:operator}, a missing required term, or a {@code sq:via} that is not an object property
 * of the {@code sq:over} type) — {@code JenaOntologyPort.buildStateFrom} folds these into the
 * overall consistency verdict exactly like every other activation-time source of truth, the same
 * way it already does for unresolved imports and reserved terms.
 *
 * @param types every named, non-builtin type in the model, sorted by IRI
 * @param issues every issue found while mapping, in the order produced; may contain {@code ERROR}
 *     severity for a malformed derivation rule
 */
record MappingResult(List<TypeDefinition> types, List<OntologyIssue> issues) {}

/**
 * Translates a built {@link OntModel} into the SPI's {@link TypeDefinition} snapshot shape,
 * honouring the hazards verified empirically against Jena 6.1.0 (see {@code plan.md}'s "API facts
 * verified empirically" table and {@code sq-vocabulary.md}'s "what the reasoner setting changes"):
 *
 * <ul>
 *   <li><b>Builtin leakage.</b> Under the {@code owl} reasoner, {@code superClasses()}, {@code
 *       domains()}, {@code ranges()} and even {@code model.classes()} all leak {@code owl:Thing},
 *       {@code rdfs:Resource} and other RDF/RDFS/OWL/XSD builtins. Every accessor that walks these
 *       is filtered through {@link #isNotBuiltin(Resource)}.
 *   <li><b>Domain/range closure.</b> Under {@code owl}, {@code domains()}/{@code ranges()} return
 *       the <em>entire</em> superclass closure of the true domain/range, not just the most specific
 *       class (e.g. {@code hasTask.ranges()} = {@code {Task, WorkItem, Deliverable, owl:Thing,
 *       rdfs:Resource}}). {@link #mostSpecificNamed(Stream)} picks the one candidate that is not an
 *       ancestor of any other candidate — the leaf of the chain — exactly as {@code
 *       step-plan-phase-3.md} §3 specifies, with ties from genuinely unrelated branches (a
 *       deliberate phase-1 case that does not arise in the reference ontology) broken by IRI order.
 *   <li><b>{@code declaredProperties()} is reasoner-dependent</b> and attaches domainless properties
 *       to every class under {@code none}; it is never called here. Property ownership is instead
 *       computed once, from each property's most-specific {@code rdfs:domain}, and inherited down
 *       through {@link #effectivePropertyMeta}'s memoized recursion over {@code superClasses(false)}
 *       — which is what makes property attribution identical under all three reasoner settings even
 *       though that recursion reuses the same reasoner-dependent accessor {@link
 *       TypeDefinition#superTypes()} is built from: a direct {@code rdfs:subClassOf} edge is always
 *       visible regardless of reasoning, so walking one level at a time and recursing re-derives the
 *       transitive closure ourselves, without depending on the reasoner having done it for us.
 *   <li><b>Cardinality is per (type, property), not per property.</b> A restriction such as
 *       {@code Task}'s {@code title minCardinality 1} is declared on {@code Task}'s own {@code
 *       rdfs:subClassOf} axioms, not on {@code title}'s owning type {@code Deliverable}; verified
 *       empirically (see the T7 handback) that the anonymous restriction node is a member of {@code
 *       Task.superClasses(false)} under all three reasoner settings, but not of {@code
 *       Deliverable.superClasses(false)}. Consequently cardinality is deliberately <em>excluded</em>
 *       from the memoized structural map in {@link #effectivePropertyMeta} — baking it in there
 *       would freeze it at whichever type first computed it and incorrectly propagate that scope to
 *       every subtype — and is instead recomputed fresh, per emitted {@link TypeDefinition}, by
 *       {@link #cardinalityFor(OntClass, OntRelationalProperty)}.
 * </ul>
 *
 * <p>Package-private: only {@code JenaOntologyPort} calls this.
 */
final class SnapshotMapper {

    private static final Set<String> BUILTIN_NAMESPACES =
        Set.of(RDF.getURI(), RDFS.getURI(), OWL.getURI(), XSD.getURI(), SqVocabulary.NS);

    private static final int NO_DISPLAY_ORDER = DisplayHints.none().order();

    private SnapshotMapper() {}

    /**
     * @param model a built, non-{@code null} {@link OntModel}; may be inconsistent — this method
     *     does no consistency checking of its own (that is {@code ConsistencyChecker}'s job) and
     *     maps whatever structure is there
     * @return every named type in {@code model} with its properties, supertypes and display hints,
     *     plus any {@code WARNING} issues found while mapping
     */
    static MappingResult map(OntModel model) {
        List<OntologyIssue> issues = new ArrayList<>();

        List<OntClass.Named> classes = model.classes()
            .filter(SnapshotMapper::isNotBuiltin)
            .sorted(Comparator.comparing(OntClass.Named::getURI))
            .toList();
        Map<String, OntClass.Named> classByIri =
            classes.stream().collect(Collectors.toMap(OntClass.Named::getURI, c -> c));

        Map<String, List<PropertyMeta>> declaredByOwner = new HashMap<>();
        model.dataProperties()
            .filter(SnapshotMapper::isNotBuiltin)
            .map(p -> dataPropertyMeta(p, model, issues))
            .flatMap(Optional::stream)
            .forEach(meta -> declaredByOwner.computeIfAbsent(meta.ownerIri(), k -> new ArrayList<>()).add(meta));
        model.objectProperties()
            .filter(SnapshotMapper::isNotBuiltin)
            .map(p -> objectPropertyMeta(p, model, issues))
            .flatMap(Optional::stream)
            .forEach(meta -> declaredByOwner.computeIfAbsent(meta.ownerIri(), k -> new ArrayList<>()).add(meta));

        Map<String, LinkedHashMap<String, PropertyMeta>> memo = new HashMap<>();

        // Third pass: sq:via must name an object property effectively declared on sq:over's type.
        // This cannot run inline while dataPropertyMeta/objectPropertyMeta build declaredByOwner
        // above: declaredByOwner is populated incrementally across those two loops, so a rule's
        // sq:over type's entry may not exist yet at the moment the rule itself is visited. Running
        // it here, after both loops, and iterating declaredByOwner's own values (each derived
        // property keyed exactly once by its owning type) rather than the final `types` list
        // avoids re-reporting a property N times for N subtypes that inherit it. An unknown
        // sq:over class needs no special case: effectivePropertyMeta then returns an empty map, so
        // `via` is null below and the same "not found" error fires naturally.
        declaredByOwner.values().stream()
            .flatMap(List::stream)
            .forEach(meta -> meta.derivation()
                .filter(RollupRule.class::isInstance)
                .map(RollupRule.class::cast)
                .ifPresent(rollup -> validateVia(meta.iri(), rollup, classByIri, declaredByOwner, memo, issues)));

        // Fourth pass: sq:StateMachine nodes, top-down (state machine -> its sq:appliesTo type)
        // rather than bottom-up like the sq:derivedBy passes above, since a state machine names
        // its target type instead of being discovered by walking that type's own properties.
        Map<String, StateMachineDefinition> stateMachineByTypeIri = stateMachinesOf(model, classByIri, issues);

        List<TypeDefinition> types = classes.stream()
            .map(cls -> toTypeDefinition(cls, classByIri, declaredByOwner, memo, stateMachineByTypeIri))
            .sorted(Comparator.comparing(TypeDefinition::iri))
            .toList();

        return new MappingResult(types, List.copyOf(issues));
    }

    /**
     * Validates that {@code rollup}'s {@code sq:via} names an object property effectively declared
     * on {@code rollup}'s {@code sq:over} type — the one rule-shape check that needs cross-type
     * knowledge and so cannot run inline in {@link #dataPropertyMeta}/{@link #objectPropertyMeta}.
     * Reuses the same memoized {@link #effectivePropertyMeta} recursion the final type-building
     * pass uses, so an unknown {@code sq:over} class or a {@code sq:via} that is a data property (or
     * simply absent from the source type) both surface as the same {@code ERROR}, named on {@code
     * propertyIri} (the derived property, not the rule node, since the rule node is a blank node
     * with no IRI to name).
     */
    private static void validateVia(
            String propertyIri,
            RollupRule rollup,
            Map<String, OntClass.Named> classByIri,
            Map<String, List<PropertyMeta>> declaredByOwner,
            Map<String, LinkedHashMap<String, PropertyMeta>> memo,
            List<OntologyIssue> issues) {
        LinkedHashMap<String, PropertyMeta> sourceProperties =
            effectivePropertyMeta(rollup.sourceTypeIri(), classByIri, declaredByOwner, memo);
        PropertyMeta via = sourceProperties.get(rollup.viaIri());
        if (via == null || !via.isObjectProperty()) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " has sq:via '" + rollup.viaIri()
                    + "' which is not an object property effectively declared on sq:over type '"
                    + rollup.sourceTypeIri() + "'"));
        }
    }

    private static TypeDefinition toTypeDefinition(
            OntClass.Named cls,
            Map<String, OntClass.Named> classByIri,
            Map<String, List<PropertyMeta>> declaredByOwner,
            Map<String, LinkedHashMap<String, PropertyMeta>> memo,
            Map<String, StateMachineDefinition> stateMachineByTypeIri) {
        LinkedHashMap<String, PropertyMeta> effective =
            effectivePropertyMeta(cls.getURI(), classByIri, declaredByOwner, memo);

        List<PropertyDefinition> properties = effective.values().stream()
            .map(meta -> toPropertyDefinition(meta, cardinalityFor(cls, meta.property())))
            .sorted(Comparator.comparingInt(PropertyDefinition::order).thenComparing(PropertyDefinition::iri))
            .toList();

        List<String> superTypes = cls.superClasses(false)
            .filter(SnapshotMapper::isNotBuiltin)
            .map(OntClass::getURI)
            .distinct()
            .sorted()
            .toList();

        String label = resolveLabel(cls);
        DisplayHints displayHints = displayHintsOf(cls);
        boolean isAbstract = SqAnnotations.bool(cls, SqVocabulary.ABSTRACT, false);

        return new TypeDefinition(
            cls.getURI(), label, superTypes, properties, displayHints, isAbstract,
            Optional.ofNullable(stateMachineByTypeIri.get(cls.getURI())));
    }

    /**
     * Structural (non-cardinality) property attribution for {@code typeIri}: its own declared
     * properties (those whose most-specific {@code rdfs:domain} is this type) overlaid on top of
     * every supertype's effective properties, recursively and memoized. A property redeclared by a
     * subtype (same IRI, re-attributed — not a phase-1 scenario, but the merge order defends against
     * it) always wins because {@code declaredByOwner} is applied last.
     *
     * <p>Deliberately excludes cardinality — see this class's javadoc for why.
     */
    private static LinkedHashMap<String, PropertyMeta> effectivePropertyMeta(
            String typeIri,
            Map<String, OntClass.Named> classByIri,
            Map<String, List<PropertyMeta>> declaredByOwner,
            Map<String, LinkedHashMap<String, PropertyMeta>> memo) {
        LinkedHashMap<String, PropertyMeta> cached = memo.get(typeIri);
        if (cached != null) {
            return cached;
        }

        LinkedHashMap<String, PropertyMeta> effective = new LinkedHashMap<>();
        OntClass.Named cls = classByIri.get(typeIri);
        if (cls != null) {
            List<String> superTypeIris = cls.superClasses(false)
                .filter(SnapshotMapper::isNotBuiltin)
                .map(OntClass::getURI)
                .distinct()
                .sorted()
                .toList();
            for (String superTypeIri : superTypeIris) {
                effective.putAll(effectivePropertyMeta(superTypeIri, classByIri, declaredByOwner, memo));
            }
        }
        for (PropertyMeta own : declaredByOwner.getOrDefault(typeIri, List.of())) {
            effective.put(own.iri(), own);
        }

        memo.put(typeIri, effective);
        return effective;
    }

    private static Optional<PropertyMeta> dataPropertyMeta(
            OntDataProperty property, OntModel model, List<OntologyIssue> issues) {
        Optional<String> ownerIri = mostSpecificNamed(property.domains());
        if (ownerIri.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> xsdIri = property.ranges()
            .filter(Resource::isURIResource)
            .map(Resource::getURI)
            .sorted()
            .findFirst();
        Datatype datatype = xsdIri.flatMap(Datatype::fromXsd).orElse(null);
        if (datatype == null) {
            datatype = Datatype.STRING;
            issues.add(new OntologyIssue(
                Severity.WARNING,
                Optional.of(property.getURI()),
                "Unrecognised datatype '" + xsdIri.orElse("(none declared)") + "' for property "
                    + property.getURI() + "; defaulting to " + Datatype.STRING));
        }

        Optional<DerivationRule> derivation = derivationOf(property, model, issues);
        boolean readOnly = derivation.isPresent() || SqAnnotations.bool(property, SqVocabulary.READ_ONLY, false);

        return Optional.of(new PropertyMeta(
            property.getURI(),
            ownerIri.get(),
            false,
            property,
            resolveLabel(property),
            SqAnnotations.bool(property, SqVocabulary.FACET, false),
            SqAnnotations.bool(property, SqVocabulary.INDEXED, false),
            SqAnnotations.bool(property, SqVocabulary.SEARCHABLE, false),
            readOnly,
            SqAnnotations.bool(property, SqVocabulary.DISPLAY_LABEL, false),
            displayHintsOf(property),
            null,
            Optional.empty(),
            false,
            datatype,
            derivation));
    }

    private static Optional<PropertyMeta> objectPropertyMeta(
            OntObjectProperty.Named property, OntModel model, List<OntologyIssue> issues) {
        Optional<String> ownerIri = mostSpecificNamed(property.domains());
        if (ownerIri.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> targetTypeIri = mostSpecificNamed(property.ranges());
        if (targetTypeIri.isEmpty()) {
            issues.add(new OntologyIssue(
                Severity.WARNING,
                Optional.of(property.getURI()),
                "Object property " + property.getURI() + " has no named rdfs:range; excluded from the snapshot"));
            return Optional.empty();
        }

        Optional<String> inverseIri = property.inverseProperties()
            .filter(OntObjectProperty::isURIResource)
            .map(OntObjectProperty::getURI)
            .sorted()
            .findFirst();

        Optional<DerivationRule> derivation = derivationOf(property, model, issues);
        boolean readOnly = derivation.isPresent() || SqAnnotations.bool(property, SqVocabulary.READ_ONLY, false);

        return Optional.of(new PropertyMeta(
            property.getURI(),
            ownerIri.get(),
            true,
            property,
            resolveLabel(property),
            SqAnnotations.bool(property, SqVocabulary.FACET, false),
            SqAnnotations.bool(property, SqVocabulary.INDEXED, false),
            SqAnnotations.bool(property, SqVocabulary.SEARCHABLE, false),
            readOnly,
            SqAnnotations.bool(property, SqVocabulary.DISPLAY_LABEL, false),
            displayHintsOf(property),
            targetTypeIri.get(),
            inverseIri,
            property.isTransitive(),
            null,
            derivation));
    }

    private static PropertyDefinition toPropertyDefinition(PropertyMeta meta, Cardinality cardinality) {
        if (meta.isObjectProperty()) {
            return new RelationshipDefinition(
                meta.iri(),
                meta.label(),
                cardinality,
                meta.facet(),
                meta.indexed(),
                meta.searchable(),
                meta.readOnly(),
                meta.displayLabel(),
                meta.displayHints(),
                meta.derivation(),
                meta.targetTypeIri(),
                meta.inverseIri(),
                meta.transitive());
        }
        return new AttributeDefinition(
            meta.iri(),
            meta.label(),
            cardinality,
            meta.facet(),
            meta.indexed(),
            meta.searchable(),
            meta.readOnly(),
            meta.displayLabel(),
            meta.displayHints(),
            meta.derivation(),
            meta.datatype());
    }

    /**
     * Cardinality of {@code property} as viewed from {@code type} specifically: intersects {@code
     * property.referringRestrictions()} (every restriction node anywhere in the model that
     * constrains this property) with {@code type.superClasses(false)} (this type's own ancestor set,
     * which — verified empirically — includes the anonymous restriction nodes {@code type} itself is
     * directly a subclass of, under all three reasoner settings). Only restrictions that are both
     * about this property and actually a superclass of {@code type} survive, per {@code
     * step-plan-phase-3.md} §4.
     */
    private static Cardinality cardinalityFor(OntClass type, OntRelationalProperty property) {
        Set<OntClass> ancestors = type.superClasses(false).collect(Collectors.toSet());

        int min = 0;
        OptionalInt max = OptionalInt.empty();
        List<OntClass.Restriction> restrictions =
            property.referringRestrictions().filter(ancestors::contains).toList();

        for (OntClass.Restriction restriction : restrictions) {
            if (restriction.canAs(OntClass.ObjectMinCardinality.class)) {
                min = Math.max(min, restriction.as(OntClass.ObjectMinCardinality.class).getCardinality());
            } else if (restriction.canAs(OntClass.DataMinCardinality.class)) {
                min = Math.max(min, restriction.as(OntClass.DataMinCardinality.class).getCardinality());
            }
            if (restriction.canAs(OntClass.ObjectMaxCardinality.class)) {
                max = combineMax(max, restriction.as(OntClass.ObjectMaxCardinality.class).getCardinality());
            } else if (restriction.canAs(OntClass.DataMaxCardinality.class)) {
                max = combineMax(max, restriction.as(OntClass.DataMaxCardinality.class).getCardinality());
            }
            if (restriction.canAs(OntClass.ObjectCardinality.class)) {
                int exact = restriction.as(OntClass.ObjectCardinality.class).getCardinality();
                min = Math.max(min, exact);
                max = combineMax(max, exact);
            } else if (restriction.canAs(OntClass.DataCardinality.class)) {
                int exact = restriction.as(OntClass.DataCardinality.class).getCardinality();
                min = Math.max(min, exact);
                max = combineMax(max, exact);
            }
            if (restriction.canAs(OntClass.ObjectSomeValuesFrom.class)
                    || restriction.canAs(OntClass.DataSomeValuesFrom.class)) {
                min = Math.max(min, 1);
            }
        }

        if (property.isFunctional()) {
            max = OptionalInt.of(Math.min(max.orElse(1), 1));
        }
        return new Cardinality(min, max);
    }

    private static OptionalInt combineMax(OptionalInt current, int candidate) {
        return OptionalInt.of(current.isPresent() ? Math.min(current.getAsInt(), candidate) : candidate);
    }

    /**
     * Picks the most specific named class among {@code candidates}, discarding builtins first (see
     * this class's javadoc on domain/range closure leakage). The most specific candidate is the one
     * that is not an ancestor of any other surviving candidate; ties between genuinely unrelated
     * branches — not exercised by the reference ontology — are broken by IRI order, a deliberate
     * phase-1 simplification per {@code step-plan-phase-3.md} §3.
     */
    private static Optional<String> mostSpecificNamed(Stream<OntClass> candidates) {
        List<OntClass> named = candidates.filter(SnapshotMapper::isNotBuiltin).distinct().toList();
        if (named.isEmpty()) {
            return Optional.empty();
        }

        List<OntClass> leaves = named.stream()
            .filter(candidate -> named.stream()
                .filter(other -> other != candidate)
                .noneMatch(other -> other.superClasses(false).anyMatch(candidate::equals)))
            .toList();
        List<OntClass> pool = leaves.isEmpty() ? named : leaves;
        return pool.stream().map(OntClass::getURI).min(Comparator.naturalOrder());
    }

    /**
     * {@code sq:label}, then {@code rdfs:label}, then the IRI's local name — the priority order
     * {@code sq-vocabulary.md} specifies, reused for types, properties, and (phase 5) {@code
     * sq:State} individuals. Parameter type is the plain {@link Resource}, not {@code OntObject} —
     * see {@link SqAnnotations}'s javadoc for why.
     */
    private static String resolveLabel(Resource subject) {
        return SqAnnotations.string(subject, SqVocabulary.LABEL)
            .or(() -> SqAnnotations.string(subject, RDFS.label))
            .orElseGet(subject::getLocalName);
    }

    private static DisplayHints displayHintsOf(OntObject subject) {
        int order = SqAnnotations.intValue(subject, SqVocabulary.DISPLAY_ORDER, NO_DISPLAY_ORDER);
        Optional<String> group = SqAnnotations.string(subject, SqVocabulary.DISPLAY_GROUP);
        boolean hidden = SqAnnotations.bool(subject, SqVocabulary.HIDDEN, false);
        return new DisplayHints(order, group, hidden);
    }

    /**
     * Mirrors {@code org.sequeless.spi.query.Operator}'s eleven constants against the local names
     * the eleven {@code sq:} operator individuals use. Deliberately an explicit map rather than a
     * camelCase-splitting regex: three names ({@code startsWith}, {@code isNull}, {@code notNull})
     * do not uppercase directly to their enum constant, and an explicit map cannot silently accept
     * an IRI nobody intended as an operator the way a permissive regex could — an unmatched local
     * name falls through to a clean "unknown operator" {@code ERROR} instead.
     */
    private static final Map<String, Operator> OPERATORS_BY_LOCAL_NAME = Map.ofEntries(
        Map.entry("eq", Operator.EQ),
        Map.entry("ne", Operator.NE),
        Map.entry("in", Operator.IN),
        Map.entry("lt", Operator.LT),
        Map.entry("lte", Operator.LTE),
        Map.entry("gt", Operator.GT),
        Map.entry("gte", Operator.GTE),
        Map.entry("contains", Operator.CONTAINS),
        Map.entry("startsWith", Operator.STARTS_WITH),
        Map.entry("isNull", Operator.IS_NULL),
        Map.entry("notNull", Operator.NOT_NULL));

    /**
     * Parses {@code property}'s {@code sq:derivedBy}, if any, into a {@link DerivationRule}. Every
     * rule-shape problem below (an untyped/dual-typed rule node, an unknown function/operator, a
     * missing required term, a malformed criterion) is recorded as an {@code ERROR} {@link
     * OntologyIssue} naming {@code property}'s IRI and short-circuits to {@link Optional#empty()}
     * rather than constructing a half-formed rule: {@link RollupRule} and {@link PluginRule} both
     * throw {@link IllegalArgumentException} from their compact constructors on a blank IRI/name,
     * which {@code JenaOntologyPort.buildState} would otherwise catch as an opaque whole-document
     * failure instead of a clean, named issue — so every field is validated before either
     * constructor is ever called.
     *
     * <p>Reads through the reasoner-expanded union model (whatever {@code property} itself is
     * backed by), not the base model: entailment only ever adds triples, it never masks a base
     * assertion, and every read here is a presence check ("does this rule node have a function?"),
     * never an absence check. Contrast {@link SqVocabulary#rejectionMessageIfReserved}, which reads
     * the base model because it checks the opposite thing — that a term is <em>never</em> used —
     * and a reasoner could not manufacture a use that was not already there, but reading the union
     * for an absence check would be reading the wrong thing for the wrong reason. Do not "fix" the
     * calls below into base-only reads; that would just make {@code owl}/{@code rdfs} inference no
     * longer able to help resolve one of these terms when it legitimately could.
     */
    private static Optional<DerivationRule> derivationOf(
            OntRelationalProperty property, OntModel model, List<OntologyIssue> issues) {
        Statement derivedByStmt = property.getProperty(SqVocabulary.DERIVED_BY);
        if (derivedByStmt == null) {
            return Optional.empty();
        }
        String propertyIri = property.getURI();
        RDFNode ruleObject = derivedByStmt.getObject();
        if (!ruleObject.isResource()) {
            issues.add(error(propertyIri, "sq:derivedBy on " + propertyIri + " must point to a node, not a literal"));
            return Optional.empty();
        }
        Resource ruleNode = ruleObject.asResource();

        boolean isRollup = ruleNode.hasProperty(RDF.type, SqVocabulary.ROLLUP);
        boolean isPlugin = ruleNode.hasProperty(RDF.type, SqVocabulary.PLUGIN);
        if (isRollup && isPlugin) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " is typed both sq:Rollup and sq:Plugin"));
            return Optional.empty();
        }
        if (!isRollup && !isPlugin) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " must be typed sq:Rollup or sq:Plugin"));
            return Optional.empty();
        }
        return isPlugin
            ? pluginRuleOf(propertyIri, ruleNode, issues)
            : rollupRuleOf(propertyIri, ruleNode, model, issues);
    }

    private static Optional<DerivationRule> pluginRuleOf(
            String propertyIri, Resource ruleNode, List<OntologyIssue> issues) {
        Statement nameStmt = ruleNode.getProperty(SqVocabulary.PLUGIN_NAME);
        if (nameStmt == null || nameStmt.getString().isBlank()) {
            issues.add(error(propertyIri, "sq:derivedBy on " + propertyIri + " is a sq:Plugin missing sq:pluginName"));
            return Optional.empty();
        }
        return Optional.of(new PluginRule(nameStmt.getString()));
    }

    private static Optional<DerivationRule> rollupRuleOf(
            String propertyIri, Resource ruleNode, OntModel model, List<OntologyIssue> issues) {
        Statement functionStmt = ruleNode.getProperty(SqVocabulary.FUNCTION);
        if (functionStmt == null || !functionStmt.getObject().isURIResource()) {
            issues.add(error(propertyIri, "sq:derivedBy on " + propertyIri + " is missing sq:function"));
            return Optional.empty();
        }
        String functionLocalName = functionStmt.getObject().asResource().getLocalName();
        AggregateFunction function;
        try {
            function = AggregateFunction.valueOf(functionLocalName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " has unknown sq:function '" + functionLocalName + "'"));
            return Optional.empty();
        }

        Statement overStmt = ruleNode.getProperty(SqVocabulary.OVER);
        if (overStmt == null || !overStmt.getObject().isURIResource()) {
            issues.add(error(propertyIri, "sq:derivedBy on " + propertyIri + " is missing sq:over"));
            return Optional.empty();
        }
        String sourceTypeIri = overStmt.getObject().asResource().getURI();

        Statement viaStmt = ruleNode.getProperty(SqVocabulary.VIA);
        if (viaStmt == null || !viaStmt.getObject().isURIResource()) {
            issues.add(error(propertyIri, "sq:derivedBy on " + propertyIri + " is missing sq:via"));
            return Optional.empty();
        }
        String viaIri = viaStmt.getObject().asResource().getURI();

        Statement ofStmt = ruleNode.getProperty(SqVocabulary.OF);
        Optional<String> ofPropertyIri = ofStmt != null && ofStmt.getObject().isURIResource()
            ? Optional.of(ofStmt.getObject().asResource().getURI())
            : Optional.empty();
        boolean requiresOf = function != AggregateFunction.COUNT;
        if (requiresOf && ofPropertyIri.isEmpty()) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " uses " + function + " but is missing sq:of"));
            return Optional.empty();
        }
        if (!requiresOf && ofPropertyIri.isPresent()) {
            issues.add(error(propertyIri,
                "sq:derivedBy on " + propertyIri + " uses sq:count but must not declare sq:of"));
            return Optional.empty();
        }

        Optional<List<Criterion>> criteria = criteriaOf(propertyIri, ruleNode, model, issues);
        if (criteria.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RollupRule(sourceTypeIri, viaIri, function, ofPropertyIri, criteria.get()));
    }

    /**
     * {@code sq:filter}, if present, is an RDF list of {@code sq:Criterion} nodes; absent means "no
     * filter" ({@code []}), per {@code sq-vocabulary.md}.
     */
    private static Optional<List<Criterion>> criteriaOf(
            String propertyIri, Resource ruleNode, OntModel model, List<OntologyIssue> issues) {
        Statement filterStmt = ruleNode.getProperty(SqVocabulary.FILTER);
        if (filterStmt == null) {
            return Optional.of(List.of());
        }
        RDFNode filterObject = filterStmt.getObject();
        if (!filterObject.isResource() || !filterObject.asResource().canAs(RDFList.class)) {
            issues.add(error(propertyIri, "sq:filter on " + propertyIri + " must be a well-formed RDF list"));
            return Optional.empty();
        }

        List<Criterion> criteria = new ArrayList<>();
        for (RDFNode node : filterObject.asResource().as(RDFList.class).asJavaList()) {
            Optional<Criterion> criterion = criterionOf(propertyIri, node, model, issues);
            if (criterion.isEmpty()) {
                return Optional.empty();
            }
            criteria.add(criterion.get());
        }
        return Optional.of(criteria);
    }

    private static Optional<Criterion> criterionOf(
            String propertyIri, RDFNode node, OntModel model, List<OntologyIssue> issues) {
        if (!node.isResource()) {
            issues.add(error(propertyIri, "sq:filter on " + propertyIri + " contains a non-resource criterion"));
            return Optional.empty();
        }
        Resource criterionNode = node.asResource();

        Statement propertyStmt = criterionNode.getProperty(SqVocabulary.PROPERTY);
        if (propertyStmt == null || !propertyStmt.getObject().isURIResource()) {
            issues.add(error(propertyIri, "sq:filter criterion on " + propertyIri + " is missing sq:property"));
            return Optional.empty();
        }
        String criterionPropertyIri = propertyStmt.getObject().asResource().getURI();

        Statement operatorStmt = criterionNode.getProperty(SqVocabulary.OPERATOR);
        if (operatorStmt == null || !operatorStmt.getObject().isURIResource()) {
            issues.add(error(propertyIri, "sq:filter criterion on " + propertyIri + " is missing sq:operator"));
            return Optional.empty();
        }
        String operatorLocalName = operatorStmt.getObject().asResource().getLocalName();
        Operator operator = OPERATORS_BY_LOCAL_NAME.get(operatorLocalName);
        if (operator == null) {
            issues.add(error(propertyIri,
                "sq:filter criterion on " + propertyIri + " has unknown sq:operator '" + operatorLocalName + "'"));
            return Optional.empty();
        }

        Statement valueStmt = criterionNode.getProperty(SqVocabulary.VALUE);
        boolean noValueOperator = operator == Operator.IS_NULL || operator == Operator.NOT_NULL;
        if (noValueOperator) {
            if (valueStmt != null) {
                issues.add(error(propertyIri,
                    "sq:filter criterion on " + propertyIri + " must not declare sq:value for " + operator));
                return Optional.empty();
            }
            return Optional.of(new Criterion(criterionPropertyIri, operator, Optional.empty()));
        }
        if (valueStmt == null) {
            issues.add(error(propertyIri, "sq:filter criterion on " + propertyIri + " is missing sq:value"));
            return Optional.empty();
        }
        Optional<Value> value =
            criterionValue(propertyIri, criterionPropertyIri, valueStmt.getString(), model, issues);
        return value.map(v -> new Criterion(criterionPropertyIri, operator, Optional.of(v)));
    }

    /**
     * Coerces {@code lexical} (the lexical form of a criterion's {@code sq:value}) against {@code
     * criterionPropertyIri}'s own kind and datatype. Thin wrapper over {@link
     * #resolveValueForProperty}, which is the shared core reused by phase 5's {@code sq:SetProperty}
     * / {@code sq:PropertyAssignment} {@code sq:value} coercion — see that method's javadoc for the
     * mechanics; this wrapper exists only to supply this call site's own {@code "sq:filter criterion
     * on ..."} error wording, so every message produced here is byte-for-byte what it always was.
     *
     * @return the coerced {@link Value}, or {@link Optional#empty()} after recording an {@code
     *     ERROR} naming {@code propertyIri} (the derived property) if {@code criterionPropertyIri}
     *     has no resolvable kind/datatype, or if {@code lexical} does not parse for it (an object
     *     property expects a UUID; a data property expects a lexical form its datatype accepts)
     */
    private static Optional<Value> criterionValue(
            String propertyIri,
            String criterionPropertyIri,
            String lexical,
            OntModel model,
            List<OntologyIssue> issues) {
        return resolveValueForProperty(
            propertyIri, "sq:filter criterion on " + propertyIri, criterionPropertyIri, lexical, model, issues);
    }

    /**
     * Coerces {@code lexical} against {@code targetPropertyIri}'s own kind and datatype, exactly as
     * {@code dataPropertyMeta} resolves an ordinary attribute's datatype: {@link
     * OntModel#getObjectProperty} / {@link OntModel#getDataProperty} tell us which kind {@code
     * targetPropertyIri} actually is (both return {@code null} rather than throwing when the IRI is
     * not declared as that kind — verified empirically), and for a data property the same {@link
     * OntDataProperty#ranges()} / {@link Datatype#fromXsd} lookup used there gives the target {@link
     * Datatype}. Shared by {@link #criterionValue} (a {@code sq:filter} criterion's {@code
     * sq:value}) and {@link #valueOrExpressionOf} (a {@code sq:SetProperty}/{@code
     * sq:PropertyAssignment} node's {@code sq:value}) — every caller supplies its own {@code
     * context} description (e.g. {@code "sq:filter criterion on " + propertyIri}) so the {@code
     * ERROR} message stays specific to what was actually being parsed, while the coercion mechanics
     * (and the {@code ERROR} it reports for each of the two ways a value can fail to resolve) live
     * here exactly once. This keeps a downstream {@link Value} always correct for every consumer (in
     * particular {@code PostgresQueryStore}, which casts the JSONB extraction per datatype): a value
     * against a numeric or date property must never be forced through a lexical {@link
     * org.sequeless.spi.object.TextValue}.
     *
     * @param errorSubjectIri the IRI an {@code ERROR} issue is named on
     * @param context a human-readable description of what is being parsed, e.g. {@code "sq:filter
     *     criterion on ex:openTaskCount"} or {@code "sq:SetProperty action on transition 'activate'
     *     of ex:ProjectLifecycle"}; prefixed onto every message this method reports
     * @return the coerced {@link Value}, or {@link Optional#empty()} after recording an {@code
     *     ERROR} if {@code targetPropertyIri} has no resolvable kind/datatype, or if {@code lexical}
     *     does not parse for it (an object property expects a UUID; a data property expects a
     *     lexical form its datatype accepts)
     */
    private static Optional<Value> resolveValueForProperty(
            String errorSubjectIri,
            String context,
            String targetPropertyIri,
            String lexical,
            OntModel model,
            List<OntologyIssue> issues) {
        if (model.getObjectProperty(targetPropertyIri) != null) {
            try {
                return Optional.of(Value.ref(ObjectId.parse(lexical)));
            } catch (IllegalArgumentException e) {
                issues.add(error(errorSubjectIri,
                    context + " has sq:value '" + lexical
                        + "' which is not a valid object id for object property " + targetPropertyIri));
                return Optional.empty();
            }
        }

        OntDataProperty dataProperty = model.getDataProperty(targetPropertyIri);
        Optional<Datatype> datatype = dataProperty == null
            ? Optional.empty()
            : dataProperty.ranges()
                .filter(Resource::isURIResource)
                .map(Resource::getURI)
                .sorted()
                .findFirst()
                .flatMap(Datatype::fromXsd);
        if (datatype.isEmpty()) {
            issues.add(error(errorSubjectIri,
                context + " references sq:property '" + targetPropertyIri + "' with no resolvable datatype"));
            return Optional.empty();
        }

        Optional<Value> value = coerceLiteral(datatype.get(), lexical);
        if (value.isEmpty()) {
            issues.add(error(errorSubjectIri,
                context + " has sq:value '" + lexical + "' which is not a valid "
                    + datatype.get() + " for property " + targetPropertyIri));
        }
        return value;
    }

    /**
     * Parses {@code lexical} per {@code datatype}, mirroring {@code
     * org.sequeless.core.validation.ValueCoercer}'s per-datatype coercion but starting from a
     * lexical string rather than an already-typed JSON value, since a Turtle {@code sq:value} is
     * always a literal's lexical form. Returns {@link Optional#empty()} — never throws — when
     * {@code lexical} does not parse, so the caller can report a clean, named {@code ERROR} instead
     * of an uncaught parse exception.
     */
    private static Optional<Value> coerceLiteral(Datatype datatype, String lexical) {
        try {
            return Optional.of(switch (datatype) {
                case STRING, ANY_URI, TIME, DURATION -> Value.text(lexical);
                case INTEGER, LONG -> Value.integer(Long.parseLong(lexical));
                case DECIMAL, DOUBLE -> Value.decimal(new BigDecimal(lexical));
                case BOOLEAN -> Value.bool(parseStrictBoolean(lexical));
                case DATE -> Value.date(LocalDate.parse(lexical));
                case DATE_TIME -> Value.dateTime(Instant.parse(lexical));
            });
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean parseStrictBoolean(String lexical) {
        if ("true".equalsIgnoreCase(lexical)) {
            return true;
        }
        if ("false".equalsIgnoreCase(lexical)) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean: " + lexical);
    }

    // -- sq:StateMachine (phase 5) ----------------------------------------------------------------

    /**
     * Finds every {@code sq:StateMachine} node in {@code model} and parses each into a {@link
     * StateMachineDefinition} keyed by the IRI of the type it governs ({@code sq:appliesTo}). A
     * top-down pass — state-machine node to governed type — rather than the bottom-up shape {@link
     * #derivationOf} uses (property to its own rule), since a state machine names its target type
     * instead of being discovered by walking that type's own properties; that is also why this
     * builds its own map once here rather than folding into the per-property loops that build
     * {@code declaredByOwner} above. A malformed state machine is skipped — absent from the
     * returned map, so its type resolves to {@link Optional#empty()} for {@code stateMachine()}) —
     * rather than aborting the whole document mapping, exactly like a malformed {@code sq:derivedBy}
     * rule is skipped for just the one property that declared it.
     */
    private static Map<String, StateMachineDefinition> stateMachinesOf(
            OntModel model, Map<String, OntClass.Named> classByIri, List<OntologyIssue> issues) {
        Map<String, StateMachineDefinition> byTypeIri = new LinkedHashMap<>();
        List<Resource> nodes = model.listResourcesWithProperty(RDF.type, SqVocabulary.STATE_MACHINE).toList();
        for (Resource node : nodes.stream().sorted(Comparator.comparing(SnapshotMapper::identifierOf)).toList()) {
            stateMachineOf(node, model, classByIri, issues)
                .ifPresent(byType -> byTypeIri.put(byType.typeIri(), byType.definition()));
        }
        return byTypeIri;
    }

    private static Optional<StateMachineByType> stateMachineOf(
            Resource node, OntModel model, Map<String, OntClass.Named> classByIri, List<OntologyIssue> issues) {
        String stateMachineIri = identifierOf(node);

        Statement appliesToStmt = node.getProperty(SqVocabulary.APPLIES_TO);
        if (appliesToStmt == null || !appliesToStmt.getObject().isURIResource()
                || !classByIri.containsKey(appliesToStmt.getObject().asResource().getURI())) {
            issues.add(error(stateMachineIri,
                "sq:StateMachine " + stateMachineIri + " must declare sq:appliesTo naming a named owl:Class"));
            return Optional.empty();
        }
        String typeIri = appliesToStmt.getObject().asResource().getURI();

        List<Statement> stateStmts = node.listProperties(SqVocabulary.STATE).toList();
        if (stateStmts.isEmpty()) {
            issues.add(error(stateMachineIri,
                "sq:StateMachine " + stateMachineIri + " must declare at least one sq:state"));
            return Optional.empty();
        }
        List<State> states = new ArrayList<>();
        for (Statement stmt : stateStmts) {
            RDFNode stateObject = stmt.getObject();
            if (!stateObject.isURIResource()) {
                issues.add(error(stateMachineIri,
                    "sq:StateMachine " + stateMachineIri + " has a sq:state that is not a named individual"));
                return Optional.empty();
            }
            Resource stateNode = stateObject.asResource();
            states.add(new State(
                stateNode.getURI(),
                resolveLabel(stateNode),
                SqAnnotations.intValue(stateNode, SqVocabulary.DISPLAY_ORDER, NO_DISPLAY_ORDER)));
        }
        states.sort(Comparator.comparingInt(State::displayOrder).thenComparing(State::iri));
        Set<String> stateIris = states.stream().map(State::iri).collect(Collectors.toSet());

        Statement initialStateStmt = node.getProperty(SqVocabulary.INITIAL_STATE);
        if (initialStateStmt == null || !initialStateStmt.getObject().isURIResource()
                || !stateIris.contains(initialStateStmt.getObject().asResource().getURI())) {
            issues.add(error(stateMachineIri,
                "sq:StateMachine " + stateMachineIri
                    + " sq:initialState must name one of its own sq:state values"));
            return Optional.empty();
        }
        String initialStateIri = initialStateStmt.getObject().asResource().getURI();
        State initialState = states.stream()
            .filter(s -> s.iri().equals(initialStateIri))
            .findFirst()
            .orElseThrow();

        List<Statement> transitionStmts = node.listProperties(SqVocabulary.TRANSITION).toList();
        List<Transition> transitions = new ArrayList<>();
        for (Statement stmt : transitionStmts) {
            if (!stmt.getObject().isResource()) {
                issues.add(error(stateMachineIri,
                    "sq:StateMachine " + stateMachineIri + " has a sq:transition that is not a node"));
                return Optional.empty();
            }
            Optional<Transition> transition =
                transitionOf(stateMachineIri, stmt.getObject().asResource(), stateIris, model, issues);
            if (transition.isEmpty()) {
                return Optional.empty();
            }
            transitions.add(transition.get());
        }
        transitions.sort(Comparator.comparing(Transition::name));

        try {
            return Optional.of(
                new StateMachineByType(typeIri, new StateMachineDefinition(stateMachineIri, states, initialState, transitions)));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:StateMachine " + stateMachineIri + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Parses one {@code sq:Transition} node: {@code sq:name} (required, non-blank), {@code
     * sq:from}/{@code sq:to} (required, must each name one of {@code stateIris}), {@code sq:trigger}
     * (required; must be exactly {@code sq:UserAction} — this phase's only supported trigger kind,
     * validated only, no snapshot field), {@code sq:guard}/{@code sq:guardMessage} (optional
     * strings), and {@code sq:action} (an ordered {@code rdf:List}, absent meaning {@code []}).
     */
    private static Optional<Transition> transitionOf(
            String stateMachineIri,
            Resource node,
            Set<String> stateIris,
            OntModel model,
            List<OntologyIssue> issues) {
        Statement nameStmt = node.getProperty(SqVocabulary.NAME);
        if (nameStmt == null || nameStmt.getString().isBlank()) {
            issues.add(error(stateMachineIri, "a sq:transition on " + stateMachineIri + " is missing sq:name"));
            return Optional.empty();
        }
        String name = nameStmt.getString();

        Statement fromStmt = node.getProperty(SqVocabulary.FROM);
        if (fromStmt == null || !fromStmt.getObject().isURIResource()
                || !stateIris.contains(fromStmt.getObject().asResource().getURI())) {
            issues.add(error(stateMachineIri, "transition '" + name + "' on " + stateMachineIri
                + " sq:from must name one of the state machine's sq:state values"));
            return Optional.empty();
        }
        String fromStateIri = fromStmt.getObject().asResource().getURI();

        Statement toStmt = node.getProperty(SqVocabulary.TO);
        if (toStmt == null || !toStmt.getObject().isURIResource()
                || !stateIris.contains(toStmt.getObject().asResource().getURI())) {
            issues.add(error(stateMachineIri, "transition '" + name + "' on " + stateMachineIri
                + " sq:to must name one of the state machine's sq:state values"));
            return Optional.empty();
        }
        String toStateIri = toStmt.getObject().asResource().getURI();

        Statement triggerStmt = node.getProperty(SqVocabulary.TRIGGER);
        if (triggerStmt == null || !triggerStmt.getObject().isURIResource()
                || !SqVocabulary.USER_ACTION.getURI().equals(triggerStmt.getObject().asResource().getURI())) {
            issues.add(error(stateMachineIri,
                "transition '" + name + "' on " + stateMachineIri + " sq:trigger must be sq:UserAction"));
            return Optional.empty();
        }

        Optional<String> guard = SqAnnotations.string(node, SqVocabulary.GUARD);
        Optional<String> guardMessage = SqAnnotations.string(node, SqVocabulary.GUARD_MESSAGE);

        Optional<List<Action>> actions = actionsOf(stateMachineIri, name, node, model, issues);
        if (actions.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new Transition(name, fromStateIri, toStateIri, guard, guardMessage, actions.get()));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri,
                "transition '" + name + "' on " + stateMachineIri + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * {@code sq:action}, if present, is an RDF list of action nodes; absent means "no actions"
     * ({@code []}), mirroring {@link #criteriaOf}'s handling of {@code sq:filter}.
     */
    private static Optional<List<Action>> actionsOf(
            String stateMachineIri, String transitionName, Resource transitionNode, OntModel model,
            List<OntologyIssue> issues) {
        Statement actionStmt = transitionNode.getProperty(SqVocabulary.ACTION);
        if (actionStmt == null) {
            return Optional.of(List.of());
        }
        RDFNode actionListObject = actionStmt.getObject();
        if (!actionListObject.isResource() || !actionListObject.asResource().canAs(RDFList.class)) {
            issues.add(error(stateMachineIri, "sq:action on transition '" + transitionName + "' of "
                + stateMachineIri + " must be a well-formed RDF list"));
            return Optional.empty();
        }

        List<Action> actions = new ArrayList<>();
        for (RDFNode actionNode : actionListObject.asResource().as(RDFList.class).asJavaList()) {
            Optional<Action> action = actionOf(stateMachineIri, transitionName, actionNode, model, issues);
            if (action.isEmpty()) {
                return Optional.empty();
            }
            actions.add(action.get());
        }
        return Optional.of(actions);
    }

    /**
     * Dispatches one {@code sq:action} list element by its {@code rdf:type} into exactly one of the
     * four {@link Action} variants; a node typed as none or more than one of {@code sq:SetProperty}/
     * {@code sq:CreateObject}/{@code sq:Webhook}/{@code sq:Log} is an {@code ERROR}, mirroring {@link
     * #derivationOf}'s {@code sq:Rollup}/{@code sq:Plugin} dual-type check.
     */
    private static Optional<Action> actionOf(
            String stateMachineIri, String transitionName, RDFNode node, OntModel model, List<OntologyIssue> issues) {
        if (!node.isResource()) {
            issues.add(error(stateMachineIri, "an action on transition '" + transitionName + "' of "
                + stateMachineIri + " is not a node"));
            return Optional.empty();
        }
        Resource actionNode = node.asResource();

        boolean isSetProperty = actionNode.hasProperty(RDF.type, SqVocabulary.SET_PROPERTY);
        boolean isCreateObject = actionNode.hasProperty(RDF.type, SqVocabulary.CREATE_OBJECT);
        boolean isWebhook = actionNode.hasProperty(RDF.type, SqVocabulary.WEBHOOK);
        boolean isLog = actionNode.hasProperty(RDF.type, SqVocabulary.LOG);
        int kindCount = (isSetProperty ? 1 : 0) + (isCreateObject ? 1 : 0) + (isWebhook ? 1 : 0) + (isLog ? 1 : 0);
        if (kindCount != 1) {
            issues.add(error(stateMachineIri, "an action on transition '" + transitionName + "' of "
                + stateMachineIri
                + " must be typed exactly one of sq:SetProperty, sq:CreateObject, sq:Webhook, sq:Log"));
            return Optional.empty();
        }

        String context = "on transition '" + transitionName + "' of " + stateMachineIri;
        if (isSetProperty) {
            return setPropertyActionOf(stateMachineIri, context, actionNode, model, issues).map(Action.class::cast);
        }
        if (isCreateObject) {
            return createObjectActionOf(stateMachineIri, context, actionNode, model, issues).map(Action.class::cast);
        }
        if (isWebhook) {
            return webhookActionOf(stateMachineIri, context, actionNode, issues).map(Action.class::cast);
        }
        return logActionOf(stateMachineIri, context, actionNode, issues).map(Action.class::cast);
    }

    private static Optional<SetPropertyAction> setPropertyActionOf(
            String stateMachineIri, String context, Resource node, OntModel model, List<OntologyIssue> issues) {
        Statement propertyStmt = node.getProperty(SqVocabulary.PROPERTY);
        if (propertyStmt == null || !propertyStmt.getObject().isURIResource()) {
            issues.add(error(stateMachineIri, "sq:SetProperty " + context + " is missing sq:property"));
            return Optional.empty();
        }
        String propertyIri = propertyStmt.getObject().asResource().getURI();

        Optional<ValueOrExpression> valueOrExpression = valueOrExpressionOf(
            stateMachineIri, "sq:SetProperty " + context, node, propertyIri, model, issues);
        if (valueOrExpression.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new SetPropertyAction(
                propertyIri, valueOrExpression.get().value(), valueOrExpression.get().expression()));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:SetProperty " + context + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<CreateObjectAction> createObjectActionOf(
            String stateMachineIri, String context, Resource node, OntModel model, List<OntologyIssue> issues) {
        Statement typeStmt = node.getProperty(SqVocabulary.TYPE);
        if (typeStmt == null || !typeStmt.getObject().isURIResource()) {
            issues.add(error(stateMachineIri, "sq:CreateObject " + context + " is missing sq:type"));
            return Optional.empty();
        }
        String createTypeIri = typeStmt.getObject().asResource().getURI();

        Statement propertiesStmt = node.getProperty(SqVocabulary.PROPERTIES);
        List<PropertyAssignment> properties = new ArrayList<>();
        if (propertiesStmt != null) {
            RDFNode propertiesObject = propertiesStmt.getObject();
            if (!propertiesObject.isResource() || !propertiesObject.asResource().canAs(RDFList.class)) {
                issues.add(error(stateMachineIri, "sq:properties " + context + " must be a well-formed RDF list"));
                return Optional.empty();
            }
            for (RDFNode propertyNode : propertiesObject.asResource().as(RDFList.class).asJavaList()) {
                Optional<PropertyAssignment> assignment =
                    propertyAssignmentOf(stateMachineIri, context, propertyNode, model, issues);
                if (assignment.isEmpty()) {
                    return Optional.empty();
                }
                properties.add(assignment.get());
            }
        }

        try {
            return Optional.of(new CreateObjectAction(createTypeIri, properties));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:CreateObject " + context + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<PropertyAssignment> propertyAssignmentOf(
            String stateMachineIri, String context, RDFNode node, OntModel model, List<OntologyIssue> issues) {
        if (!node.isResource()) {
            issues.add(error(stateMachineIri, "sq:properties " + context + " contains a non-resource sq:PropertyAssignment"));
            return Optional.empty();
        }
        Resource assignmentNode = node.asResource();
        if (!assignmentNode.hasProperty(RDF.type, SqVocabulary.PROPERTY_ASSIGNMENT)) {
            issues.add(error(stateMachineIri, "sq:properties " + context + " contains a node not typed sq:PropertyAssignment"));
            return Optional.empty();
        }

        Statement propertyStmt = assignmentNode.getProperty(SqVocabulary.PROPERTY);
        if (propertyStmt == null || !propertyStmt.getObject().isURIResource()) {
            issues.add(error(stateMachineIri, "sq:PropertyAssignment " + context + " is missing sq:property"));
            return Optional.empty();
        }
        String propertyIri = propertyStmt.getObject().asResource().getURI();

        Optional<ValueOrExpression> valueOrExpression = valueOrExpressionOf(
            stateMachineIri, "sq:PropertyAssignment " + context, assignmentNode, propertyIri, model, issues);
        if (valueOrExpression.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PropertyAssignment(
                propertyIri, valueOrExpression.get().value(), valueOrExpression.get().expression()));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:PropertyAssignment " + context + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<WebhookAction> webhookActionOf(
            String stateMachineIri, String context, Resource node, List<OntologyIssue> issues) {
        Statement urlStmt = node.getProperty(SqVocabulary.URL);
        if (urlStmt == null || urlStmt.getString().isBlank()) {
            issues.add(error(stateMachineIri, "sq:Webhook " + context + " is missing sq:url"));
            return Optional.empty();
        }
        String url = urlStmt.getString();
        String method = SqAnnotations.string(node, SqVocabulary.METHOD)
            .filter(m -> !m.isBlank())
            .orElse("POST");
        Optional<String> body = SqAnnotations.string(node, SqVocabulary.BODY);

        try {
            return Optional.of(new WebhookAction(url, method, body));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:Webhook " + context + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<LogAction> logActionOf(
            String stateMachineIri, String context, Resource node, List<OntologyIssue> issues) {
        Statement messageStmt = node.getProperty(SqVocabulary.MESSAGE);
        if (messageStmt == null || messageStmt.getString().isBlank()) {
            issues.add(error(stateMachineIri, "sq:Log " + context + " is missing sq:message"));
            return Optional.empty();
        }

        try {
            return Optional.of(new LogAction(messageStmt.getString()));
        } catch (IllegalArgumentException e) {
            issues.add(error(stateMachineIri, "sq:Log " + context + " is malformed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * The exactly-one-of-{@code sq:value}/{@code sq:expression} shape shared verbatim by {@code
     * sq:SetProperty} and {@code sq:PropertyAssignment} nodes: coerces {@code sq:value} against
     * {@code targetPropertyIri}'s own datatype via {@link #resolveValueForProperty} when present, or
     * carries {@code sq:expression}'s JEXL source through unparsed (the {@code ExpressionPort}, a
     * later phase, evaluates it) — factored out here once rather than duplicated between {@link
     * #setPropertyActionOf} and {@link #propertyAssignmentOf}.
     */
    private static Optional<ValueOrExpression> valueOrExpressionOf(
            String stateMachineIri,
            String context,
            Resource node,
            String targetPropertyIri,
            OntModel model,
            List<OntologyIssue> issues) {
        Statement valueStmt = node.getProperty(SqVocabulary.VALUE);
        Statement expressionStmt = node.getProperty(SqVocabulary.EXPRESSION);
        boolean hasValue = valueStmt != null;
        boolean hasExpression = expressionStmt != null;
        if (hasValue == hasExpression) {
            issues.add(error(stateMachineIri, context + " must declare exactly one of sq:value or sq:expression"));
            return Optional.empty();
        }

        if (hasExpression) {
            String expression = expressionStmt.getString();
            if (expression.isBlank()) {
                issues.add(error(stateMachineIri, context + " has a blank sq:expression"));
                return Optional.empty();
            }
            return Optional.of(new ValueOrExpression(Optional.empty(), Optional.of(expression)));
        }

        Optional<Value> value =
            resolveValueForProperty(stateMachineIri, context, targetPropertyIri, valueStmt.getString(), model, issues);
        return value.map(v -> new ValueOrExpression(Optional.of(v), Optional.empty()));
    }

    /**
     * @return {@code resource}'s IRI if it is named, or its {@code toString()} form (e.g. {@code
     *     "_:b0"}) otherwise — used only to name an {@code ERROR} issue's subject when the resource
     *     itself may be a blank node (state machines are always named in practice, but this defends
     *     against a malformed document that declares one anonymously anyway) and as a deterministic
     *     sort key across {@link #stateMachinesOf}'s document-wide pass.
     */
    private static String identifierOf(Resource resource) {
        return resource.isURIResource() ? resource.getURI() : resource.toString();
    }

    /** {@code sq:appliesTo}'s target type IRI, paired with the {@link StateMachineDefinition} it maps to. */
    private record StateMachineByType(String typeIri, StateMachineDefinition definition) {}

    /**
     * The parsed {@code sq:value}/{@code sq:expression} of a {@code sq:SetProperty} or {@code
     * sq:PropertyAssignment} node — exactly one populated, per {@link #valueOrExpressionOf}.
     */
    private record ValueOrExpression(Optional<Value> value, Optional<String> expression) {}

    private static OntologyIssue error(String propertyIri, String message) {
        return new OntologyIssue(Severity.ERROR, Optional.of(propertyIri), message);
    }

    /**
     * @return {@code true} unless {@code resource} is anonymous or belongs to the {@code rdf:},
     *     {@code rdfs:}, {@code owl:} or {@code xsd:} builtin namespaces
     */
    private static boolean isNotBuiltin(Resource resource) {
        // BUILTIN_NAMESPACES also includes SqVocabulary.NS: sq:Rollup/sq:Plugin/sq:Criterion (T1)
        // are owl:Class so that sq:derivedBy's blank nodes can be typed, but they describe a
        // derivation rule, not a business type or property, so they are excluded here exactly like
        // rdf:/rdfs:/owl:/xsd: builtins are.
        return resource.isURIResource() && !BUILTIN_NAMESPACES.contains(resource.getNameSpace());
    }

    /**
     * The non-cardinality facts about a single property, gathered once regardless of how many types
     * effectively carry it. {@code property} is retained so {@link #cardinalityFor} can be called
     * fresh for whichever type is currently being emitted. Exactly one of {@code targetTypeIri}/
     * {@code inverseIri}/{@code transitive} (object properties) or {@code datatype} (data
     * properties) is meaningful, selected by {@code isObjectProperty}; the unused side is {@code
     * null}/{@code false} rather than modelled as a second sealed hierarchy, since this is a private
     * implementation detail local to one method's recursion, not a public SPI shape.
     */
    private record PropertyMeta(
        String iri,
        String ownerIri,
        boolean isObjectProperty,
        OntRelationalProperty property,
        String label,
        boolean facet,
        boolean indexed,
        boolean searchable,
        boolean readOnly,
        boolean displayLabel,
        DisplayHints displayHints,
        String targetTypeIri,
        Optional<String> inverseIri,
        boolean transitive,
        Datatype datatype,
        Optional<DerivationRule> derivation) {}
}
