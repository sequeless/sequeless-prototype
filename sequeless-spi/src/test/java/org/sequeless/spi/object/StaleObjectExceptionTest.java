package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class StaleObjectExceptionTest {

    @Test
    void exposesObjectIdAndExpectedVersion() {
        ObjectId id = ObjectId.random();

        StaleObjectException exception = new StaleObjectException(id, 5);

        assertThat(exception.objectId()).isEqualTo(id);
        assertThat(exception.expectedVersion()).isEqualTo(5);
        assertThat(exception.getMessage()).contains(id.toString()).contains("5");
    }

    @Test
    void rejectsNullObjectId() {
        assertThatNullPointerException().isThrownBy(() -> new StaleObjectException(null, 5));
    }
}
