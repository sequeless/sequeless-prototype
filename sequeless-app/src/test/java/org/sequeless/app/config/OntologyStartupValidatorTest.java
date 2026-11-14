package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.api.OntologyException;
import org.sequeless.ontology.api.OntologyIssue;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.api.Severity;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.testkit.query.QueryFixtures;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Unit-tests {@link OntologyStartupValidator#run} against hand-built doubles, with no Spring context
 * and no Postgres container. The startup call is the host's only index preparation for the seed
 * ontology, so these tests prove that call on its own.
 */
class OntologyStartupValidatorTest {

    private static final Scope STARTUP_SCOPE = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

    @Test
    void startupReadsTheDefaultTenantSchemaTwiceAndPreparesIndexesForItsVersionOnce() {
        Schema schema = QueryFixtures.schema();
        List<TenantId> readTenants = new ArrayList<>();
        SchemaSource source = tenant -> {
            readTenants.add(tenant);
            return schema;
        };
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();

        new OntologyStartupValidator(source, indexPreparation(source, port)).run(new DefaultApplicationArguments());

        // The validator reads the Schema to learn its version, and the use case reads it again to pass
        // to the port: two reads of the same, unchanged Schema.
        assertThat(readTenants).containsExactly(TenantId.DEFAULT, TenantId.DEFAULT);
        assertThat(port.calls).containsExactly(new PrepareCall(STARTUP_SCOPE, schema.ontologyVersion(), schema));
    }

    @Test
    void anUnreadableOntologyPropagatesItsExceptionWithoutPreparingIndexes() {
        OntologyIssue error = new OntologyIssue(Severity.ERROR, Optional.empty(), "inconsistent by fixture");
        OntologyException failure = new OntologyException(new OntologyReport(false, List.of(error)));
        SchemaSource source = tenant -> {
            throw failure;
        };
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort();
        OntologyStartupValidator validator = new OntologyStartupValidator(source, indexPreparation(source, port));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isSameAs(failure);
        assertThat(port.calls).isEmpty();
    }

    /** The host use case over {@code port}, with no backfill needed and no transaction. */
    private static IndexPreparation indexPreparation(SchemaSource source, IndexPreparationPort port) {
        return new IndexPreparation(source, port, scope -> {}, TransactionOperations.withoutTransaction());
    }

    /** One {@link IndexPreparationPort#prepare} invocation: the scope, version and Schema it was given. */
    private record PrepareCall(Scope scope, OntologyVersion version, Schema schema) {}

    /** An {@link IndexPreparationPort} that records each {@code prepare} call and registers nothing new. */
    private static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        private final List<PrepareCall> calls = new ArrayList<>();

        @Override
        public boolean prepare(Scope scope, OntologyVersion version, Schema schema) {
            calls.add(new PrepareCall(scope, version, schema));
            return false;
        }
    }
}
