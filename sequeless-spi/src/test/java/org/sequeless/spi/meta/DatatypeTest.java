package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DatatypeTest {

    @ParameterizedTest
    @EnumSource(Datatype.class)
    void xsdIriRoundTripsThroughFromXsd(Datatype datatype) {
        assertThat(Datatype.fromXsd(datatype.xsdIri())).contains(datatype);
    }

    @Test
    void fromXsdReturnsEmptyForAnUnrecognisedIri() {
        assertThat(Datatype.fromXsd("http://example.org/not-xsd")).isEmpty();
    }

    @Test
    void fromXsdRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Datatype.fromXsd(null));
    }

    @Test
    void stringXsdIriIsTheStandardOne() {
        assertThat(Datatype.STRING.xsdIri()).isEqualTo("http://www.w3.org/2001/XMLSchema#string");
    }
}
