package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;

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
    private static final RelationshipDefinition ASSIGNED_TO =
        new RelationshipDefinition(
            ASSIGNED_TO_IRI,
            "Assigned To",
            Cardinality.atMost(1),
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Optional.empty(),
            PERSON_IRI,
            Optional.empty(),
            false);
    private static final AttributeDefinition CREATED_AT =
        attribute(CREATED_AT_IRI, Cardinality.atMost(1), Datatype.DATE_TIME, true);

    private static final TypeDefinition TASK =
        new TypeDefinition(
            NS + "Task",
            "Task",
            List.of(),
            List.of(TITLE, PRIORITY, ESTIMATED_HOURS, DUE_DATE, TAGS, ASSIGNED_TO, CREATED_AT),
            DisplayHints.none(),
            false,
            Optional.empty());

    private static AttributeDefinition attribute(
        String iri, Cardinality cardinality, Datatype datatype, boolean readOnly) {
        return new AttributeDefinition(
            iri,
            iri,
            cardinality,
            false,
            false,
            false,
            readOnly,
            DisplayHints.none(),
            Optional.empty(),
            datatype);
    }

    // --- scalar datatypes ---

    @Test
    void coercesStringProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("title", "Write plan"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(new PropertyRef(TITLE_IRI), new TextValue("Write plan"));
    }

    @Test
    void coercesIntegerProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("priority", 2));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(new PropertyRef(PRIORITY_IRI), new IntegerValue(2));
    }

    @Test
    void coercesDecimalPropertyFromBigDecimalKeepingScale() {
        CoercionResult result =
            ValueCoercer.coerce(TASK, Map.of("estimatedHours", new BigDecimal("12.50")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(
                new PropertyRef(ESTIMATED_HOURS_IRI), new DecimalValue(new BigDecimal("12.50")));
    }

    @Test
    void coercesDecimalPropertyFromNumericStringKeepingScale() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("estimatedHours", "12.50"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(
                new PropertyRef(ESTIMATED_HOURS_IRI), new DecimalValue(new BigDecimal("12.50")));
    }

    @Test
    void coercesBooleanProperty() {
        AttributeDefinition flag = attribute(NS + "flag", Cardinality.atMost(1), Datatype.BOOLEAN, false);
        TypeDefinition type =
            new TypeDefinition(
                NS + "T", "T", List.of(), List.of(flag), DisplayHints.none(), false, Optional.empty());

        CoercionResult result = ValueCoercer.coerce(type, Map.of("flag", true));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(new PropertyRef(NS + "flag"), new BoolValue(true));
    }

    @Test
    void coercesDateProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("dueDate", "2026-10-01"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(new PropertyRef(DUE_DATE_IRI), new DateValue(LocalDate.parse("2026-10-01")));
    }

    @Test
    void coercesDateTimeProperty() {
        AttributeDefinition writable =
            attribute(NS + "occurredAt", Cardinality.atMost(1), Datatype.DATE_TIME, false);
        TypeDefinition type =
            new TypeDefinition(
                NS + "T",
                "T",
                List.of(),
                List.of(writable),
                DisplayHints.none(),
                false,
                Optional.empty());

        CoercionResult result =
            ValueCoercer.coerce(type, Map.of("occurredAt", "2026-09-16T10:00:00Z"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(
                new PropertyRef(NS + "occurredAt"),
                new DateTimeValue(Instant.parse("2026-09-16T10:00:00Z")));
    }

    @Test
    void coercesListProperty() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("tags", List.of("a", "b")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(
                new PropertyRef(TAGS_IRI),
                new ListValue(List.of(new TextValue("a"), new TextValue("b"))));
    }

    @Test
    void coercesRelationshipProperty() {
        ObjectId personId = ObjectId.random();

        CoercionResult result =
            ValueCoercer.coerce(TASK, Map.of("assignedTo", personId.value().toString()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties())
            .containsEntry(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(personId));
    }

    // --- unknown / read-only ---

    @Test
    void unknownPropertyProducesViolationWithRawKeyAsPath() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("bogus", "x"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo("bogus");
    }

    @Test
    void readOnlyPropertySuppliedByCallerProducesViolation() {
        CoercionResult result =
            ValueCoercer.coerce(TASK, Map.of("createdAt", "2026-09-16T10:00:00Z"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo(CREATED_AT_IRI);
    }

    @Test
    void readOnlyCheckShortCircuitsBeforeDatatypeCheckProducingExactlyOneViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("createdAt", "not-iso"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).path()).isEqualTo(CREATED_AT_IRI);
    }

    // --- datatype mismatches ---

    @Test
    void datatypeMismatchForInteger() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("priority", "not a number"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(PRIORITY_IRI);
    }

    @Test
    void datatypeMismatchForString() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("title", true));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TITLE_IRI);
    }

    @Test
    void datatypeMismatchForDate() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("dueDate", "not-iso"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(DUE_DATE_IRI);
    }

    @Test
    void datatypeMismatchForDateTimeWithoutOffset() {
        AttributeDefinition writable =
            attribute(NS + "occurredAt", Cardinality.atMost(1), Datatype.DATE_TIME, false);
        TypeDefinition type =
            new TypeDefinition(
                NS + "T",
                "T",
                List.of(),
                List.of(writable),
                DisplayHints.none(),
                false,
                Optional.empty());

        CoercionResult result =
            ValueCoercer.coerce(type, Map.of("occurredAt", "2026-09-16T10:00:00"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(NS + "occurredAt");
    }

    // --- scalar vs list mismatches ---

    @Test
    void listForScalarPropertyIsAViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("title", List.of("a", "b")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TITLE_IRI);
    }

    @Test
    void scalarForListPropertyIsAViolation() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of("tags", "not-a-list"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TAGS_IRI);
    }

    @Test
    void nestedListProducesViolationNotAnException() {
        CoercionResult result =
            ValueCoercer.coerce(TASK, Map.of("tags", List.of(List.of("a", "b"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations().get(0).path()).isEqualTo(TAGS_IRI);
    }

    // --- null handling and boundary of StructuralValidator's job ---

    @Test
    void nullRawValueMeansAbsentEvenForRequiredProperty() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("title", null);

        CoercionResult result = ValueCoercer.coerce(TASK, raw);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).isEmpty();
    }

    @Test
    void emptyRawPropertiesSucceedsWithEmptyProperties() {
        CoercionResult result = ValueCoercer.coerce(TASK, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.properties()).isEmpty();
    }

    // --- short name vs IRI ---

    @Test
    void shortNameAndFullIriBothResolveToTheSamePropertyRefKeyedByFullIri() {
        CoercionResult byShortName = ValueCoercer.coerce(TASK, Map.of("title", "a"));
        CoercionResult byIri = ValueCoercer.coerce(TASK, Map.of(TITLE_IRI, "a"));

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

        CoercionResult result = ValueCoercer.coerce(TASK, raw);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.violations()).hasSize(3);
    }

    // --- null arguments ---

    @Test
    void coerceRejectsNullType() {
        assertThatNullPointerException().isThrownBy(() -> ValueCoercer.coerce(null, Map.of()));
    }

    @Test
    void coerceRejectsNullRawProperties() {
        assertThatNullPointerException().isThrownBy(() -> ValueCoercer.coerce(TASK, null));
    }
}
