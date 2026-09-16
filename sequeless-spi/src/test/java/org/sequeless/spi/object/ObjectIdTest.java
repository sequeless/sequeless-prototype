package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectIdTest {

    @Test
    void randomProducesDistinctIdsAcrossCalls() {
        assertThat(ObjectId.random()).isNotEqualTo(ObjectId.random());
    }

    @Test
    void parseWrapsTheParsedUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(ObjectId.parse(uuid.toString())).isEqualTo(new ObjectId(uuid));
    }

    @Test
    void parseRejectsAnInvalidUuidString() {
        assertThatIllegalArgumentException().isThrownBy(() -> ObjectId.parse("not-a-uuid"));
    }

    @Test
    void parseRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> ObjectId.parse(null));
    }

    @Test
    void canonicalConstructorRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new ObjectId(null));
    }
}
