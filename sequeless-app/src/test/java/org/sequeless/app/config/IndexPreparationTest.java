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
import org.sequeless.spi.object.SearchVectorBackfillPort;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.testkit.query.QueryFixtures;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Unit-tests {@link IndexPreparation} against hand-built doubles: it reads the tenant's Schema and
 * hands it to the port under an anonymous principal, with the version it was given passed through
 * unchanged. It backfills exactly when the port reports a newly registered searchable property, and
 * the port and the backfill both run inside the transaction it is given. These tests prove call order
 * only; the rollback behaviour against a real database is proved by {@code
 * IndexPreparationTransactionProofTest}.
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
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(false, transactions);

        new IndexPreparation(source, port, new RecordingBackfillPort(transactions), transactions)
                .prepare(TENANT, schema.ontologyVersion());

        assertThat(readTenants).containsExactly(TENANT);
        assertThat(port.calls)
                .containsExactly(
                        new PrepareCall(new Scope(TENANT, Principal.ANONYMOUS), schema.ontologyVersion(), schema));
    }

    @Test
    void prepareDoesNotCheckTheVersionAgainstTheSchemaItReads() {
        Schema schema = QueryFixtures.schema();
        OntologyVersion unmatched = new OntologyVersion(schema.ontologyVersion().value() + 6);
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(false, transactions);

        new IndexPreparation(tenant -> schema, port, new RecordingBackfillPort(transactions), transactions)
                .prepare(TENANT, unmatched);

        assertThat(port.calls)
                .containsExactly(new PrepareCall(new Scope(TENANT, Principal.ANONYMOUS), unmatched, schema));
    }

    @Test
    void backfillRunsForTheSameScopeWhenThePortRegistersANewSearchableProperty() {
        Schema schema = QueryFixtures.schema();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(true, transactions);
        RecordingBackfillPort backfill = new RecordingBackfillPort(transactions);

        new IndexPreparation(tenant -> schema, port, backfill, transactions).prepare(TENANT, schema.ontologyVersion());

        assertThat(backfill.scopes).containsExactly(new Scope(TENANT, Principal.ANONYMOUS));
    }

    @Test
    void backfillIsSkippedWhenThePortRegistersNothingNew() {
        Schema schema = QueryFixtures.schema();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(false, transactions);
        RecordingBackfillPort backfill = new RecordingBackfillPort(transactions);

        new IndexPreparation(tenant -> schema, port, backfill, transactions).prepare(TENANT, schema.ontologyVersion());

        assertThat(port.calls).hasSize(1);
        assertThat(backfill.scopes).isEmpty();
    }

    @Test
    void prepareAndBackfillRunInsideOneTransactionAndTheTransactionCloses() {
        Schema schema = QueryFixtures.schema();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(true, transactions);
        RecordingBackfillPort backfill = new RecordingBackfillPort(transactions);

        new IndexPreparation(tenant -> schema, port, backfill, transactions).prepare(TENANT, schema.ontologyVersion());

        assertThat(transactions.executions).isEqualTo(1);
        assertThat(port.openDuringCalls).containsExactly(true);
        assertThat(backfill.openDuringCalls).containsExactly(true);
        assertThat(transactions.open).isFalse();
    }

    @Test
    void constructorAndPrepareRejectNullArguments() {
        SchemaSource source = tenant -> QueryFixtures.schema();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingIndexPreparationPort port = new RecordingIndexPreparationPort(false, transactions);
        RecordingBackfillPort backfill = new RecordingBackfillPort(transactions);
        OntologyVersion version = new OntologyVersion(1);

        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(null, port, backfill, transactions));
        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(source, null, backfill, transactions));
        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(source, port, null, transactions));
        assertThatNullPointerException().isThrownBy(() -> new IndexPreparation(source, port, backfill, null));

        IndexPreparation useCase = new IndexPreparation(source, port, backfill, transactions);
        assertThatNullPointerException().isThrownBy(() -> useCase.prepare(null, version));
        assertThatNullPointerException().isThrownBy(() -> useCase.prepare(TENANT, null));
        assertThat(port.calls).isEmpty();
        assertThat(backfill.scopes).isEmpty();
    }

    /** One {@link IndexPreparationPort#prepare} invocation: the scope, version and Schema it was given. */
    private record PrepareCall(Scope scope, OntologyVersion version, Schema schema) {}

    /**
     * A transaction boundary that counts its executions and reports whether it is open, so a double
     * can record that it ran inside one.
     */
    private static final class RecordingTransactions implements TransactionOperations {

        private int executions;
        private boolean open;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            executions++;
            open = true;
            try {
                return action.doInTransaction(new SimpleTransactionStatus());
            } finally {
                open = false;
            }
        }
    }

    /**
     * An {@link IndexPreparationPort} that records each {@code prepare} call, whether the transaction
     * was open at the time, and returns a fixed answer for whether a searchable property was new.
     */
    private static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        private final boolean registersNewProperty;
        private final RecordingTransactions transactions;
        private final List<PrepareCall> calls = new ArrayList<>();
        private final List<Boolean> openDuringCalls = new ArrayList<>();

        RecordingIndexPreparationPort(boolean registersNewProperty, RecordingTransactions transactions) {
            this.registersNewProperty = registersNewProperty;
            this.transactions = transactions;
        }

        @Override
        public boolean prepare(Scope scope, OntologyVersion version, Schema schema) {
            calls.add(new PrepareCall(scope, version, schema));
            openDuringCalls.add(transactions.open);
            return registersNewProperty;
        }
    }

    /** A {@link SearchVectorBackfillPort} that records each backfill and whether the transaction was open. */
    private static final class RecordingBackfillPort implements SearchVectorBackfillPort {

        private final RecordingTransactions transactions;
        private final List<Scope> scopes = new ArrayList<>();
        private final List<Boolean> openDuringCalls = new ArrayList<>();

        RecordingBackfillPort(RecordingTransactions transactions) {
            this.transactions = transactions;
        }

        @Override
        public void backfill(Scope scope) {
            scopes.add(scope);
            openDuringCalls.add(transactions.open);
        }
    }
}
