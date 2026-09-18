package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.DerivationRule;
import org.sequeless.spi.meta.PluginRule;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * Unit-tests {@link SnapshotMapper}'s {@code sq:derivedBy} parsing in isolation, against small
 * inline Turtle fixtures rather than {@code reference.ttl}: a malformed derivation rule must never
 * live in the known-good reference ontology, and T5 has not yet added the real {@code ex:Project}
 * rollups there. Covers the two well-formed shapes ({@code sq:Rollup} with and without {@code
 * sq:filter}, {@code sq:Plugin}), the read-only side effect of {@code sq:derivedBy}, and one test
 * per rule-shape validation in {@code sq-vocabulary.md}'s "OWL / sq: construct" table.
 */
class DerivationMappingTest {

    private static final String REF_NS = "https://sequeless.dev/ns/ref#";
    private static final String PROJECT_IRI = REF_NS + "Project";
    private static final String TASK_IRI = REF_NS + "Task";
    private static final String STATUS_IRI = REF_NS + "status";
    private static final String BELONGS_TO_PROJECT_IRI = REF_NS + "belongsToProject";
    private static final String ESTIMATED_HOURS_IRI = REF_NS + "estimatedHours";
    private static final String PRIORITY_IRI = REF_NS + "priority";
    private static final String OPEN_TASK_COUNT_IRI = REF_NS + "openTaskCount";
    private static final String TOTAL_ESTIMATED_HOURS_IRI = REF_NS + "totalEstimatedHours";

    /**
     * {@code ex:Project}/{@code ex:Task} plus the two ordinary (non-derived) properties every
     * derivation fixture below needs reachable: {@code ex:status} (a datatype property, used both
     * as a criterion target and, in one test, misused as a bogus {@code sq:via}) and {@code
     * ex:belongsToProject} (the one legitimate object property from {@code Task} back to {@code
     * Project}).
     */
    private static final String HEADER =
        """
        @prefix sq:   <https://sequeless.dev/ns/meta#> .
        @prefix owl:  <http://www.w3.org/2002/07/owl#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
        @prefix ex:   <https://sequeless.dev/ns/ref#> .

        <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

        ex:Project a owl:Class .
        ex:Task a owl:Class .
        ex:Person a owl:Class .

        ex:status a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:string .
        ex:belongsToProject a owl:ObjectProperty ; rdfs:domain ex:Task ; rdfs:range ex:Project .
        ex:estimatedHours a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:decimal .
        ex:priority a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:integer .

        """;

    // -- Well-formed shapes ---------------------------------------------------------------------

    @Test
    void rollupWithFilterMapsToRollupRule() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ;
                                  sq:property ex:status ;
                                  sq:operator sq:ne ;
                                  sq:value "done" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        AttributeDefinition property = attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI);
        assertThat(property.derivation()).isPresent();
        RollupRule rule = (RollupRule) property.derivation().orElseThrow();
        assertThat(rule.sourceTypeIri()).isEqualTo(TASK_IRI);
        assertThat(rule.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(rule.function()).isEqualTo(AggregateFunction.COUNT);
        assertThat(rule.ofPropertyIri()).isEmpty();
        assertThat(rule.criteria())
            .containsExactly(new Criterion(STATUS_IRI, Operator.NE, Optional.of(new TextValue("done"))));
        assertThat(property.readOnly()).isTrue();
    }

    @Test
    void criterionAgainstIntegerPropertyCoercesToIntegerValueNotText() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ;
                                  sq:property ex:priority ;
                                  sq:operator sq:gt ;
                                  sq:value "3" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        RollupRule rule =
            (RollupRule) attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).derivation().orElseThrow();
        assertThat(rule.criteria())
            .containsExactly(new Criterion(PRIORITY_IRI, Operator.GT, Optional.of(new IntegerValue(3))));
    }

    @Test
    void rollupWithoutFilterMapsToRollupRuleWithEmptyCriteriaAndForcesReadOnly() {
        MappingResult result = mapTurtle(
            """
            ex:totalEstimatedHours
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:sum ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:of ex:estimatedHours ] .
            """);
        assertThat(errors(result)).isEmpty();

        AttributeDefinition property = attribute(result, PROJECT_IRI, TOTAL_ESTIMATED_HOURS_IRI);
        RollupRule rule = (RollupRule) property.derivation().orElseThrow();
        assertThat(rule.sourceTypeIri()).isEqualTo(TASK_IRI);
        assertThat(rule.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(rule.function()).isEqualTo(AggregateFunction.SUM);
        assertThat(rule.ofPropertyIri()).hasValue(ESTIMATED_HOURS_IRI);
        assertThat(rule.criteria()).isEmpty();

        // No sq:readOnly asserted anywhere on this property; sq:derivedBy alone forces it.
        assertThat(property.readOnly()).isTrue();
    }

    @Test
    void pluginMapsToPluginRule() {
        MappingResult result = mapTurtle(
            """
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
            """);
        assertThat(errors(result)).isEmpty();

        AttributeDefinition property = attribute(result, REF_NS + "Person", REF_NS + "workload");
        DerivationRule derivation = property.derivation().orElseThrow();
        assertThat(derivation).isInstanceOf(PluginRule.class);
        assertThat(((PluginRule) derivation).pluginName()).isEqualTo("workload");
        assertThat(property.readOnly()).isTrue();
    }

    // -- Rule-shape validation --------------------------------------------------------------------

    @Test
    void typedBothRollupAndPluginIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup, sq:Plugin ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:pluginName "x" ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "typed both sq:Rollup and sq:Plugin");
    }

    @Test
    void typedNeitherIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "must be typed sq:Rollup or sq:Plugin");
    }

    @Test
    void missingFunctionIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:function");
    }

    @Test
    void unknownFunctionIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:median ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "unknown sq:function 'median'");
    }

    @Test
    void missingOverIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:via ex:belongsToProject ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:over");
    }

    @Test
    void missingViaIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:via");
    }

    @Test
    void ofMissingForSumIsError() {
        MappingResult result = mapTurtle(
            """
            ex:totalEstimatedHours
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:sum ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ] .
            """);
        assertSingleError(result, TOTAL_ESTIMATED_HOURS_IRI, "missing sq:of");
    }

    @Test
    void ofPresentForCountIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:of ex:estimatedHours ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "must not declare sq:of");
    }

    @Test
    void missingPluginNameIsError() {
        MappingResult result = mapTurtle(
            """
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ] .
            """);
        assertSingleError(result, REF_NS + "workload", "missing sq:pluginName");
    }

    @Test
    void criterionMissingPropertyIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ; sq:operator sq:ne ; sq:value "done" ] ) ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:property");
    }

    @Test
    void criterionMissingOperatorIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ; sq:property ex:status ; sq:value "done" ] ) ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:operator");
    }

    @Test
    void criterionValuePresentForIsNullIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ; sq:property ex:status ; sq:operator sq:isNull ;
                                  sq:value "done" ] ) ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "must not declare sq:value for IS_NULL");
    }

    @Test
    void criterionValueAbsentForNonNullOperatorIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ; sq:property ex:status ; sq:operator sq:ne ] ) ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "missing sq:value");
    }

    @Test
    void criterionValueThatDoesNotParseForDeclaredDatatypeIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ;
                                  sq:property ex:priority ;
                                  sq:operator sq:gt ;
                                  sq:value "abc" ] ) ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI, "is not a valid INTEGER for property");
    }

    @Test
    void viaNotObjectPropertyOnOverTypeIsError() {
        MappingResult result = mapTurtle(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:status ] .
            """);
        assertSingleError(result, OPEN_TASK_COUNT_IRI,
            "is not an object property effectively declared on sq:over type");
    }

    // -- Reasoner interaction --------------------------------------------------------------------

    /**
     * Regression test for a real bug this phase shipped and then fixed: {@code sq:derivedBy} used
     * to assert {@code rdfs:range sq:Rollup} (one arm of its real union range {@code sq:Rollup} ∪
     * {@code sq:Plugin}, which RDFS cannot express directly). Under {@link ReasonerSetting#OWL} —
     * the setting the running application actually uses, per {@code JenaOntologyProperties}'s
     * default — Jena's rule-based reasoner applied the standard {@code rdfs:range} entailment rule
     * to every {@code sq:derivedBy} statement, adding {@code rdf:type sq:Rollup} to genuine {@code
     * sq:Plugin} nodes too, which made {@code derivationOf}'s "typed both/neither" check see every
     * plug-in rule as typed both and reject it — a plug-in that only worked under {@code
     * reasoner: none} would never satisfy this phase's acceptance criterion, since the app runs
     * {@code reasoner: owl}. The fix (this vocabulary's Turtle file) removes that {@code rdfs:range}
     * assertion entirely rather than trying to express the union; this test proves the entailment
     * is actually gone, not just that {@link #mapTurtle} (pinned to {@link ReasonerSetting#NONE},
     * see its own javadoc) continues to pass.
     */
    @Test
    void pluginMapsToPluginRuleUnderTheOwlReasonerTheAppActuallyUses() {
        MappingResult result = mapTurtleUnderReasoner(
            """
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
            """,
            ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        AttributeDefinition property = attribute(result, REF_NS + "Person", REF_NS + "workload");
        DerivationRule derivation = property.derivation().orElseThrow();
        assertThat(derivation).isInstanceOf(PluginRule.class);
        assertThat(((PluginRule) derivation).pluginName()).isEqualTo("workload");
    }

    /**
     * Symmetry check: a {@code sq:Rollup} rule was never at risk from the {@code sq:derivedBy}
     * range hazard above (asserting {@code sq:Rollup} could only ever spuriously *add* the type a
     * genuine rollup node already has), but proving it still maps correctly under {@link
     * ReasonerSetting#OWL} closes out the "does this whole feature work under the app's actual
     * configuration" question this bug raised, for both rule shapes.
     */
    @Test
    void rollupMapsToRollupRuleUnderTheOwlReasonerTheAppActuallyUses() {
        MappingResult result = mapTurtleUnderReasoner(
            """
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:filter ( [ a sq:Criterion ;
                                  sq:property ex:status ;
                                  sq:operator sq:ne ;
                                  sq:value "done" ] ) ] .
            """,
            ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        RollupRule rule =
            (RollupRule) attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).derivation().orElseThrow();
        assertThat(rule.sourceTypeIri()).isEqualTo(TASK_IRI);
        assertThat(rule.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(rule.function()).isEqualTo(AggregateFunction.COUNT);
        assertThat(rule.criteria())
            .containsExactly(new Criterion(STATUS_IRI, Operator.NE, Optional.of(new TextValue("done"))));
    }

    // -- Fixtures ---------------------------------------------------------------------------------

    /**
     * Pinned to {@link ReasonerSetting#NONE} for every rule-shape validation test in this class,
     * deliberately: these tests assert {@code SnapshotMapper}'s parsing and validation logic in
     * isolation from reasoner-entailed triples, which is what makes {@link #assertSingleError}
     * reliable — an entailed triple could otherwise turn a deliberately-malformed fixture into a
     * well-formed one, or vice versa, for reasons unrelated to what each test actually means to
     * exercise (see the {@code sq:derivedBy}/{@code rdfs:range} bug the reasoner-interaction tests
     * below regression-test: under {@link ReasonerSetting#OWL} a fixture built here would once have
     * spuriously failed the "sq:Plugin" tests with an unrelated "typed both sq:Rollup and sq:Plugin"
     * error). This proves the parsing/validation contract only; it does not by itself prove the
     * mapping still holds under the reasoner setting the running application actually configures —
     * that is what the OWL-reasoner tests above are for.
     */
    private static MappingResult mapTurtle(String derivedByBlock) {
        return mapTurtleUnderReasoner(derivedByBlock, ReasonerSetting.NONE);
    }

    private static MappingResult mapTurtleUnderReasoner(String derivedByBlock, ReasonerSetting reasoner) {
        OntModel model = buildModel(HEADER + derivedByBlock, reasoner);
        return SnapshotMapper.map(model);
    }

    private static OntModel buildModel(String turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(
            rdfModel,
            new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
            "https://sequeless.dev/ns/ref",
            Lang.TURTLE);
        return OntModelFactory.createModel(
            rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }

    private static List<OntologyIssue> errors(MappingResult result) {
        return result.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).toList();
    }

    private static void assertSingleError(MappingResult result, String propertyIri, String messageFragment) {
        List<OntologyIssue> errors = errors(result);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).subjectIri()).contains(propertyIri);
        assertThat(errors.get(0).message()).contains(messageFragment);
    }

    private static AttributeDefinition attribute(MappingResult result, String typeIri, String propertyIri) {
        TypeDefinition type = result.types().stream()
            .filter(t -> t.iri().equals(typeIri))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No type mapped for " + typeIri));
        PropertyDefinition property = type.properties().stream()
            .filter(p -> p.iri().equals(propertyIri))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No property " + propertyIri + " on " + typeIri));
        return (AttributeDefinition) property;
    }
}
