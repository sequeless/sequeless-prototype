package org.sequeless.adapter.ontology.jena;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;

/**
 * Builds the single-object RDF data graph {@link ShaclValidationPort} runs {@code
 * org.apache.jena.shacl.ShaclValidator} against: {@code object} becomes {@code urn:uuid:<id>},
 * typed with its own type IRI plus every ancestor type IRI from {@code snapshot} (the full
 * transitive closure, not just direct superclasses — see this class's package-private {@link
 * #typeAndSupertypes(MetaModelSnapshot, String)} javadoc for why that is necessary, not merely
 * thorough), and each property either as a typed literal (an {@link AttributeDefinition}'s
 * declared {@link Datatype} drives the XSD type) or as an {@code urn:uuid:<target>} object
 * reference, expanding a {@link ListValue} into one triple per element rather than an RDF
 * collection.
 *
 * <p>Package-private and stateless: this is a pure function of {@link MetaModelSnapshot} and
 * {@link BusinessObject}, with no dependency on any live Jena model, so {@code
 * ShaclDataGraphBuilderTest} exercises it directly without going through {@link
 * ShaclValidationPort} or a Spring context at all.
 */
final class ShaclDataGraphBuilder {

    private ShaclDataGraphBuilder() {}

    /**
     * @param snapshot the type system {@code object}'s type and properties are resolved against;
     *     must not be {@code null}
     * @param object the single object to render as an RDF data graph; must not be {@code null}
     * @return a freshly built {@link Graph} containing exactly {@code object}'s triples
     * @throws IllegalArgumentException if {@code object}'s type is not in {@code snapshot}
     */
    static Graph build(MetaModelSnapshot snapshot, BusinessObject object) {
        Model model = ModelFactory.createDefaultModel();
        Resource subject = model.createResource(subjectUri(object));

        for (String typeIri : typeAndSupertypes(snapshot, object.type().iri())) {
            model.add(subject, RDF.type, model.createResource(typeIri));
        }

        TypeDefinition typeDef = snapshot.type(object.type().iri())
            .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + object.type().iri()));
        Map<String, PropertyDefinition> byIri = new HashMap<>();
        for (PropertyDefinition def : typeDef.properties()) {
            byIri.put(def.iri(), def);
        }

        object.properties().forEach((propRef, value) ->
            addValue(model, subject, propRef.iri(), value, byIri.get(propRef.iri())));

        return model.getGraph();
    }

    private static String subjectUri(BusinessObject object) {
        return "urn:uuid:" + object.id().value();
    }

    /**
     * Walks {@link TypeDefinition#superTypes()} — documented as direct edges only — transitively,
     * because plain {@code ShaclValidator.validate(Shapes, Graph)} runs no reasoner over this data
     * graph: an {@code sh:targetClass} shape only fires against an {@code rdf:type} triple actually
     * present in the graph, so a shape targeting an indirect ancestor would silently never apply
     * unless every ancestor's IRI is asserted here explicitly.
     *
     * @param snapshot the type system to walk {@code superTypes()} edges against
     * @param typeIri the starting (most specific) type IRI
     * @return every type IRI in the closure, starting with {@code typeIri} itself
     */
    private static Set<String> typeAndSupertypes(MetaModelSnapshot snapshot, String typeIri) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(typeIri));
        while (!queue.isEmpty()) {
            String iri = queue.poll();
            if (!visited.add(iri)) {
                continue;
            }
            snapshot.type(iri).ifPresent(def -> queue.addAll(def.superTypes()));
        }
        return visited;
    }

    private static void addValue(
            Model model, Resource subject, String propertyIri, Value value, PropertyDefinition def) {
        Property predicate = model.createProperty(propertyIri);
        switch (value) {
            case ListValue list -> list.values().forEach(v -> addValue(model, subject, propertyIri, v, def));
            case ReferenceValue ref ->
                model.add(subject, predicate, model.createResource("urn:uuid:" + ref.target().value()));
            case TextValue text -> model.add(subject, predicate, literal(model, text.value(), def));
            case IntegerValue integer -> model.add(subject, predicate, model.createTypedLiteral(integer.value()));
            case DecimalValue decimal -> model.add(subject, predicate, model.createTypedLiteral(decimal.value()));
            case BoolValue bool -> model.add(subject, predicate, model.createTypedLiteral(bool.value()));
            case DateTimeValue dt ->
                model.add(subject, predicate, model.createTypedLiteral(dt.value().toString(), XSDDatatype.XSDdateTime));
            case DateValue date ->
                model.add(subject, predicate, model.createTypedLiteral(date.value().toString(), XSDDatatype.XSDdate));
        }
    }

    /**
     * @param def the property's declared definition, or {@code null} if {@code snapshot} has no
     *     property with this IRI (an object carrying an unknown property is still rendered, typed
     *     as {@code xsd:string}, rather than making graph construction itself fail)
     */
    private static Literal literal(Model model, String lexical, PropertyDefinition def) {
        String xsdIri = (def instanceof AttributeDefinition attr) ? attr.datatype().xsdIri() : Datatype.STRING.xsdIri();
        return model.createTypedLiteral(lexical, TypeMapper.getInstance().getSafeTypeByName(xsdIri));
    }
}
