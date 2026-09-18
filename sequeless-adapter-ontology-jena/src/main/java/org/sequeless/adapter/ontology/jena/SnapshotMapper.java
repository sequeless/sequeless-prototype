package org.sequeless.adapter.ontology.jena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.Severity;

/**
 * The outcome of {@link SnapshotMapper#map(OntModel)}: every {@link TypeDefinition} the model
 * contains, deterministically ordered, plus any {@code WARNING} {@link OntologyIssue}s produced
 * along the way (currently only the unmapped-datatype fallback). Building the rest of a {@code
 * MetaModelSnapshot} — ontology IRI, version IRI, prefixes, and merging in the {@code
 * ConsistencyChecker}'s {@code OntologyReport} — is {@code JenaOntologyPort}'s job, not this
 * mapper's; {@code SnapshotMapper} only ever turns OWL/{@code sq:} into snapshot value types.
 *
 * @param types every named, non-builtin type in the model, sorted by IRI
 * @param warnings {@code WARNING}-severity issues found while mapping; never contains an {@code
 *     ERROR} — a document too broken to map at all is {@link OntModelBuilder}'s concern, not this
 *     mapper's
 */
record MappingResult(List<TypeDefinition> types, List<OntologyIssue> warnings) {}

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
        List<OntologyIssue> warnings = new ArrayList<>();

        List<OntClass.Named> classes = model.classes()
            .filter(SnapshotMapper::isNotBuiltin)
            .sorted(Comparator.comparing(OntClass.Named::getURI))
            .toList();
        Map<String, OntClass.Named> classByIri =
            classes.stream().collect(Collectors.toMap(OntClass.Named::getURI, c -> c));

        Map<String, List<PropertyMeta>> declaredByOwner = new HashMap<>();
        model.dataProperties()
            .filter(SnapshotMapper::isNotBuiltin)
            .map(p -> dataPropertyMeta(p, warnings))
            .flatMap(Optional::stream)
            .forEach(meta -> declaredByOwner.computeIfAbsent(meta.ownerIri(), k -> new ArrayList<>()).add(meta));
        model.objectProperties()
            .filter(SnapshotMapper::isNotBuiltin)
            .map(p -> objectPropertyMeta(p, warnings))
            .flatMap(Optional::stream)
            .forEach(meta -> declaredByOwner.computeIfAbsent(meta.ownerIri(), k -> new ArrayList<>()).add(meta));

        Map<String, LinkedHashMap<String, PropertyMeta>> memo = new HashMap<>();
        List<TypeDefinition> types = classes.stream()
            .map(cls -> toTypeDefinition(cls, classByIri, declaredByOwner, memo))
            .sorted(Comparator.comparing(TypeDefinition::iri))
            .toList();

        return new MappingResult(types, List.copyOf(warnings));
    }

    private static TypeDefinition toTypeDefinition(
            OntClass.Named cls,
            Map<String, OntClass.Named> classByIri,
            Map<String, List<PropertyMeta>> declaredByOwner,
            Map<String, LinkedHashMap<String, PropertyMeta>> memo) {
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
            cls.getURI(), label, superTypes, properties, displayHints, isAbstract, Optional.empty());
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

    private static Optional<PropertyMeta> dataPropertyMeta(OntDataProperty property, List<OntologyIssue> warnings) {
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
            warnings.add(new OntologyIssue(
                Severity.WARNING,
                Optional.of(property.getURI()),
                "Unrecognised datatype '" + xsdIri.orElse("(none declared)") + "' for property "
                    + property.getURI() + "; defaulting to " + Datatype.STRING));
        }

        return Optional.of(new PropertyMeta(
            property.getURI(),
            ownerIri.get(),
            false,
            property,
            resolveLabel(property),
            SqAnnotations.bool(property, SqVocabulary.FACET, false),
            SqAnnotations.bool(property, SqVocabulary.INDEXED, false),
            SqAnnotations.bool(property, SqVocabulary.SEARCHABLE, false),
            SqAnnotations.bool(property, SqVocabulary.READ_ONLY, false),
            SqAnnotations.bool(property, SqVocabulary.DISPLAY_LABEL, false),
            displayHintsOf(property),
            null,
            Optional.empty(),
            false,
            datatype));
    }

    private static Optional<PropertyMeta> objectPropertyMeta(
            OntObjectProperty.Named property, List<OntologyIssue> warnings) {
        Optional<String> ownerIri = mostSpecificNamed(property.domains());
        if (ownerIri.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> targetTypeIri = mostSpecificNamed(property.ranges());
        if (targetTypeIri.isEmpty()) {
            warnings.add(new OntologyIssue(
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

        return Optional.of(new PropertyMeta(
            property.getURI(),
            ownerIri.get(),
            true,
            property,
            resolveLabel(property),
            SqAnnotations.bool(property, SqVocabulary.FACET, false),
            SqAnnotations.bool(property, SqVocabulary.INDEXED, false),
            SqAnnotations.bool(property, SqVocabulary.SEARCHABLE, false),
            SqAnnotations.bool(property, SqVocabulary.READ_ONLY, false),
            SqAnnotations.bool(property, SqVocabulary.DISPLAY_LABEL, false),
            displayHintsOf(property),
            targetTypeIri.get(),
            inverseIri,
            property.isTransitive(),
            null));
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
                Optional.empty(),
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
            Optional.empty(),
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
     * {@code sq-vocabulary.md} specifies, reused for both types and properties.
     */
    private static String resolveLabel(OntObject subject) {
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
        Datatype datatype) {}
}
