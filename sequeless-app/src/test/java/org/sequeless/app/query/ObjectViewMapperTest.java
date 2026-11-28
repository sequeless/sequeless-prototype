package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.TypeRef;
import org.sequeless.spi.object.BusinessObject;

class ObjectViewMapperTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-11-02T10:30:00Z");

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final TenantId MAPPER_TENANT = new TenantId("acme");
    private static final String MAPPER_TASK_IRI = "https://sequeless.dev/ns/mapper-test#Task";
    private static final org.sequeless.spi.object.PropertyRef MAPPER_TITLE =
            new org.sequeless.spi.object.PropertyRef(MAPPER_TASK_IRI + "/title");

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

    @Test
    void toViewCarriesEveryComponentAcrossUnchanged() {
        BusinessObject object =
                mapperObject(ObjectId.random(), Optional.of("Open"), Map.of(MAPPER_TITLE, Value.text("Plan")), true);

        org.sequeless.spi.query.ObjectView view = ObjectViewMapper.toView(object);

        assertThat(view.id()).isEqualTo(object.id());
        assertThat(view.type()).isEqualTo(new org.sequeless.spi.object.TypeRef(MAPPER_TASK_IRI));
        assertThat(view.tenant()).isEqualTo(MAPPER_TENANT);
        assertThat(view.version()).isEqualTo(3);
        assertThat(view.state()).contains("Open");
        assertThat(view.properties()).isEqualTo(object.properties());
        assertThat(view.audit()).isEqualTo(object.audit());
        assertThat(view.deleted()).isTrue();
    }

    @Test
    void roundTripRebuildsAnEqualBusinessObject() {
        BusinessObject object =
                mapperObject(ObjectId.random(), Optional.empty(), Map.of(MAPPER_TITLE, Value.text("Plan")), false);

        BusinessObject rebuilt = ObjectViewMapper.toBusinessObject(ObjectViewMapper.toView(object));

        assertThat(rebuilt).isEqualTo(object);
    }

    @Test
    void mappingAListKeepsItsOrder() {
        List<BusinessObject> objects = List.of(
                mapperObject(ObjectId.random(), Optional.empty(), Map.of(), false),
                mapperObject(ObjectId.random(), Optional.of("Open"), Map.of(), false),
                mapperObject(ObjectId.random(), Optional.empty(), Map.of(MAPPER_TITLE, Value.text("Last")), true));

        List<BusinessObject> rebuilt = objects.stream()
                .map(ObjectViewMapper::toView)
                .map(ObjectViewMapper::toBusinessObject)
                .toList();

        assertThat(rebuilt).containsExactlyElementsOf(objects);
    }

    private static BusinessObject mapperObject(
            ObjectId id,
            Optional<String> state,
            Map<org.sequeless.spi.object.PropertyRef, Value> properties,
            boolean deleted) {
        return new BusinessObject(
                id,
                new org.sequeless.spi.object.TypeRef(MAPPER_TASK_IRI),
                MAPPER_TENANT,
                3,
                state,
                properties,
                new org.sequeless.spi.object.Audit(NOW, "alice", NOW.plusSeconds(60), "bob"),
                deleted);
    }
}
