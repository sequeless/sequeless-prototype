package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ObjectNotFoundExceptionTest {

    @Test
    void exposesObjectId() {
        ObjectId id = ObjectId.random();

        ObjectNotFoundException exception = new ObjectNotFoundException(id);

        assertThat(exception.objectId()).isEqualTo(id);
        assertThat(exception.getMessage()).contains(id.toString());
    }

    @Test
    void rejectsNullObjectId() {
        assertThatNullPointerException().isThrownBy(() -> new ObjectNotFoundException(null));
    }
}
