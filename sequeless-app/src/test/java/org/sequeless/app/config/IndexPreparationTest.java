package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.testkit.query.QueryFixtures;

/**
 * Unit-tests {@link IndexPreparation} against hand-built doubles: it reads the tenant's Schema and
 * hands it to the port under an anonymous principal, with the version it was given passed through
 * unchanged.
 */
class IndexPreparationTest {

    private static final TenantId TENANT = new TenantId("tenant-a");

    @Test
    void prepareReadsTheTenantSchemaAndPassesScopeVersionAndSchemaToThePort() {
        Schema schema = QueryFixtures.schema();
        List<TenantId> readTenants = new ArrayList<>();
        SchemaSource source = tenant -> {
            readTenants.add(tenant);
            return schema;
        };
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();

        new IndexPreparation(source, port).prepare(TENANT, schema.ontologyVersion());

        assertThat(readTenants).containsExactly(TENANT);
        assertThat(port.calls)
                .containsExactly(
                        new PrepareCall(new Scope(TENANT, Principal.ANONYMOUS), schema.ontologyVersion(), schema));
    }

    @Test
    void prepareDoesNotCheckTheVersionAgainstTheSchemaItReads() {
        Schema schema = QueryFixtures.schema();
        OntologyVersion unmatched = new OntologyVersion(schema.ontologyVersion().value() + 6);
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();

        new IndexPreparation(tenant -> schema, port).prepare(TENANT, unmatched);

        assertThat(port.calls)
                .containsExactly(new PrepareCall(new Scope(TENANT, Principal.ANONYMOUS), unmatched, schema));
    }

    @Test
    void constructorAndPrepareRejectNullArguments() {
        SchemaSource source = tenant -> QueryFixtures.schema();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        OntologyVersion version = new OntologyVersion(1);

        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(null, port));
        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(source, null));

        IndexPreparation useCase = new IndexPreparation(source, port);
        assertThatNullPointerException().isThrownBy(() -> useCase.prepare(null, version));
        assertThatNullPointerException().isThrownBy(() -> useCase.prepare(TENANT, null));
        assertThat(port.calls).isEmpty();
    }

    /** One {@link IndexPreparationPort#prepare} invocation: the scope, version and Schema it was given. */
    private record PrepareCall(Scope scope, OntologyVersion version, Schema schema) {}

    /** An {@link IndexPreparationPort} that records each {@code prepare} call and nothing else. */
    private static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        private final List<PrepareCall> calls = new ArrayList<>();

        @Override
        public void prepare(Scope scope, OntologyVersion version, Schema schema) {
            calls.add(new PrepareCall(scope, version, schema));
        }
    }
}
