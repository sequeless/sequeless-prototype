package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Value;

/**
 * Round-trips every {@link Value} variant through {@link ValueJsonCodec}, including the
 * scale-preservation guarantee {@link DecimalValue}'s javadoc documents.
 */
class ValueJsonCodecTest {

    private static final String NS = "https://example.org/codec#";

    @Test
    void roundTripsEveryScalarVariant() {
        ObjectId referenced = ObjectId.random();
        Map<PropertyRef, Value> properties =
            Map.of(
                new PropertyRef(NS + "text"), Value.text("hello"),
                new PropertyRef(NS + "integer"), Value.integer(42L),
                new PropertyRef(NS + "decimal"), Value.decimal(new BigDecimal("12.50")),
                new PropertyRef(NS + "bool"), Value.bool(true),
                new PropertyRef(NS + "dateTime"), Value.dateTime(Instant.parse("2026-01-01T00:00:00Z")),
                new PropertyRef(NS + "date"), Value.date(LocalDate.of(2026, 1, 1)),
                new PropertyRef(NS + "ref"), Value.ref(referenced));

        String json = ValueJsonCodec.toJson(properties);
        Map<PropertyRef, Value> decoded = ValueJsonCodec.fromJson(json);

        assertThat(decoded).isEqualTo(properties);
    }

    @Test
    void roundTripsListOfMixedScalars() {
        ObjectId referenced = ObjectId.random();
        PropertyRef listProp = new PropertyRef(NS + "list");
        Value list = Value.list(List.of(Value.text("a"), Value.integer(1), Value.ref(referenced)));
        Map<PropertyRef, Value> properties = Map.of(listProp, list);

        Map<PropertyRef, Value> decoded = ValueJsonCodec.fromJson(ValueJsonCodec.toJson(properties));

        assertThat(decoded).isEqualTo(properties);
    }

    @Test
    void decimalRoundTripPreservesScale() {
        PropertyRef decimalProp = new PropertyRef(NS + "decimal");
        Map<PropertyRef, Value> properties = Map.of(decimalProp, Value.decimal(new BigDecimal("12.50")));

        Map<PropertyRef, Value> decoded = ValueJsonCodec.fromJson(ValueJsonCodec.toJson(properties));

        DecimalValue decoded1 = (DecimalValue) decoded.get(decimalProp);
        assertThat(decoded1.value().scale()).isEqualTo(2);
        assertThat(decoded1.value()).isEqualTo(new BigDecimal("12.50"));
    }

    @Test
    void emptyPropertiesRoundTrip() {
        assertThat(ValueJsonCodec.fromJson(ValueJsonCodec.toJson(Map.of()))).isEmpty();
    }
}
