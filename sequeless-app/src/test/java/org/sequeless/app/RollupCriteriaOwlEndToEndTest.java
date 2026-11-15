package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.core.derivation.FilterNodeToCriterion;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.api.OntologyDocument;
import org.sequeless.ontology.api.OntologyFormat;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.jena.JenaOntologyPort;
import org.sequeless.ontology.jena.ReasonerSetting;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * Runs the criteria bridge end to end against the released loader under the OWL reasoner, the
 * prototype's default. The rollup's {@code sq:filter} compares an {@code xsd:integer} property with
 * the integer 3, and the criteria bridge converts the filter that the released {@link
 * JenaOntologyPort} hands over. The released loader ({@code ontology-jena-adapter} 0.1.1, from
 * sequeless-ontology commit 10f5a57, PR #15) coerces {@code sq:value} to the most specific range, the
 * same selection the snapshot's {@code mostSpecificXsdIri} makes, so the criterion's value is the
 * integer 3.
 *
 * <p>It lives in {@code sequeless-app} because that is the only module that sees core, the released
 * loader and the Jena wiring together. {@code sequeless-adapter-ontology-jena} may not depend on core.
 */
class RollupCriteriaOwlEndToEndTest {

    private static final String ONT = "https://example.org/ont#";
    private static final String URGENT_TASK_COUNT = ONT + "urgentTaskCount";
    private static final String PRIORITY = ONT + "priority";

    private static final String TURTLE = """
            @prefix ex:   <https://example.org/ont#> .
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            <https://example.org/ont>
                a owl:Ontology ;
                owl:imports <https://sequeless.dev/ns/meta> .

            ex:Task a owl:Class ;
                sq:label "Task" .

            ex:Project a owl:Class ;
                sq:label "Project" .

            ex:priority
                a owl:DatatypeProperty ;
                rdfs:domain ex:Task ;
                rdfs:range xsd:integer ;
                sq:label "Priority" .

            ex:belongsToProject
                a owl:ObjectProperty ;
                rdfs:domain ex:Task ;
                rdfs:range ex:Project ;
                sq:label "Belongs To Project" .

            ex:urgentTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:label "Urgent Tasks" ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ;
                                  sq:property ex:priority ;
                                  sq:operator sq:gt ;
                                  sq:value 3 ] ) ] .
            """;

    @Test
    void owlLoaderFilterBecomesOneGreaterThanCriterionOverTheIntegerThree() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                new OntologyDocument(TURTLE, OntologyFormat.TURTLE), ReasonerSetting.OWL, () -> Set.of());
        Schema schema = port.schema(TenantId.DEFAULT);
        Behaviour behaviour = port.behaviour(TenantId.DEFAULT);
        RollupRule rule = (RollupRule) behaviour.derivations().get(URGENT_TASK_COUNT);
        assertThat(rule)
                .as("the released loader must produce a RollupRule for %s", URGENT_TASK_COUNT)
                .isNotNull();

        FilterNode filter = rule.criteria().orElseThrow();
        List<Criterion> criteria = FilterNodeToCriterion.toCriteria(filter, schema, rule.sourceTypeIri());

        assertThat(criteria).hasSize(1);
        Criterion criterion = criteria.get(0);
        assertThat(criterion.property()).isEqualTo(PRIORITY);
        assertThat(criterion.operator()).isEqualTo(Operator.GT);
        // The value is an integer because the released loader coerces to the most specific range (sequeless-ontology
        // 10f5a57, #15), which agrees with the snapshot.
        assertThat(criterion.value()).contains(Value.integer(3));
    }
}
