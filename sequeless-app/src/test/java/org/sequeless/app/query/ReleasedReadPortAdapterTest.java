package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.app.query.QueryFixtures.SCOPE;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TENANT;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.TypeRef;

class ReleasedReadPortAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");

    @Test
    void aFoundObjectIsReturnedAsAPrototypeView() {
        ObjectId id = new ObjectId(UUID.fromString("5b6f0c2e-7d1a-4c4e-9a55-0f7a2e1d9c10"));
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        queryService.readResult = Optional.of(new ObjectView(
                id,
                new TypeRef(TASK_IRI),
                TENANT,
                2L,
                Optional.empty(),
                Map.of(),
                new Audit(NOW, "alice", NOW, "alice"),
                false));

        Optional<org.sequeless.spi.query.ObjectView> read =
                new ReleasedReadPortAdapter(queryService).read(SCOPE, new org.sequeless.spi.query.ReadRequest(id));

        assertThat(queryService.reads).containsExactly(id);
        assertThat(read).hasValueSatisfying(view -> {
            assertThat(view.id()).isEqualTo(id);
            assertThat(view.version()).isEqualTo(2L);
            assertThat(view.type()).isEqualTo(new org.sequeless.spi.object.TypeRef(TASK_IRI));
        });
    }

    @Test
    void anUnknownObjectIsEmpty() {
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();

        assertThat(new ReleasedReadPortAdapter(queryService)
                        .read(SCOPE, new org.sequeless.spi.query.ReadRequest(new ObjectId(UUID.randomUUID()))))
                .isEmpty();
    }
}
