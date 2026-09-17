package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.testkit.Fixtures;

/**
 * Plain unit tests for {@link ShaclDataGraphBuilder}: no Spring, no {@code ValidationPort} —
 * exercises the RDF shape of the graph it builds directly, against a real {@link MetaModelSnapshot}
 * built from the reference-domain ontology (needed for a real {@code superTypes()} chain and real
 * {@code AttributeDefinition} datatypes).
 */
class ShaclDataGraphBuilderTest {

    private static final MetaModelSnapshot SNAPSHOT =
        JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL)
            .snapshot(Fixtures.defaultScope());

    @Test
    void referenceValueProducesResourceObjectNotLiteral() {
        ObjectId personId = ObjectId.random();
        BusinessObject task =
            task(Map.of(new PropertyRef(Fixtures.ASSIGNED_TO_IRI), Value.ref(personId)));

        Graph graph = ShaclDataGraphBuilder.build(SNAPSHOT, task);

        Node subject = NodeFactory.createURI("urn:uuid:" + task.id().value());
        Node predicate = NodeFactory.createURI(Fixtures.ASSIGNED_TO_IRI);
        Node expectedObject = NodeFactory.createURI("urn:uuid:" + personId.value());

        List<Node> objects =
            graph.find(subject, predicate, Node.ANY).mapWith(triple -> triple.getObject()).toList();
        assertThat(objects).containsExactly(expectedObject);
        assertThat(expectedObject.isURI()).isTrue();
    }

    @Test
    void rdfTypeTriplesIncludeFullTransitiveSupertypeClosure() {
        BusinessObject task = task(Map.of());

        Graph graph = ShaclDataGraphBuilder.build(SNAPSHOT, task);

        Node subject = NodeFactory.createURI("urn:uuid:" + task.id().value());
        List<Node> types =
            graph.find(subject, RDF.type.asNode(), Node.ANY).mapWith(triple -> triple.getObject()).toList();

        assertThat(types)
            .extracting(Node::getURI)
            .containsExactlyInAnyOrder(Fixtures.TASK_IRI, Fixtures.WORK_ITEM_IRI, Fixtures.DELIVERABLE_IRI);
    }

    @Test
    void estimatedHoursProducesScalePreservingXsdDecimalLiteral() {
        BigDecimal exact = new BigDecimal("40.50");
        BusinessObject task =
            task(Map.of(new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI), Value.decimal(exact)));

        Graph graph = ShaclDataGraphBuilder.build(SNAPSHOT, task);
        Model model = ModelFactory.createModelForGraph(graph);

        Node subject = NodeFactory.createURI("urn:uuid:" + task.id().value());
        Node predicate = NodeFactory.createURI(Fixtures.ESTIMATED_HOURS_IRI);
        Node literal = graph.find(subject, predicate, Node.ANY).next().getObject();

        assertThat(literal.isLiteral()).isTrue();
        assertThat(literal.getLiteralDatatypeURI()).isEqualTo("http://www.w3.org/2001/XMLSchema#decimal");
        assertThat(literal.getLiteralLexicalForm()).isEqualTo(exact.toString());
        model.close();
    }

    @Test
    void multiValuedListProducesOneTriplePerElementNotAnRdfCollection() {
        BusinessObject task =
            task(
                Map.of(
                    new PropertyRef(Fixtures.STATUS_IRI),
                    Value.list(List.of(Value.text("OPEN"), Value.text("URGENT")))));

        Graph graph = ShaclDataGraphBuilder.build(SNAPSHOT, task);

        Node subject = NodeFactory.createURI("urn:uuid:" + task.id().value());
        Node predicate = NodeFactory.createURI(Fixtures.STATUS_IRI);
        List<Node> values =
            graph.find(subject, predicate, Node.ANY).mapWith(triple -> triple.getObject()).toList();

        assertThat(values).hasSize(2);
        assertThat(values).extracting(Node::getLiteralLexicalForm).containsExactlyInAnyOrder("OPEN", "URGENT");
    }

    private static BusinessObject task(Map<PropertyRef, Value> properties) {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Audit audit = new Audit(at, "tester", at, "tester");
        return new BusinessObject(
            ObjectId.random(), new TypeRef(Fixtures.TASK_IRI), TenantId.DEFAULT, 1, Optional.empty(),
            properties, audit, false);
    }
}
