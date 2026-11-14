package org.sequeless.core.derivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.math.BigInteger;
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
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
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
    private static final String BIG = ONT + "#big";
    private static final String RATIO = ONT + "#ratio";
    private static final String ACTIVE = ONT + "#active";
    private static final String DUE = ONT + "#due";
    private static final String OPENED = ONT + "#opened";
    private static final String AT = ONT + "#at";
    private static final String SPAN = ONT + "#span";
    private static final String SITE = ONT + "#site";
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

        assertThat(criteria(node))
                .containsExactly(
                        new Criterion(NAME, Operator.EQ, Optional.of(Value.text("a"))),
                        new Criterion(COUNT, Operator.GT, Optional.of(Value.integer(2))),
                        new Criterion(ACTIVE, Operator.EQ, Optional.of(Value.bool(true))));
        assertThat(criteria(new AndFilter(List.of()))).isEmpty();
    }

    @Test
    void rejectsOrFilterNamingTheNodeType() {
        FilterNode node = new OrFilter(List.of(field(NAME, "is", TextNode.valueOf("a"))));
        FilterNode nestedInAnd = new AndFilter(List.of(node));

        assertThatThrownBy(() -> criteria(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrFilter is not supported");
        assertThatThrownBy(() -> criteria(nestedInAnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrFilter is not supported");
    }

    @Test
    void rejectsAnyFilterNamingTheNodeTypeAlsoWhenNestedInAnd() {
        FilterNode node = new AnyFilter("is", TextNode.valueOf("a"));
        FilterNode nestedInAnd = new AndFilter(List.of(field(NAME, "is", TextNode.valueOf("a")), node));

        assertThatThrownBy(() -> criteria(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AnyFilter is not supported");
        assertThatThrownBy(() -> criteria(nestedInAnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AnyFilter is not supported");
    }

    @Test
    void rejectsUnmappedFilterOpsNamingTheOp() {
        for (String op : List.of("is like", "is not like", "exists", "does not exist", "bogus")) {
            FilterNode node = field(NAME, op, TextNode.valueOf("a"));

            assertThatThrownBy(() -> criteria(node))
                    .as(op)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported filter op '" + op + "' on " + NAME);
        }
    }

    @Test
    void mapsIsAndIsNotWithNullValueToIsNullAndNotNull() {
        FilterNode node = new AndFilter(List.of(
                field(NAME, "is", NullNode.getInstance()),
                field(NAME, "is not", NullNode.getInstance()),
                field(ESTIMATE, "is", null),
                field(ESTIMATE, "is not", null)));

        assertThat(criteria(node))
                .containsExactly(
                        new Criterion(NAME, Operator.IS_NULL, Optional.empty()),
                        new Criterion(NAME, Operator.NOT_NULL, Optional.empty()),
                        new Criterion(ESTIMATE, Operator.IS_NULL, Optional.empty()),
                        new Criterion(ESTIMATE, Operator.NOT_NULL, Optional.empty()));
    }

    @Test
    void acceptsNoValueOpOnUndeclaredPropertyWithoutLookup() {
        assertThat(criteria(field(MISSING, "is", NullNode.getInstance())))
                .containsExactly(new Criterion(MISSING, Operator.IS_NULL, Optional.empty()));
        assertThat(criteria(field(MISSING, "is not", null)))
                .containsExactly(new Criterion(MISSING, Operator.NOT_NULL, Optional.empty()));
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
    void convertsValuesByPropertyDatatypeForEveryDatatype() {
        assertThat(valueOf(NAME, TextNode.valueOf("x"))).isEqualTo(Value.text("x"));
        assertThat(valueOf(COUNT, LongNode.valueOf(7))).isEqualTo(Value.integer(7));
        assertThat(valueOf(BIG, LongNode.valueOf(7))).isEqualTo(Value.integer(7));
        assertThat(valueOf(ESTIMATE, DecimalNode.valueOf(new BigDecimal("1.50"))))
                .isEqualTo(Value.decimal(new BigDecimal("1.50")));
        assertThat(valueOf(RATIO, DecimalNode.valueOf(new BigDecimal("2.5"))))
                .isEqualTo(Value.decimal(new BigDecimal("2.5")));
        assertThat(valueOf(ACTIVE, BooleanNode.TRUE)).isEqualTo(Value.bool(true));
        assertThat(valueOf(DUE, TextNode.valueOf("2026-01-02"))).isEqualTo(Value.date(LocalDate.parse("2026-01-02")));
        assertThat(valueOf(OPENED, TextNode.valueOf("2026-01-02T03:04:05Z")))
                .isEqualTo(Value.dateTime(Instant.parse("2026-01-02T03:04:05Z")));
        assertThat(valueOf(AT, TextNode.valueOf("10:15:30"))).isEqualTo(Value.text("10:15:30"));
        assertThat(valueOf(SPAN, TextNode.valueOf("P1D"))).isEqualTo(Value.text("P1D"));
        assertThat(valueOf(SITE, TextNode.valueOf("https://example.org/x")))
                .isEqualTo(Value.text("https://example.org/x"));
    }

    @Test
    void convertsIntegralNodeOnIntegerKindAsInteger() {
        assertThat(valueOf(COUNT, IntNode.valueOf(3))).isEqualTo(Value.integer(3));
    }

    @Test
    void convertsDecimalNodeOnIntegerKindAsDecimal() {
        // The bridge keeps a DecimalNode on an integer kind as a decimal.
        assertThat(valueOf(COUNT, DecimalNode.valueOf(new BigDecimal("3.0"))))
                .isEqualTo(Value.decimal(new BigDecimal("3.0")));
        assertThat(valueOf(BIG, DecimalNode.valueOf(new BigDecimal("3.5"))))
                .isEqualTo(Value.decimal(new BigDecimal("3.5")));
    }

    @Test
    void decimalNodeOnIntegerKindConvertsToDecimal() {
        // This test builds its own DecimalNode, so it covers only the bridge's conversion; the
        // released loader now coerces an integer property to an integer (sequeless-ontology 10f5a57).
        assertThat(valueOf(COUNT, DecimalNode.valueOf(new BigDecimal("3"))))
                .isEqualTo(Value.decimal(new BigDecimal("3")));
    }

    @Test
    void rejectsValuesThatDoNotFitTheirDatatype() {
        Map<FilterNode, String> malformed = Map.of(
                field(COUNT, "is", TextNode.valueOf("7")),
                "on " + COUNT + " does not fit INTEGER",
                field(COUNT, "is", JSON.arrayNode().add(1L)),
                "on " + COUNT + " does not fit INTEGER",
                field(COUNT, "is", BigIntegerNode.valueOf(BigInteger.TWO.pow(70))),
                "on " + COUNT + " does not fit INTEGER",
                field(COUNT, "is greater than", JSON.arrayNode().add(1L)),
                "on " + COUNT + " does not fit INTEGER",
                field(NAME, "is", BooleanNode.TRUE),
                "on " + NAME + " does not fit STRING",
                field(DUE, "is", TextNode.valueOf("2026-13-01")),
                "on " + DUE + " does not fit DATE",
                field(OPENED, "is", TextNode.valueOf("not a time")),
                "on " + OPENED + " does not fit DATE_TIME");

        malformed.forEach((node, fragment) -> assertThatThrownBy(() -> criteria(node))
                .as(fragment)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fragment));
    }

    @Test
    void rejectsNotANumberAndInfinityForDouble() {
        assertThatThrownBy(() -> valueOf(RATIO, DoubleNode.valueOf(Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on " + RATIO + " does not fit DOUBLE");
        assertThatThrownBy(() -> valueOf(RATIO, DoubleNode.valueOf(Double.POSITIVE_INFINITY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on " + RATIO + " does not fit DOUBLE");
    }

    @Test
    void rejectsNoValueForValueOpNamingTheOpAndProperty() {
        assertThatThrownBy(() -> criteria(field(COUNT, "is greater than", NullNode.getInstance())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter op 'is greater than' on " + COUNT + " needs a value");
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
                .hasMessageContaining("Property " + MISSING + " is not declared on " + TASK);
        assertThatThrownBy(
                        () -> FilterNodeToCriterion.toCriteria(field(NAME, "is", TextNode.valueOf("x")), SCHEMA, GHOST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property " + NAME + " is not declared on " + GHOST);
        assertThatThrownBy(() -> single(field(SCORE, "is", LongNode.valueOf(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property " + SCORE + " is declared with different value kinds on " + TASK);
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

        assertThatThrownBy(() -> criteria(scalar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter op 'is in' on " + COUNT + " needs a JSON array value");
        assertThatThrownBy(() -> criteria(nullElement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on " + COUNT + " does not fit INTEGER");
    }

    @Test
    void rejectsIsInWithNullValue() {
        assertThatThrownBy(() -> criteria(field(COUNT, "is in", NullNode.getInstance())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter op 'is in' on " + COUNT + " needs a JSON array value");
        assertThatThrownBy(() -> criteria(field(COUNT, "is in", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter op 'is in' on " + COUNT + " needs a JSON array value");
    }

    @Test
    void mapsRelationshipPropertyToReferenceValue() {
        ObjectId target = ObjectId.random();

        assertThat(valueOf(OWNER, TextNode.valueOf(target.value().toString()))).isEqualTo(Value.ref(target));
    }

    @Test
    void rejectsRelationshipValueThatIsNotUuidText() {
        assertThatThrownBy(() -> valueOf(OWNER, TextNode.valueOf("not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on " + OWNER + " does not fit a UUID text (reference)");
        assertThatThrownBy(() -> valueOf(OWNER, LongNode.valueOf(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on " + OWNER + " does not fit a UUID text (reference)");
    }

    @Test
    void rejectsNullArgumentsNamingTheArgument() {
        FilterNode node = field(NAME, "is", TextNode.valueOf("x"));

        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(null, SCHEMA, TASK))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("node must not be null");
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, null, TASK))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("schema must not be null");
        assertThatThrownBy(() -> FilterNodeToCriterion.toCriteria(node, SCHEMA, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceTypeIri must not be null");
    }

    private static FieldFilter field(String path, String op, JsonNode value) {
        return new FieldFilter(path, op, value);
    }

    private static List<Criterion> criteria(FilterNode node) {
        return FilterNodeToCriterion.toCriteria(node, SCHEMA, TASK);
    }

    private static Criterion single(FilterNode node) {
        return criteria(node).get(0);
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
                        attribute(BIG, Datatype.LONG),
                        attribute(RATIO, Datatype.DOUBLE),
                        attribute(ACTIVE, Datatype.BOOLEAN),
                        attribute(DUE, Datatype.DATE),
                        attribute(OPENED, Datatype.DATE_TIME),
                        attribute(AT, Datatype.TIME),
                        attribute(SPAN, Datatype.DURATION),
                        attribute(SITE, Datatype.ANY_URI),
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
