package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.meta.behaviour.DerivationRule;
import org.sequeless.spi.meta.behaviour.PluginRule;
import org.sequeless.spi.meta.behaviour.RollupRule;
import org.sequeless.spi.meta.schema.AggregateFunction;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.TypeDefinition;
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
    private static final String DUE_DATE_IRI = REF_NS + "dueDate";
    private static final String CREATED_AT_IRI = REF_NS + "createdAt";
    private static final String ARCHIVED_IRI = REF_NS + "archived";
    private static final String AMOUNT_IRI = REF_NS + "amount";

    /**
     * {@code ex:Project}/{@code ex:Task} plus the two ordinary (non-derived) properties every
     * derivation fixture below needs reachable: {@code ex:status} (a datatype property, used both
     * as a criterion target and, in one test, misused as a bogus {@code sq:via}) and {@code
     * ex:belongsToProject} (the one legitimate object property from {@code Task} back to {@code
     * Project}).
     */
    private static final String HEADER = """
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
        MappingResult result = mapTurtle("""
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
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isPresent();
        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
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
        MappingResult result = mapTurtle("""
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

        assertThat(attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).iri()).isEqualTo(OPEN_TASK_COUNT_IRI);
        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.criteria())
                .containsExactly(new Criterion(PRIORITY_IRI, Operator.GT, Optional.of(new IntegerValue(3))));
    }

    @Test
    void rollupWithoutFilterMapsToRollupRuleWithEmptyCriteriaAndForcesReadOnly() {
        MappingResult result = mapTurtle("""
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
        RollupRule rule =
                (RollupRule) derivation(result, TOTAL_ESTIMATED_HOURS_IRI).orElseThrow();
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
        MappingResult result = mapTurtle("""
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
            """);
        assertThat(errors(result)).isEmpty();

        AttributeDefinition property = attribute(result, REF_NS + "Person", REF_NS + "workload");
        DerivationRule derivation = derivation(result, REF_NS + "workload").orElseThrow();
        assertThat(derivation).isInstanceOf(PluginRule.class);
        assertThat(((PluginRule) derivation).pluginName()).isEqualTo("workload");
        assertThat(property.readOnly()).isTrue();
    }

    // -- sq:materialised (Phase 6) ----------------------------------------------------------------

    @Test
    void rollupWithoutMaterialisedDefaultsToFalse() {
        MappingResult result = mapTurtle("""
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ] .
            """);
        assertThat(errors(result)).isEmpty();

        assertThat(attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).iri()).isEqualTo(OPEN_TASK_COUNT_IRI);
        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.materialised()).isFalse();
    }

    @Test
    void rollupWithMaterialisedTrueIsHonoured() {
        MappingResult result = mapTurtle("""
            ex:openTaskCount
                a owl:DatatypeProperty ;
                rdfs:domain ex:Project ;
                rdfs:range xsd:integer ;
                sq:derivedBy [
                    a sq:Rollup ;
                    sq:function sq:count ;
                    sq:over ex:Task ;
                    sq:via ex:belongsToProject ;
                    sq:materialised true ] .
            """);
        assertThat(errors(result)).isEmpty();

        assertThat(attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).iri()).isEqualTo(OPEN_TASK_COUNT_IRI);
        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.materialised()).isTrue();
    }

    @Test
    void pluginWithoutMaterialisedDefaultsToFalse() {
        MappingResult result = mapTurtle("""
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
            """);
        assertThat(errors(result)).isEmpty();

        assertThat(attribute(result, REF_NS + "Person", REF_NS + "workload").iri())
                .isEqualTo(REF_NS + "workload");
        PluginRule rule = (PluginRule) derivation(result, REF_NS + "workload").orElseThrow();
        assertThat(rule.materialised()).isFalse();
    }

    @Test
    void pluginWithMaterialisedTrueIsError() {
        // A materialised sq:Plugin cannot be generically recomputed: unlike a sq:Rollup it carries
        // no sq:over/sq:via, so DefaultDerivationRecomputer (T5) has no generic way to discover which
        // targets a changed object affects. Rejecting the combination here, at mapping time, keeps
        // that gap from ever reaching the Behaviour half.
        MappingResult result = mapTurtle("""
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ; sq:materialised true ] .
            """);
        assertSingleError(result, REF_NS + "workload", "sq:materialised true");
    }

    // -- Rule-shape validation --------------------------------------------------------------------

    @Test
    void typedBothRollupAndPluginIsError() {
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        MappingResult result = mapTurtle("""
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
        assertSingleError(
                result, OPEN_TASK_COUNT_IRI, "is not an object property effectively declared on sq:over type");
    }

    // -- Comparisons on non-number properties (spec 11 section 11, Q4) ---------------------------

    @Test
    void ltOnDatePropertyIsErrorAndRuleIsNotLoaded() {
        // The value is deliberately not a valid date: a rejected comparison must stop before the
        // value is coerced, so the only ERROR is the comparison one.
        MappingResult result = mapTurtle("""
            ex:dueDate a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:date .
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
                                  sq:property ex:dueDate ;
                                  sq:operator sq:lt ;
                                  sq:value "not-a-date" ] ) ] .
            """);
        assertSingleError(
                result, OPEN_TASK_COUNT_IRI, "compares " + DUE_DATE_IRI + " with LT, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
    }

    @Test
    void lteOnDatePropertyIsErrorAndRuleIsNotLoaded() {
        // As in ltOnDatePropertyIsErrorAndRuleIsNotLoaded: the value is deliberately not a valid
        // date, so the only ERROR is the comparison one.
        MappingResult result = mapTurtle("""
            ex:dueDate a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:date .
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
                                  sq:property ex:dueDate ;
                                  sq:operator sq:lte ;
                                  sq:value "not-a-date" ] ) ] .
            """);
        assertSingleError(
                result, OPEN_TASK_COUNT_IRI, "compares " + DUE_DATE_IRI + " with LTE, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
    }

    @Test
    void gteOnDateTimePropertyIsError() {
        MappingResult result = mapTurtle("""
            ex:createdAt a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:dateTime .
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
                                  sq:property ex:createdAt ;
                                  sq:operator sq:gte ;
                                  sq:value "2026-01-01T00:00:00Z" ] ) ] .
            """);
        assertSingleError(
                result,
                OPEN_TASK_COUNT_IRI,
                "compares " + CREATED_AT_IRI + " with GTE, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
    }

    @Test
    void gtOnBooleanPropertyIsError() {
        MappingResult result = mapTurtle("""
            ex:archived a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:boolean .
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
                                  sq:property ex:archived ;
                                  sq:operator sq:gt ;
                                  sq:value "true" ] ) ] .
            """);
        assertSingleError(
                result, OPEN_TASK_COUNT_IRI, "compares " + ARCHIVED_IRI + " with GT, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
    }

    @Test
    void gtOnObjectPropertyIsError() {
        MappingResult result = mapTurtle("""
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
                                  sq:property ex:belongsToProject ;
                                  sq:operator sq:gt ;
                                  sq:value "3f2c9a1e-0000-4000-8000-000000000001" ] ) ] .
            """);
        assertSingleError(
                result,
                OPEN_TASK_COUNT_IRI,
                "compares " + BELONGS_TO_PROJECT_IRI + " with GT, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
    }

    @Test
    void gtOnStringTimeDurationOrAnyUriPropertyIsErrorAndRuleIsNotLoaded() {
        // A rejected comparison stops before the value is coerced, so the value need not parse for
        // the datatype: the only ERROR is the comparison one.
        for (String range : List.of("xsd:string", "xsd:time", "xsd:duration", "xsd:anyURI")) {
            MappingResult result = mapTurtle("""
                ex:amount a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range %s .
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
                                      sq:property ex:amount ;
                                      sq:operator sq:gt ;
                                      sq:value "3" ] ) ] .
                """.formatted(range));
            assertThat(errors(result)).as(range).hasSize(1);
            assertThat(errors(result).get(0).message())
                    .as(range)
                    .contains("compares " + AMOUNT_IRI + " with GT, which needs a numeric property");
            assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).as(range).isEmpty();
        }
    }

    @Test
    void comparisonOnNumberPropertyStillLoadsWithNoError() {
        for (String range : List.of("xsd:integer", "xsd:long", "xsd:decimal", "xsd:double")) {
            MappingResult result = mapTurtle("""
                ex:amount a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range %s .
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
                                      sq:property ex:amount ;
                                      sq:operator sq:gt ;
                                      sq:value "3" ] ) ] .
                """.formatted(range));
            assertThat(errors(result)).as(range).isEmpty();

            RollupRule rule =
                    (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
            assertThat(rule.criteria()).as(range).hasSize(1);
            assertThat(rule.criteria().get(0).property()).as(range).isEqualTo(AMOUNT_IRI);
            assertThat(rule.criteria().get(0).operator()).as(range).isEqualTo(Operator.GT);
        }
    }

    @Test
    void comparisonOnIntegerAndAnyUriRangePropertyStillLoadsWithNoError() {
        // The snapshot types this property INTEGER (mostSpecificXsdIri prefers the integral range),
        // even though xsd:anyURI sorts first. The comparison check must agree with the snapshot.
        MappingResult result = mapTurtle("""
            ex:amount a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:integer, xsd:anyURI .
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
                                  sq:property ex:amount ;
                                  sq:operator sq:gt ;
                                  sq:value "3" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.criteria()).hasSize(1);
        assertThat(rule.criteria().get(0).property()).isEqualTo(AMOUNT_IRI);
        assertThat(rule.criteria().get(0).operator()).isEqualTo(Operator.GT);
    }

    @Test
    void eqOnDatePropertyStillLoadsWithNoError() {
        MappingResult result = mapTurtle("""
            ex:dueDate a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:date .
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
                                  sq:property ex:dueDate ;
                                  sq:operator sq:eq ;
                                  sq:value "2026-12-01" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.criteria())
                .containsExactly(
                        new Criterion(DUE_DATE_IRI, Operator.EQ, Optional.of(Value.date(LocalDate.of(2026, 12, 1)))));
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
        MappingResult result = mapTurtleUnderReasoner("""
            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
            """, ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        assertThat(attribute(result, REF_NS + "Person", REF_NS + "workload").iri())
                .isEqualTo(REF_NS + "workload");
        DerivationRule derivation = derivation(result, REF_NS + "workload").orElseThrow();
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
        MappingResult result = mapTurtleUnderReasoner("""
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
            """, ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        assertThat(attribute(result, PROJECT_IRI, OPEN_TASK_COUNT_IRI).iri()).isEqualTo(OPEN_TASK_COUNT_IRI);
        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.sourceTypeIri()).isEqualTo(TASK_IRI);
        assertThat(rule.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(rule.function()).isEqualTo(AggregateFunction.COUNT);
        assertThat(rule.criteria())
                .containsExactly(new Criterion(STATUS_IRI, Operator.NE, Optional.of(new TextValue("done"))));
    }

    /** The integer-plus-anyURI regression, under the reasoner the running application uses. */
    @Test
    void comparisonOnIntegerAndAnyUriRangePropertyStillLoadsUnderTheOwlReasoner() {
        MappingResult result = mapTurtleUnderReasoner("""
            ex:amount a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:integer, xsd:anyURI .
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
                                  sq:property ex:amount ;
                                  sq:operator sq:gt ;
                                  sq:value "3" ] ) ] .
            """, ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        RollupRule rule = (RollupRule) derivation(result, OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(rule.criteria()).hasSize(1);
        assertThat(rule.criteria().get(0).property()).isEqualTo(AMOUNT_IRI);
        assertThat(rule.criteria().get(0).operator()).isEqualTo(Operator.GT);
    }

    /** The LTE-on-DATE rejection, under the reasoner the running application uses. */
    @Test
    void lteOnDatePropertyIsErrorUnderTheOwlReasoner() {
        MappingResult result = mapTurtleUnderReasoner("""
            ex:dueDate a owl:DatatypeProperty ; rdfs:domain ex:Task ; rdfs:range xsd:date .
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
                                  sq:property ex:dueDate ;
                                  sq:operator sq:lte ;
                                  sq:value "not-a-date" ] ) ] .
            """, ReasonerSetting.OWL);
        assertSingleError(
                result, OPEN_TASK_COUNT_IRI, "compares " + DUE_DATE_IRI + " with LTE, which needs a numeric property");
        assertThat(derivation(result, OPEN_TASK_COUNT_IRI)).isEmpty();
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
        return result.issues().stream()
                .filter(issue -> issue.severity() == Severity.ERROR)
                .toList();
    }

    private static void assertSingleError(MappingResult result, String propertyIri, String messageFragment) {
        List<OntologyIssue> errors = errors(result);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).subjectIri()).contains(propertyIri);
        assertThat(errors.get(0).message()).contains(messageFragment);
    }

    /** The derivation rule of {@code propertyIri}, as the behaviour half holds it. */
    private static Optional<DerivationRule> derivation(MappingResult result, String propertyIri) {
        return Optional.ofNullable(result.derivations().get(propertyIri));
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
