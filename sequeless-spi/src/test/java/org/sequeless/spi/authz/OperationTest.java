package org.sequeless.spi.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationTest {

    @Test
    void valuesAreExactlyTheSevenOperationsInDeclaredOrder() {
        assertThat(Operation.values())
            .as("declaration order guards ordinal stability for anything that ends up comparing ordinals")
            .containsExactly(
                Operation.BROWSE,
                Operation.READ,
                Operation.EDIT,
                Operation.ADD,
                Operation.DELETE,
                Operation.TRANSITION,
                Operation.ADMIN);
    }
}
