package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.TypeRef;

class ObjectViewMapperTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-11-02T10:30:00Z");

    @Test
    void everyComponentCrossesTheBoundaryWithItsValue() {
        ObjectId id = new ObjectId(UUID.fromString("5b6f0c2e-7d1a-4c4e-9a55-0f7a2e1d9c10"));
        ObjectView released = new ObjectView(
                id,
                new TypeRef(TASK_IRI),
                QueryFixtures.TENANT,
                3L,
                Optional.of("OPEN"),
                Map.of(new PropertyRef(TITLE_IRI), Value.text("Write the report")),
                new Audit(CREATED, "alice", UPDATED, "bob"),
                false);

        org.sequeless.spi.query.ObjectView prototype = ObjectViewMapper.toPrototype(released);

        assertThat(prototype.id()).isEqualTo(id);
        assertThat(prototype.type()).isEqualTo(new org.sequeless.spi.object.TypeRef(TASK_IRI));
        assertThat(prototype.tenant()).isEqualTo(QueryFixtures.TENANT);
        assertThat(prototype.version()).isEqualTo(3L);
        assertThat(prototype.state()).contains("OPEN");
        assertThat(prototype.properties())
                .containsExactly(
                        Map.entry(new org.sequeless.spi.object.PropertyRef(TITLE_IRI), Value.text("Write the report")));
        assertThat(prototype.audit()).isEqualTo(new org.sequeless.spi.object.Audit(CREATED, "alice", UPDATED, "bob"));
        assertThat(prototype.deleted()).isFalse();
    }

    @Test
    void aSoftDeletedFlagCrossesUnchanged() {
        ObjectView released = new ObjectView(
                new ObjectId(UUID.randomUUID()),
                new TypeRef(TASK_IRI),
                QueryFixtures.TENANT,
                1L,
                Optional.empty(),
                Map.of(),
                new Audit(CREATED, "alice", CREATED, "alice"),
                true);

        assertThat(ObjectViewMapper.toPrototype(released).deleted()).isTrue();
    }
}
