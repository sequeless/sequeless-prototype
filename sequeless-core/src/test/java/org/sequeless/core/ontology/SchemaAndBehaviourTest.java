package org.sequeless.core.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;

/** Unit tests for {@link SchemaAndBehaviour#read}, the version check that keeps one request on one activation. */
class SchemaAndBehaviourTest {

    private static final TenantId TENANT = new TenantId("acme");
    private static final String ONTOLOGY_IRI = "https://sequeless.dev/ns/ref";

    private static Schema schema(int version) {
        return new Schema(new OntologyVersion(version), ONTOLOGY_IRI, Optional.empty(), Map.of(), List.of());
    }

    private static Behaviour behaviour(int version) {
        return new Behaviour(new OntologyVersion(version), Map.of(), Map.of());
    }

    @Test
    void returnsBothHalvesReadOnceWhenTheirVersionsMatch() {
        AtomicInteger schemaReads = new AtomicInteger();
        AtomicInteger behaviourReads = new AtomicInteger();
        Schema schema = schema(1);
        Behaviour behaviour = behaviour(1);

        SchemaAndBehaviour result = SchemaAndBehaviour.read(
                TENANT,
                tenant -> {
                    schemaReads.incrementAndGet();
                    return schema;
                },
                tenant -> {
                    behaviourReads.incrementAndGet();
                    return behaviour;
                });

        assertThat(result.schema()).isSameAs(schema);
        assertThat(result.behaviour()).isSameAs(behaviour);
        assertThat(schemaReads).hasValue(1);
        assertThat(behaviourReads).hasValue(1);
    }

    @Test
    void readsBothHalvesOnceMoreWhenTheirVersionsDifferAndReturnsTheSecondPairWhenItMatches() {
        AtomicInteger schemaReads = new AtomicInteger();
        AtomicInteger behaviourReads = new AtomicInteger();
        Schema stale = schema(1);
        Schema fresh = schema(2);
        Behaviour behaviour = behaviour(2);

        // The first schema read is from the activation before a reload, the second from after it.
        SchemaAndBehaviour result = SchemaAndBehaviour.read(
                TENANT, tenant -> schemaReads.incrementAndGet() == 1 ? stale : fresh, tenant -> {
                    behaviourReads.incrementAndGet();
                    return behaviour;
                });

        assertThat(result.schema()).isSameAs(fresh);
        assertThat(result.behaviour()).isSameAs(behaviour);
        assertThat(schemaReads).hasValue(2);
        assertThat(behaviourReads).hasValue(2);
    }

    @Test
    void throwsIllegalStateWhenTheVersionsStillDifferAfterTheSecondRead() {
        AtomicInteger schemaReads = new AtomicInteger();
        AtomicInteger behaviourReads = new AtomicInteger();
        SchemaSource schemas = tenant -> {
            schemaReads.incrementAndGet();
            return schema(1);
        };
        BehaviourSource behaviours = tenant -> {
            behaviourReads.incrementAndGet();
            return behaviour(2);
        };

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> SchemaAndBehaviour.read(TENANT, schemas, behaviours));
        assertThat(schemaReads).hasValue(2);
        assertThat(behaviourReads).hasValue(2);
    }

    @Test
    void rejectsNullArguments() {
        SchemaSource schemas = tenant -> schema(1);
        BehaviourSource behaviours = tenant -> behaviour(1);

        assertThatNullPointerException().isThrownBy(() -> SchemaAndBehaviour.read(null, schemas, behaviours));
        assertThatNullPointerException().isThrownBy(() -> SchemaAndBehaviour.read(TENANT, null, behaviours));
        assertThatNullPointerException().isThrownBy(() -> SchemaAndBehaviour.read(TENANT, schemas, null));
    }

    @Test
    void recordRejectsNullHalves() {
        assertThatNullPointerException().isThrownBy(() -> new SchemaAndBehaviour(null, behaviour(1)));
        assertThatNullPointerException().isThrownBy(() -> new SchemaAndBehaviour(schema(1), null));
    }
}
