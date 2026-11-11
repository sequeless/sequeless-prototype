package org.sequeless.core.derivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.Cardinality;
import org.sequeless.spi.meta.schema.Datatype;
import org.sequeless.spi.meta.schema.DisplayHints;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.RelationshipDefinition;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

class FilterNodeToCriterionTest {

    private static final String ONT = "https://example.org/ont";
    private static final String TASK = ONT + "#Task";
    private static final String BUG = ONT + "#Bug";
    private static final String PERSON = ONT + "#Person";
    private static final String GHOST = ONT + "#Ghost";
    private static final String NAME = ONT + "#name";
    private static final String ESTIMATE = ONT + "#estimate";
    private static final String COUNT = ONT + "#count";
    private static final String ACTIVE = ONT + "#active";
    private static final String DUE = ONT + "#due";
    private static final String OPENED = ONT + "#opened";
    private static final String OWNER = ONT + "#owner";
    private static final String SEVERITY = ONT + "#severity";
    private static final String SCORE = ONT + "#score";
    private static final String MISSING = ONT + "#missing";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Schema SCHEMA = schema();

    @Test
    void flattensAndIntoOneCriterionPerFieldFilterInTreeOrder() {
        FilterNode node = new AndFilter(List.of(
                field(NAME, "is", TextNode.valueOf("a")),
                new AndFilter(List.of(
                        field(COUNT, "is greater than", LongNode.valueOf(2)), field(ACTIVE, "is", BooleanNode.TRUE)))));

        assertThat(FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                .containsExactly(
                        new Criterion(NAME, Operator.EQ, Optional.of(Value.text("a"))),
                        new Criterion(COUNT, Operator.GT, Optional.of(Value.integer(2))),
                        new Criterion(ACTIVE, Operator.EQ, Optional.of(Value.bool(true))));
        assertThat(FilterNodeToCriterion.toCriteria(new AndFilter(List.of()), SCHEMA, TASK))
                .isEmpty();
    }

    @Test
    void rejectsOrFilterNamingTheNodeType() {
        FilterNode node = new OrFilter(List.of(field(NAME, "is", TextNode.valueOf("a"))));
        FilterNode nestedInAnd = new AndFilter(List.of(node));

        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrFilter");
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(nestedInAnd, SCHEMA, TASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrFilter");
    }

    @Test
    void rejectsAnyFilterNamingTheNodeType() {
        FilterNode node = new AnyFilter("is", TextNode.valueOf("a"));

        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AnyFilter");
    }

    @Test
    void rejectsUnmappedFilterOpsNamingTheOp() {
        for (String op : List.of("is like", "is not like", "exists", "does not exist", "bogus")) {
            FilterNode node = field(NAME, op, TextNode.valueOf("a"));

            assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                    .as(op)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'" + op + "'");
        }
    }

    @Test
    void mapsIsAndIsNotWithNullValueToIsNullAndNotNull() {
        FilterNode node = new AndFilter(List.of(
                field(NAME, "is", NullNode.getInstance()),
                field(NAME, "is not", NullNode.getInstance()),
                field(ESTIMATE, "is", null),
                field(ESTIMATE, "is not", null)));

        assertThat(FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                .containsExactly(
                        new Criterion(NAME, Operator.IS_NULL, Optional.empty()),
                        new Criterion(NAME, Operator.NOT_NULL, Optional.empty()),
                        new Criterion(ESTIMATE, Operator.IS_NULL, Optional.empty()),
                        new Criterion(ESTIMATE, Operator.NOT_NULL, Optional.empty()));
    }

    @Test
    void mapsValueOperatorNamesToTheirOperators() {
        Map<String, Operator> numeric = Map.of(
                "is", Operator.EQ,
                "is not", Operator.NE,
                "is less than", Operator.LT,
                "is less than or equal to", Operator.LTE,
                "is greater than", Operator.GT,
                "is greater than or equal to", Operator.GTE);
        numeric.forEach((op, expected) -> assertThat(
                        single(field(COUNT, op, LongNode.valueOf(1))).operator())
                .as(op)
                .isEqualTo(expected));
        Map.of("contains", Operator.CONTAINS, "starts with", Operator.STARTS_WITH)
                .forEach((op, expected) -> assertThat(
                                single(field(NAME, op, TextNode.valueOf("x"))).operator())
                        .as(op)
                        .isEqualTo(expected));
    }

    @Test
    void convertsValuesByPropertyDatatype() {
        assertThat(valueOf(NAME, TextNode.valueOf("x"))).isEqualTo(Value.text("x"));
        assertThat(valueOf(COUNT, LongNode.valueOf(7))).isEqualTo(Value.integer(7));
        assertThat(valueOf(ESTIMATE, DecimalNode.valueOf(new BigDecimal("1.50"))))
                .isEqualTo(Value.decimal(new BigDecimal("1.50")));
        assertThat(valueOf(ACTIVE, BooleanNode.TRUE)).isEqualTo(Value.bool(true));
        assertThat(valueOf(DUE, TextNode.valueOf("2026-01-02"))).isEqualTo(Value.date(LocalDate.parse("2026-01-02")));
        assertThat(valueOf(OPENED, TextNode.valueOf("2026-01-02T03:04:05Z")))
                .isEqualTo(Value.dateTime(Instant.parse("2026-01-02T03:04:05Z")));
    }

    @Test
    void rejectsValuesThatDoNotFitTheirDatatype() {
        Map<FilterNode, String> malformed = Map.of(
                field(COUNT, "is", TextNode.valueOf("7")), COUNT,
                field(COUNT, "is", JSON.arrayNode().add(1L)), COUNT,
                field(NAME, "is", BooleanNode.TRUE), NAME,
                field(DUE, "is", TextNode.valueOf("2026-13-01")), DUE,
                field(OPENED, "is", TextNode.valueOf("not a time")), OPENED,
                field(COUNT, "is greater than", NullNode.getInstance()), COUNT,
                field(COUNT, "is greater than", JSON.arrayNode().add(1L)), COUNT);

        malformed.forEach(
                (node, property) -> assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK))
                        .as(property)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(property));
    }

    @Test
    void findsPropertyDeclaredOnlyOnSubtypeOfSourceType() {
        assertThat(single(field(SEVERITY, "is", TextNode.valueOf("high"))))
                .isEqualTo(new Criterion(SEVERITY, Operator.EQ, Optional.of(Value.text("high"))));
    }

    @Test
    void rejectsUndeclaredPropertyUnknownSourceTypeAndConflictingDeclarations() {
        assertThatThrownBy(() -> single(field(MISSING, "is", TextNode.valueOf("x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MISSING);
        assertThatThrownBy(
                        () -> FilterNodeToCriterion.toCriteria(field(NAME, "is", TextNode.valueOf("x")), SCHEMA, GHOST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NAME)
                .hasMessageContaining(GHOST);
        assertThatThrownBy(() -> single(field(SCORE, "is", LongNode.valueOf(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different value kinds");
    }

    @Test
    void mapsIsInArrayToListValueOfElementValues() {
        assertThat(single(field(COUNT, "is in", JSON.arrayNode().add(1L).add(2L))))
                .isEqualTo(new Criterion(
                        COUNT, Operator.IN, Optional.of(Value.list(List.of(Value.integer(1), Value.integer(2))))));
        assertThat(single(field(COUNT, "is in", JSON.arrayNode())))
                .isEqualTo(new Criterion(COUNT, Operator.IN, Optional.of(Value.list(List.of()))));
    }

    @Test
    void rejectsIsInWithScalarOrNullElement() {
        FilterNode scalar = field(COUNT, "is in", LongNode.valueOf(1));
        FilterNode nullElement = field(COUNT, "is in", JSON.arrayNode().add(1L).addNull());

        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(scalar, SCHEMA, TASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'is in'");
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(nullElement, SCHEMA, TASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(COUNT);
    }

    @Test
    void mapsRelationshipPropertyToReferenceValue() {
        ObjectId target = ObjectId.random();

        assertThat(valueOf(OWNER, TextNode.valueOf(target.value().toString()))).isEqualTo(Value.ref(target));
        assertThatThrownBy(() -> valueOf(OWNER, TextNode.valueOf("not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OWNER);
    }

    @Test
    void rejectsNullArguments() {
        FilterNode node = field(NAME, "is", TextNode.valueOf("x"));

        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(null, SCHEMA, TASK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, null, TASK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static FieldFilter field(String path, String op, JsonNode value) {
        return new FieldFilter(path, op, value);
    }

    private static Criterion single(FilterNode node) {
        return FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK).get(0);
    }

    private static Value valueOf(String property, JsonNode node) {
        return single(field(property, "is", node)).value().orElseThrow();
    }

    private static Schema schema() {
        TypeDefinition task = new TypeDefinition(
                TASK,
                "Task",
                List.of(),
                List.of(
                        attribute(NAME, Datatype.STRING),
                        attribute(ESTIMATE, Datatype.DECIMAL),
                        attribute(COUNT, Datatype.INTEGER),
                        attribute(ACTIVE, Datatype.BOOLEAN),
                        attribute(DUE, Datatype.DATE),
                        attribute(OPENED, Datatype.DATE_TIME),
                        attribute(SCORE, Datatype.INTEGER),
                        new RelationshipDefinition(
                                OWNER,
                                "owner",
                                Cardinality.optional(),
                                false,
                                false,
                                false,
                                false,
                                false,
                                DisplayHints.none(),
                                PERSON,
                                Optional.empty(),
                                false)),
                DisplayHints.none(),
                false);
        TypeDefinition bug = new TypeDefinition(
                BUG,
                "Bug",
                List.of(TASK),
                List.of(attribute(SEVERITY, Datatype.STRING), attribute(SCORE, Datatype.DECIMAL)),
                DisplayHints.none(),
                false);
        TypeDefinition person = new TypeDefinition(
                PERSON, "Person", List.of(), List.of(attribute(NAME, Datatype.STRING)), DisplayHints.none(), false);
        return new Schema(new OntologyVersion(1), ONT, Optional.empty(), Map.of(), List.of(task, bug, person));
    }

    private static AttributeDefinition attribute(String iri, Datatype datatype) {
        return new AttributeDefinition(
                iri,
                iri.substring(iri.indexOf('#') + 1),
                Cardinality.optional(),
                false,
                false,
                false,
                false,
                false,
                DisplayHints.none(),
                datatype);
    }
}
