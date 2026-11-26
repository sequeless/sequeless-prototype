package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/** Pins the raw form each kernel {@link Value} subtype takes on the Command side's write path. */
class ValueWireTest {

    @Test
    void mapsEachScalarToTheRawFormValueCoercerAccepts() {
        ObjectId target = new ObjectId(UUID.fromString("00000000-0000-0000-0000-000000000042"));

        assertThat(ValueWire.toRaw(Value.text("hello"))).isEqualTo("hello");
        assertThat(ValueWire.toRaw(Value.integer(7))).isEqualTo(7L);
        assertThat(ValueWire.toRaw(Value.decimal(new BigDecimal("1.50")))).isEqualTo(new BigDecimal("1.50"));
        assertThat(ValueWire.toRaw(Value.bool(true))).isEqualTo(true);
        assertThat(ValueWire.toRaw(Value.dateTime(Instant.parse("2026-01-02T03:04:05Z"))))
                .isEqualTo("2026-01-02T03:04:05Z");
        assertThat(ValueWire.toRaw(Value.date(LocalDate.of(2026, 1, 2)))).isEqualTo("2026-01-02");
        assertThat(ValueWire.toRaw(Value.ref(target))).isEqualTo("00000000-0000-0000-0000-000000000042");
    }

    @Test
    void mapsAListElementwise() {
        Value list = new ListValue(List.of(Value.text("a"), Value.integer(1)));

        assertThat(ValueWire.toRaw(list)).isEqualTo(List.of("a", 1L));
    }

    @Test
    void keepsEveryKeyOfAPropertyMap() {
        Map<String, Object> raw = ValueWire.toRawProperties(Map.of(
                "https://sequeless.dev/ns/test#name", Value.text("n"),
                "https://sequeless.dev/ns/test#count", Value.integer(2)));

        assertThat(raw)
                .containsEntry("https://sequeless.dev/ns/test#name", "n")
                .containsEntry("https://sequeless.dev/ns/test#count", 2L)
                .hasSize(2);
    }
}
