package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeverityTest {

    @Test
    void valuesAreExactlyTheTwoSeveritiesInDeclaredOrder() {
        assertThat(Severity.values())
            .as("declaration order guards ordinal stability for anything that ends up comparing ordinals")
            .containsExactly(Severity.ERROR, Severity.WARNING);
    }
}
