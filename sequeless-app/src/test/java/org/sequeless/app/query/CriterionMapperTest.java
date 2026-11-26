package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;
import static org.sequeless.app.query.QueryFixtures.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

class CriterionMapperTest {

    @ParameterizedTest
    @CsvSource({
        "EQ, is",
        "NE, is not",
        "GT, is greater than",
        "GTE, is greater than or equal to",
        "LT, is less than",
        "LTE, is less than or equal to",
        "CONTAINS, contains",
        "STARTS_WITH, starts with",
        "IN, is in"
    })
    void mapsEachValueOperatorToItsReleasedName(Operator operator, String released) {
        Value value = operator == Operator.IN ? Value.list(List.of(Value.text("a"))) : Value.text("a");

        FieldFilter filter = CriterionMapper.toFieldFilter(criterion(operator, value));

        assertThat(filter.op()).isEqualTo(released);
    }

    @Test
    void theFieldFilterNamesTheCriterionPropertyIri() {
        FieldFilter filter = CriterionMapper.toFieldFilter(criterion(Operator.EQ, Value.text("x")));

        assertThat(filter.path()).isEqualTo(TITLE_IRI);
        assertThat(filter.value().asText()).isEqualTo("x");
    }

    @Test
    void isNullIsUnmappedAndNamesTheOperator() {
        assertThatThrownBy(() -> CriterionMapper.toFieldFilter(criterion(Operator.IS_NULL, null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("IS_NULL");
    }

    @Test
    void notNullIsUnmappedAndNamesTheOperator() {
        assertThatThrownBy(() -> CriterionMapper.toFieldFilter(criterion(Operator.NOT_NULL, null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("NOT_NULL");
    }

    @Test
    void anUnmappedOperatorInsideAnAndIsRejectedWhole() {
        List<Criterion> criteria = List.of(criterion(Operator.EQ, Value.text("x")), criterion(Operator.IS_NULL, null));

        assertThatThrownBy(() -> CriterionMapper.toFilter(criteria))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("IS_NULL");
    }

    @Test
    void aValueOperatorWithoutAValueIsRejected() {
        assertThatThrownBy(() -> CriterionMapper.toFieldFilter(criterion(Operator.GT, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a value");
    }

    @Test
    void noCriteriaIsNoFilter() {
        assertThat(CriterionMapper.toFilter(List.of())).isEmpty();
    }

    @Test
    void oneCriterionIsItsOwnFilter() {
        Criterion only = criterion(Operator.EQ, Value.text("x"));

        assertThat(CriterionMapper.toFilter(List.of(only))).contains(CriterionMapper.toFieldFilter(only));
    }

    @Test
    void severalCriteriaAreAnAndFilterInOrder() {
        Criterion first = criterion(Operator.EQ, Value.text("x"));
        Criterion second = criterion(Operator.GT, Value.integer(3));

        assertThat(CriterionMapper.toFilter(List.of(first, second)))
                .hasValueSatisfying(node -> assertThat(node)
                        .isEqualTo(new AndFilter(
                                List.of(CriterionMapper.toFieldFilter(first), CriterionMapper.toFieldFilter(second)))));
    }

    @Test
    void textIsAJsonString() {
        assertThat(CriterionMapper.toJson(Value.text("hello"))).isEqualTo(JsonNodeFactory.instance.textNode("hello"));
    }

    @Test
    void integerIsAJsonNumber() {
        JsonNode node = CriterionMapper.toJson(Value.integer(42));

        assertThat(node.isIntegralNumber()).isTrue();
        assertThat(node.asLong()).isEqualTo(42L);
    }

    @Test
    void decimalIsAJsonNumber() {
        JsonNode node = CriterionMapper.toJson(Value.decimal(new BigDecimal("1.50")));

        assertThat(node.isNumber()).isTrue();
        assertThat(node.decimalValue()).isEqualByComparingTo("1.50");
    }

    @Test
    void booleanIsAJsonBoolean() {
        assertThat(CriterionMapper.toJson(Value.bool(true)).booleanValue()).isTrue();
    }

    @Test
    void dateIsIsoText() {
        assertThat(CriterionMapper.toJson(Value.date(LocalDate.of(2026, 11, 26)))
                        .textValue())
                .isEqualTo("2026-11-26");
    }

    @Test
    void dateTimeIsIsoTextWithOffset() {
        assertThat(CriterionMapper.toJson(Value.dateTime(Instant.parse("2026-11-26T10:15:30Z")))
                        .textValue())
                .isEqualTo("2026-11-26T10:15:30Z");
    }

    @Test
    void referenceIsItsUuidText() {
        UUID id = UUID.fromString("5b6f0c2e-7d1a-4c4e-9a55-0f7a2e1d9c10");

        assertThat(CriterionMapper.toJson(Value.ref(new ObjectId(id))).textValue())
                .isEqualTo(id.toString());
    }

    @Test
    void listIsAnArrayOfItsElementsInOrder() {
        JsonNode node = CriterionMapper.toJson(Value.list(List.of(Value.text("a"), Value.integer(2))));

        ArrayNode expected = JsonNodeFactory.instance.arrayNode();
        expected.add("a");
        expected.add(2L);
        assertThat(node).isEqualTo(expected);
    }

    @Test
    void anInListCriterionCarriesTheArray() {
        Criterion in = criterion(Operator.IN, Value.list(List.of(Value.text("a"), Value.text("b"))));

        assertThat(CriterionMapper.toFieldFilter(in).value().isArray()).isTrue();
        assertThat(CriterionMapper.toFieldFilter(in).value().size()).isEqualTo(2);
    }
}
