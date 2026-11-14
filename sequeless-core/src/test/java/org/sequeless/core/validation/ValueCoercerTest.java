package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.validation.Violation;

/**
 * Unit tests for {@link ValueCoercer}. The {@code Task} fixture is built directly from SPI records
 * — no ontology adapter is available in {@code sequeless-core} — mirroring how {@code
 * DefaultMetaModelServiceTest} builds its own {@code TypeDefinition}s by hand.
 */
class ValueCoercerTest {

    private static final String NS = "https://sequeless.dev/ns/ref#";
    private static final String TITLE_IRI = NS + "title";
    private static final String PRIORITY_IRI = NS + "priority";
    private static final String ESTIMATED_HOURS_IRI = NS + "estimatedHours";
    private static final String DUE_DATE_IRI = NS + "dueDate";
    private static final String TAGS_IRI = NS + "tags";
    private static final String ASSIGNED_TO_IRI = NS + "assignedTo";
    private static final String CREATED_AT_IRI = NS + "createdAt";
    private static final String PERSON_IRI = NS + "Person";
    private static final String OPEN_TASK_COUNT_IRI = NS + "openTaskCount";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String STATUS_IRI = NS + "status";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";

    private static final AttributeDefinition TITLE =
            attribute(TITLE_IRI, Cardinality.range(1, 1), Datatype.STRING, false);
    private static final AttributeDefinition PRIORITY =
            attribute(PRIORITY_IRI, Cardinality.atMost(1), Datatype.INTEGER, false);
    private static final AttributeDefinition ESTIMATED_HOURS =
            attribute(ESTIMATED_HOURS_IRI, Cardinality.atMost(1), Datatype.DECIMAL, false);
    private static final AttributeDefinition DUE_DATE =
            attribute(DUE_DATE_IRI, Cardinality.atMost(1), Datatype.DATE, false);
    private static final AttributeDefinition TAGS =
            attribute(TAGS_IRI, Cardinality.atMost(Integer.MAX_VALUE), Datatype.STRING, false);
    private static final RelationshipDefinition ASSIGNED_TO = new RelationshipDefinition(
            ASSIGNED_TO_IRI,
            "Assigned To",
            Cardinality.atMost(1),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            PERSON_IRI,
            Optional.empty(),
            false);
    private static final AttributeDefinition CREATED_AT =
            attribute(CREATED_AT_IRI, Cardinality.atMost(1), Datatype.DATE_TIME, true);
    private static final AttributeDefinition OPEN_TASK_COUNT = new AttributeDefinition(
            OPEN_TASK_COUNT_IRI,
            "Open Tasks",
            Cardinality.atMost(1),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Datatype.INTEGER);

    /**
     * {@code sq:Rollup}-derived: {@code count} of {@code Task} via {@code belongsToProject},
     * filtered to {@code status != "done"}. The rule lives in the {@link Behaviour}, not on the
     * {@link AttributeDefinition}; it mirrors the {@link RollupRule} {@code
     * QueryFixtures.behaviour()} carries for {@code openTaskCount}.
     */
    private static final RollupRule OPEN_TASK_COUNT_RULE = new RollupRule(
            NS + "Task",
            BELONGS_TO_PROJECT_IRI,
            AggregateFunction.COUNT,
            Optional.empty(),
            Optional.of(new FieldFilter(STATUS_IRI, "is not", TextNode.valueOf("done"))),
            false);

    /**
     * The behaviour every coercion in this class runs against. {@code openTaskCount} is the only
     * derived property, so one behaviour serves every test.
     */
    private static final Behaviour BEHAVIOUR =
            new Behaviour(new OntologyVersion(1), Map.of(), Map.of(OPEN_TASK_COUNT_IRI, OPEN_TASK_COUNT_RULE));

    private static final TypeDefinition TASK = new TypeDefinition(
            NS + "Task",
            "Task",
            List.of(),
            List.of(TITLE, PRIORITY, ESTIMATED_HOURS, DUE_DATE, TAGS, ASSIGNED_TO, CREATED_AT),
            DisplayHints.none(),
            false);

    private static final TypeDefinition PROJECT =
            new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(OPEN_TASK_COUNT), DisplayHints.none(), false);

    private static AttributeDefinition attribute(
            String iri, Cardinality cardinality, Datatype datatype, boolean readOnly) {
        return new AttributeDefinition(
                iri, iri, cardinality, false, false, false, readOnly, false, DisplayHints.none(), datatype);
    }

    // --- scalar datatypes ---

    @Test
    void coercesStringProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("title", "Write plan"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).containsEntry(new PropertyRef(TITLE_IRI), new TextValue("Write plan"));
    }

    @Test
    void coercesIntegerProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("priority", 2));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).containsEntry(new PropertyRef(PRIORITY_IRI), new IntegerValue(2));
    }

    @Test
    void coercesDecimalPropertyFromBigDecimalKeepingScale() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("estimatedHours", new BigDecimal("12.50")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
                .containsEntry(new PropertyRef(ESTIMATED_HOURS_IRI), new DecimalValue(new BigDecimal("12.50")));
    }

    @Test
    void coercesDecimalPropertyFromNumericStringKeepingScale() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("estimatedHours", "12.50"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
                .containsEntry(new PropertyRef(ESTIMATED_HOURS_IRI), new DecimalValue(new BigDecimal("12.50")));
    }

    @Test
    void coercesBooleanProperty() {
        AttributeDefinition flag = attribute(NS + "flag", Cardinality.atMost(1), Datatype.BOOLEAN, false);
        TypeDefinition type = new TypeDefinition(NS + "T", "T", List.of(), List.of(flag), DisplayHints.none(), false);

        CoercionResult result = ValueCoercer.coerce(type, BEHAVIOUR, Map.of("flag", true));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).containsEntry(new PropertyRef(NS + "flag"), new BoolValue(true));
    }

    @Test
    void coercesDateProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("dueDate", "2026-10-01"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
                .containsEntry(new PropertyRef(DUE_DATE_IRI), new DateValue(LocalDate.parse("2026-10-01")));
    }

    @Test
    void coercesDateTimeProperty() {
        AttributeDefinition writable = attribute(NS + "occurredAt", Cardinality.atMost(1), Datatype.DATE_TIME, false);
        TypeDefinition type =
                new TypeDefinition(NS + "T", "T", List.of(), List.of(writable), DisplayHints.none(), false);

        CoercionResult result = ValueCoercer.coerce(type, BEHAVIOUR, Map.of("occurredAt", "2026-09-16T10:00:00Z"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
                .containsEntry(
                        new PropertyRef(NS + "occurredAt"), new DateTimeValue(Instant.parse("2026-09-16T10:00:00Z")));
    }

    @Test
    void coercesListProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("tags", List.of("a", "b")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
                .containsEntry(
                        new PropertyRef(TAGS_IRI), new ListValue(List.of(new TextValue("a"), new TextValue("b"))));
    }

    @Test
    void coercesRelationshipProperty() {
        ObjectId personId = ObjectId.random();

        CoercionResult result = ValueCoercer.coerce(
                TASK, BEHAVIOUR, Map.of("assignedTo", personId.value().toString()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).containsEntry(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(personId));
    }

    // --- unknown / read-only ---

    @Test
    void unknownPropertyProducesViolationWithRawKeyAsPath() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("bogus", "x"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo("bogus");
    }

    @Test
    void readOnlyPropertySuppliedByCallerProducesViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("createdAt", "2026-09-16T10:00:00Z"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo(CREATED_AT_IRI);
    }

    @Test
    void readOnlyCheckShortCircuitsBeforeDatatypeCheckProducingExactlyOneViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("createdAt", "not-iso"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo(CREATED_AT_IRI);
    }

    @Test
    void derivedPropertySuppliedByCallerProducesViolationNamingItAsDerived() {
        CoercionResult plainReadOnly =
                ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("createdAt", "2026-09-16T10:00:00Z"));
        CoercionResult derived = ValueCoercer.coerce(PROJECT, BEHAVIOUR, Map.of("openTaskCount", 3));

        assertThat(plainReadOnly.isSuccess()).isFalse();
        assertThat(derived.isSuccess()).isFalse();
        assertThat(derived.violations()).hasSize(1);
        Violation violation = derived.violations().get(0);
        assertThat(violation.path()).isEqualTo(OPEN_TASK_COUNT_IRI);
        assertThat(violation.message()).contains("is a derived property and cannot be set directly");
        assertThat(violation.message())
                .isNotEqualTo(plainReadOnly.violations().get(0).message());
    }

    // --- datatype mismatches ---

    @Test
    void datatypeMismatchForInteger() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("priority", "not a number"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(PRIORITY_IRI);
    }

    @Test
    void datatypeMismatchForString() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("title", true));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TITLE_IRI);
    }

    @Test
    void datatypeMismatchForDate() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("dueDate", "not-iso"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(DUE_DATE_IRI);
    }

    @Test
    void datatypeMismatchForDateTimeWithoutOffset() {
        AttributeDefinition writable = attribute(NS + "occurredAt", Cardinality.atMost(1), Datatype.DATE_TIME, false);
        TypeDefinition type =
                new TypeDefinition(NS + "T", "T", List.of(), List.of(writable), DisplayHints.none(), false);

        CoercionResult result = ValueCoercer.coerce(type, BEHAVIOUR, Map.of("occurredAt", "2026-09-16T10:00:00"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(NS + "occurredAt");
    }

    // --- scalar vs list mismatches ---

    @Test
    void listForScalarPropertyIsAViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("title", List.of("a", "b")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TITLE_IRI);
    }

    @Test
    void scalarForListPropertyIsAViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("tags", "not-a-list"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TAGS_IRI);
    }

    @Test
    void nestedListProducesViolationNotAnException() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("tags", List.of(List.of("a", "b"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TAGS_IRI);
    }

    // --- null handling and boundary of StructuralValidator's job ---

    @Test
    void nullRawValueMeansAbsentEvenForRequiredProperty() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("title", null);

        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, raw);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).isEmpty();
    }

    @Test
    void emptyRawPropertiesSucceedsWithEmptyProperties() {
        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).isEmpty();
    }

    // --- short name vs IRI ---

    @Test
    void shortNameAndFullIriBothResolveToTheSamePropertyRefKeyedByFullIri() {
        CoercionResult byShortName = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of("title", "a"));
        CoercionResult byIri = ValueCoercer.coerce(TASK, BEHAVIOUR, Map.of(TITLE_IRI, "a"));

        assertThat(byShortName.properties().keySet()).containsExactly(new PropertyRef(TITLE_IRI));
        assertThat(byIri.properties().keySet()).containsExactly(new PropertyRef(TITLE_IRI));
    }

    // --- multiple violations collected ---

    @Test
    void multipleViolationsAcrossKeysAreAllCollected() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("bogus", "x");
        raw.put("priority", "not a number");
        raw.put("createdAt", "y");

        CoercionResult result = ValueCoercer.coerce(TASK, BEHAVIOUR, raw);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(3);
    }

    // --- null arguments ---

    @Test
    void coerceRejectsNullType() {
        assertThatNullPointerException().isThrownBy(() -> ValueCoercer.coerce(null, BEHAVIOUR, Map.of()));
    }

    @Test
    void coerceRejectsNullRawProperties() {
        assertThatNullPointerException().isThrownBy(() -> ValueCoercer.coerce(TASK, BEHAVIOUR, null));
    }
}
