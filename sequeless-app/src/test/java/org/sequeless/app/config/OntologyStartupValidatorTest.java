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
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.ontology.api.OntologyException;
import org.sequeless.ontology.api.OntologyIssue;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.api.Severity;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.testkit.query.QueryFixtures;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Unit-tests {@link OntologyStartupValidator#run} against hand-built doubles, with no Spring context
 * and no Postgres container. The startup call is the host's only index preparation for the seed
 * ontology, so these tests prove that call on its own.
 */
class OntologyStartupValidatorTest {

    private static final Scope STARTUP_SCOPE = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

    @Test
    void startupReadsTheDefaultTenantSchemaAndPreparesItsIndexesOnce() {
        Schema schema = QueryFixtures.schema();
        List<TenantId> readTenants = new ArrayList<>();
        SchemaSource source = tenant -> {
            readTenants.add(tenant);
            return schema;
        };
        RecordingQueryPort queryPort = new RecordingQueryPort();

        new OntologyStartupValidator(source, queryPort).run(new DefaultApplicationArguments());

        assertThat(readTenants).containsExactly(TenantId.DEFAULT);
        assertThat(queryPort.calls).containsExactly(new EnsureIndexesCall(STARTUP_SCOPE, schema));
    }

    @Test
    void anUnreadableOntologyPropagatesItsExceptionWithoutPreparingIndexes() {
        OntologyIssue error = new OntologyIssue(Severity.ERROR, Optional.empty(), "inconsistent by fixture");
        OntologyException failure = new OntologyException(new OntologyReport(false, List.of(error)));
        SchemaSource source = tenant -> {
            throw failure;
        };
        RecordingQueryPort queryPort = new RecordingQueryPort();
        OntologyStartupValidator validator = new OntologyStartupValidator(source, queryPort);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isSameAs(failure);
        assertThat(queryPort.calls).isEmpty();
    }

    /** One {@link QueryPort#ensureIndexes} invocation: the scope and the Schema it was given. */
    private record EnsureIndexesCall(Scope scope, Schema schema) {}

    /** A {@link QueryPort} that records each {@code ensureIndexes} call and nothing else. */
    private static final class RecordingQueryPort implements QueryPort {

        private final List<EnsureIndexesCall> calls = new ArrayList<>();

        @Override
        public void ensureIndexes(Scope scope, Schema schema) {
            calls.add(new EnsureIndexesCall(scope, schema));
        }

        @Override
        public QueryResult query(Scope scope, Schema schema, Query query) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public AggregateResult aggregate(Scope scope, Schema schema, AggregateRequest request) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
