package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;

class UpdateTest {

    private static BusinessObject sampleObject() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        Audit audit = new Audit(now, "alice", now, "alice");
        return new BusinessObject(
            ObjectId.random(), new TypeRef("https://example.org/ns#Task"), TenantId.DEFAULT, 1,
            Optional.empty(), Map.of(), audit, false);
    }

    @Test
    void carriesObjectAndExpectedVersion() {
        BusinessObject object = sampleObject();
        Update update = new Update(object, 3);

        assertThat(update.object()).isEqualTo(object);
        assertThat(update.expectedVersion()).isEqualTo(3);
    }

    @Test
    void rejectsNullObject() {
        assertThatNullPointerException().isThrownBy(() -> new Update(null, 1));
    }

    @Test
    void rejectsExpectedVersionLessThanOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Update(sampleObject(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Update(sampleObject(), -1));
    }
}
