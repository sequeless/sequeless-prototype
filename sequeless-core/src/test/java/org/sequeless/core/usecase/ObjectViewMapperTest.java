package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;

class ObjectViewMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final TenantId TENANT = new TenantId("acme");
    private static final String TASK_IRI = "https://sequeless.dev/ns/mapper-test#Task";
    private static final PropertyRef TITLE = new PropertyRef(TASK_IRI + "/title");

    @Test
    void toViewCarriesEveryComponentAcrossUnchanged() {
        BusinessObject object = object(ObjectId.random(), Optional.of("Open"), Map.of(TITLE, Value.text("Plan")), true);

        ObjectView view = ObjectViewMapper.toView(object);

        assertThat(view.id()).isEqualTo(object.id());
        assertThat(view.type()).isEqualTo(new TypeRef(TASK_IRI));
        assertThat(view.tenant()).isEqualTo(TENANT);
        assertThat(view.version()).isEqualTo(3);
        assertThat(view.state()).contains("Open");
        assertThat(view.properties()).isEqualTo(object.properties());
        assertThat(view.audit()).isEqualTo(object.audit());
        assertThat(view.deleted()).isTrue();
    }

    @Test
    void roundTripRebuildsAnEqualBusinessObject() {
        BusinessObject object = object(ObjectId.random(), Optional.empty(), Map.of(TITLE, Value.text("Plan")), false);

        BusinessObject rebuilt = ObjectViewMapper.toBusinessObject(ObjectViewMapper.toView(object));

        assertThat(rebuilt).isEqualTo(object);
    }

    @Test
    void mappingAListKeepsItsOrder() {
        List<BusinessObject> objects = List.of(
                object(ObjectId.random(), Optional.empty(), Map.of(), false),
                object(ObjectId.random(), Optional.of("Open"), Map.of(), false),
                object(ObjectId.random(), Optional.empty(), Map.of(TITLE, Value.text("Last")), true));

        List<BusinessObject> rebuilt = objects.stream()
                .map(ObjectViewMapper::toView)
                .map(ObjectViewMapper::toBusinessObject)
                .toList();

        assertThat(rebuilt).containsExactlyElementsOf(objects);
    }

    private static BusinessObject object(
            ObjectId id, Optional<String> state, Map<PropertyRef, Value> properties, boolean deleted) {
        return new BusinessObject(
                id,
                new TypeRef(TASK_IRI),
                TENANT,
                3,
                state,
                properties,
                new Audit(NOW, "alice", NOW.plusSeconds(60), "bob"),
                deleted);
    }
}
