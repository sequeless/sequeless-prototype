package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;

class CreateTest {

    private static BusinessObject sampleObject() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        Audit audit = new Audit(now, "alice", now, "alice");
        return new BusinessObject(
            ObjectId.random(), new TypeRef("https://example.org/ns#Task"), TenantId.DEFAULT, 1,
            Optional.empty(), Map.of(), audit, false);
    }

    @Test
    void carriesObject() {
        BusinessObject object = sampleObject();

        assertThat(new Create(object).object()).isEqualTo(object);
    }

    @Test
    void rejectsNullObject() {
        assertThatNullPointerException().isThrownBy(() -> new Create(null));
    }

    @Test
    void isUsableAsAMutation() {
        Mutation mutation = new Create(sampleObject());

        assertThat(mutation).isInstanceOf(Create.class);
    }
}
