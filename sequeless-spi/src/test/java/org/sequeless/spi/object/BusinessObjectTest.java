package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;

class BusinessObjectTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");
    private static final Audit AUDIT = new Audit(NOW, "alice", NOW, "alice");
    private static final ObjectId ID = ObjectId.random();
    private static final TypeRef TYPE = new TypeRef("https://example.org/ns#Task");
    private static final PropertyRef TITLE = new PropertyRef("https://example.org/ns#title");

    private static BusinessObject sample(Map<PropertyRef, Value> properties) {
        return new BusinessObject(ID, TYPE, TenantId.DEFAULT, 1, Optional.empty(), properties, AUDIT, false);
    }

    @Test
    void carriesAllFields() {
        BusinessObject object = sample(Map.of(TITLE, Value.text("Write plan")));

        assertThat(object.id()).isEqualTo(ID);
        assertThat(object.type()).isEqualTo(TYPE);
        assertThat(object.tenant()).isEqualTo(TenantId.DEFAULT);
        assertThat(object.version()).isEqualTo(1);
        assertThat(object.state()).isEmpty();
        assertThat(object.properties()).containsEntry(TITLE, Value.text("Write plan"));
        assertThat(object.audit()).isEqualTo(AUDIT);
        assertThat(object.deleted()).isFalse();
    }

    @Test
    void propertiesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerMap() {
        Map<PropertyRef, Value> callerProperties = new HashMap<>();
        callerProperties.put(TITLE, Value.text("Write plan"));

        BusinessObject object = sample(callerProperties);
        callerProperties.put(TITLE, Value.text("Changed"));

        assertThat(object.properties()).containsEntry(TITLE, Value.text("Write plan"));
        assertThatThrownBy(() -> object.properties().put(TITLE, Value.text("x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsVersionLessThanOne() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, TenantId.DEFAULT, 0, Optional.empty(), Map.of(), AUDIT, false));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, TenantId.DEFAULT, -1, Optional.empty(), Map.of(), AUDIT, false));
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(null, TYPE, TenantId.DEFAULT, 1, Optional.empty(), Map.of(), AUDIT, false));
    }

    @Test
    void rejectsNullType() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(ID, null, TenantId.DEFAULT, 1, Optional.empty(), Map.of(), AUDIT, false));
    }

    @Test
    void rejectsNullTenant() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, null, 1, Optional.empty(), Map.of(), AUDIT, false));
    }

    @Test
    void rejectsNullStateWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, TenantId.DEFAULT, 1, null, Map.of(), AUDIT, false));
    }

    @Test
    void rejectsNullProperties() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, TenantId.DEFAULT, 1, Optional.empty(), null, AUDIT, false));
    }

    @Test
    void rejectsNullAudit() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BusinessObject(ID, TYPE, TenantId.DEFAULT, 1, Optional.empty(), Map.of(), null, false));
    }
}
