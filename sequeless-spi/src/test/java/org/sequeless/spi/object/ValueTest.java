package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueTest {

    @Test
    void textFactoryProducesTextValue() {
        assertThat(Value.text("hello")).isEqualTo(new TextValue("hello"));
    }

    @Test
    void integerFactoryProducesIntegerValue() {
        assertThat(Value.integer(42L)).isEqualTo(new IntegerValue(42L));
    }

    @Test
    void decimalFactoryProducesDecimalValue() {
        assertThat(Value.decimal(new BigDecimal("12.50"))).isEqualTo(new DecimalValue(new BigDecimal("12.50")));
    }

    @Test
    void boolFactoryProducesBoolValue() {
        assertThat(Value.bool(true)).isEqualTo(new BoolValue(true));
    }

    @Test
    void dateTimeFactoryProducesDateTimeValue() {
        Instant instant = Instant.parse("2026-09-16T10:00:00Z");

        assertThat(Value.dateTime(instant)).isEqualTo(new DateTimeValue(instant));
    }

    @Test
    void dateFactoryProducesDateValue() {
        LocalDate date = LocalDate.of(2026, 10, 1);

        assertThat(Value.date(date)).isEqualTo(new DateValue(date));
    }

    @Test
    void refFactoryProducesReferenceValue() {
        ObjectId target = ObjectId.random();

        assertThat(Value.ref(target)).isEqualTo(new ReferenceValue(target));
    }

    @Test
    void listFactoryProducesListValue() {
        List<Value> values = List.of(Value.text("a"), Value.text("b"));

        assertThat(Value.list(values)).isEqualTo(new ListValue(values));
    }

    @Test
    void listValueRejectsNestedList() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Value.list(List.of(Value.list(List.of()))))
            .withMessageContaining("nested");
    }

    @Test
    void listValueAcceptsAFlatMixedList() {
        List<Value> values = List.of(Value.text("a"), Value.integer(1), Value.bool(true));

        assertThat(new ListValue(values).values()).containsExactlyElementsOf(values);
    }

    @Test
    void decimalValueEqualityIsScaleSensitive() {
        assertThat(new DecimalValue(new BigDecimal("12.50")))
            .isNotEqualTo(new DecimalValue(new BigDecimal("12.5")));
        assertThat(new DecimalValue(new BigDecimal("12.50")))
            .isNotEqualTo(new DecimalValue(BigDecimal.valueOf(12.5)));
    }

    @Test
    void textValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new TextValue(null));
    }

    @Test
    void decimalValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new DecimalValue(null));
    }

    @Test
    void dateTimeValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new DateTimeValue(null));
    }

    @Test
    void dateValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new DateValue(null));
    }

    @Test
    void referenceValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new ReferenceValue(null));
    }

    @Test
    void listValueRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new ListValue(null));
    }
}
