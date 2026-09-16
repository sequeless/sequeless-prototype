package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class CardinalityTest {

    @Test
    void optionalIsMinZeroMaxUnbounded() {
        Cardinality cardinality = Cardinality.optional();

        assertThat(cardinality.min()).isZero();
        assertThat(cardinality.max()).isEmpty();
    }

    @Test
    void requiredIsMinOneMaxUnbounded() {
        Cardinality cardinality = Cardinality.required();

        assertThat(cardinality.min()).isOne();
        assertThat(cardinality.max()).isEmpty();
    }

    @Test
    void exactlyPinsMinAndMaxToTheSameValue() {
        Cardinality cardinality = Cardinality.exactly(3);

        assertThat(cardinality.min()).isEqualTo(3);
        assertThat(cardinality.max()).hasValue(3);
    }

    @Test
    void atMostIsMinZero() {
        Cardinality cardinality = Cardinality.atMost(2);

        assertThat(cardinality.min()).isZero();
        assertThat(cardinality.max()).hasValue(2);
    }

    @Test
    void rangeSetsMinAndMaxIndependently() {
        Cardinality cardinality = Cardinality.range(1, 5);

        assertThat(cardinality.min()).isOne();
        assertThat(cardinality.max()).hasValue(5);
    }

    @Test
    void rejectsNegativeMin() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Cardinality(-1, OptionalInt.empty()));
    }

    @Test
    void rejectsMaxLessThanMin() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Cardinality(2, OptionalInt.of(1)));
    }

    @Test
    void rejectsNullMaxWrapper() {
        assertThatNullPointerException().isThrownBy(() -> new Cardinality(0, null));
    }

    @Test
    void acceptsMaxEqualToMin() {
        Cardinality cardinality = new Cardinality(2, OptionalInt.of(2));

        assertThat(cardinality.max()).hasValue(2);
    }
}
