package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.testkit.Fixtures;

/**
 * Covers {@link JenaOntologyPort#fromStore} and the store-aware {@link JenaOntologyPort#reload} and
 * {@link JenaOntologyPort#importDocument} paths added on top of the plain {@code fromDocument}/
 * {@code fromSource} behaviour already covered by {@link JenaOntologyPortContractTest}. With a store,
 * every adopted activation takes the version the store gives it, not the in-memory counter's.
 *
 * <p>Uses a small local fake of {@link OntologyDocumentStore} rather than {@code
 * sequeless-spi-testkit}'s own in-memory implementation: that one is nested inside a test-scoped
 * class in {@code sequeless-spi-testkit}'s own test sources, which this module cannot depend on
 * (only its main sources are a test dependency here). Duplicating the ~15 lines locally is
 * cheaper and clearer than restructuring that module just to share it.
 */
class JenaOntologyPortStorePersistenceTest {

    private static final String VALID_SOURCE = "classpath:ontology/reference.ttl";
    private static final String INCONSISTENT_SOURCE = "classpath:ontology/inconsistent.ttl";
    private static final String SOURCE_PROPERTY = "sequeless.ontology.source";

    @Test
    void noActiveDocumentAndBlankSourceThrows() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();

        assertThatIllegalStateException()
                .isThrownBy(() -> JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL))
                .withMessageContaining(scope.tenantId().value())
                .withMessageContaining(SOURCE_PROPERTY);

        assertThatIllegalStateException()
                .isThrownBy(() -> JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, "  ", ReasonerSetting.OWL));
    }

    @Test
    void noActiveDocumentSeedsFromSourceAndActivatesIt() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();

        JenaOntologyPort port =
                JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, VALID_SOURCE, ReasonerSetting.OWL);

        Optional<StoredOntologyDocument> active = store.active(scope);
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(1);

        Halves fromStoredDocument =
                Halves.of(JenaOntologyPort.fromDocument(active.get().document(), ReasonerSetting.OWL));
        assertThat(Halves.of(port)).isEqualTo(fromStoredDocument);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(1));
    }

    @Test
    void noActiveDocumentAndInconsistentSourceNeverActivates() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();

        assertThatThrownBy(() -> JenaOntologyPort.fromStore(
                        store, scope, SOURCE_PROPERTY, INCONSISTENT_SOURCE, ReasonerSetting.OWL))
                .isInstanceOf(OntologyException.class);

        assertThat(store.active(scope)).isEmpty();
    }

    @Test
    void activeDocumentPresentIsUsedInsteadOfSourceAtItsStoredVersion() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        store.activate(scope, Fixtures.inverseTransitiveOntology());
        OntologyDocument stored = Fixtures.referenceOntology();
        store.activate(scope, stored); // version 2 is the active one

        JenaOntologyPort port =
                JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, INCONSISTENT_SOURCE, ReasonerSetting.OWL);

        assertThat(store.active(scope)).isPresent();
        assertThat(store.active(scope).get().version()).isEqualTo(2);

        Halves fromStoredDocument = Halves.of(JenaOntologyPort.fromDocument(stored, ReasonerSetting.OWL));
        assertThat(Halves.of(port).withoutVersion()).isEqualTo(fromStoredDocument.withoutVersion());
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(2));
    }

    @Test
    void reloadReReadsWhateverIsCurrentlyActiveAndTakesItsStoredVersion() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument first = Fixtures.referenceOntology();
        store.activate(scope, first);

        JenaOntologyPort port = JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);

        // Two activations behind the port's back: the store is now at version 3, while the in-memory
        // counter would only say 2. The reload must take the store's number.
        OntologyDocument second = Fixtures.inverseTransitiveOntology();
        store.activate(scope, second);
        store.activate(scope, second);

        OntologyReport reloaded = port.reload(scope);

        assertThat(reloaded.consistent()).isTrue();
        Halves expected = Halves.of(JenaOntologyPort.fromDocument(second, ReasonerSetting.OWL));
        assertThat(Halves.of(port).withoutVersion()).isEqualTo(expected.withoutVersion());
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(3));
    }

    @Test
    void aFailedStoreBackedReloadKeepsTheHalvesAndTheirVersion() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        store.activate(scope, Fixtures.referenceOntology());

        JenaOntologyPort port = JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);
        Halves before = Halves.of(port);

        // Activated behind the port's back: the store now holds an inconsistent document at version 2.
        store.activate(scope, Fixtures.inconsistentOntology());

        assertThatThrownBy(() -> port.reload(scope)).isInstanceOf(OntologyException.class);

        assertThat(Halves.of(port)).isEqualTo(before);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(1));
    }

    @Test
    void importDocumentActivatesInTheStoreBeforeSwappingAndTakesTheVersionItReturns() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        store.activate(scope, Fixtures.referenceOntology());
        store.activate(scope, Fixtures.referenceOntology()); // store is at version 2

        JenaOntologyPort port = JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);

        OntologyDocument replacement = Fixtures.inverseTransitiveOntology();
        ImportReport report = port.importDocument(scope, replacement, ImportMode.REPLACE);

        Optional<StoredOntologyDocument> active = store.active(scope);
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(3);
        assertThat(active.get().document()).isEqualTo(replacement);

        Halves expected = Halves.of(JenaOntologyPort.fromDocument(replacement, ReasonerSetting.OWL));
        assertThat(Halves.of(port).withoutVersion()).isEqualTo(expected.withoutVersion());
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(3));
        assertThat(report.typeCount())
                .isEqualTo(Halves.of(port).schema().types().size());
    }

    @Test
    void importDocumentWithInconsistentReplacementNeverActivates() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument original = Fixtures.referenceOntology();
        store.activate(scope, original);

        JenaOntologyPort port = JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);
        Halves before = Halves.of(port);

        assertThatThrownBy(() -> port.importDocument(scope, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
                .isInstanceOf(OntologyException.class);

        Optional<StoredOntologyDocument> active = store.active(scope);
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(1);
        assertThat(active.get().document()).isEqualTo(original);
        assertThat(Halves.of(port)).isEqualTo(before);
    }

    /**
     * A per-tenant version counter with no persistence at all, duplicated from {@code
     * sequeless-spi-testkit}'s own {@code InMemoryObjectStorePort.InMemoryOntologyDocumentStore} —
     * see this class's javadoc for why it cannot simply be reused.
     */
    private static final class InMemoryOntologyDocumentStore implements OntologyDocumentStore {

        private final Map<TenantId, StoredOntologyDocument> active = new LinkedHashMap<>();
        private final Map<TenantId, Integer> nextVersion = new LinkedHashMap<>();

        @Override
        public Optional<StoredOntologyDocument> active(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return Optional.ofNullable(active.get(scope.tenantId()));
        }

        @Override
        public StoredOntologyDocument activate(Scope scope, OntologyDocument document) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(document, "document must not be null");
            int version = nextVersion.merge(scope.tenantId(), 1, Integer::sum);
            StoredOntologyDocument stored =
                    new StoredOntologyDocument(UUID.randomUUID(), version, document, Instant.now());
            active.put(scope.tenantId(), stored);
            return stored;
        }
    }
}
