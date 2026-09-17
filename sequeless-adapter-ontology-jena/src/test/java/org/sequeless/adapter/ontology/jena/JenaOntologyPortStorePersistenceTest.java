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
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.testkit.Fixtures;

/**
 * Covers {@link JenaOntologyPort#fromStore} and the store-aware {@link JenaOntologyPort#reload} and
 * {@link JenaOntologyPort#importDocument} paths added on top of the plain {@code fromDocument}/
 * {@code fromSource} behaviour already covered by {@link JenaOntologyPortContractTest}.
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
            .isThrownBy(
                () -> JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL))
            .withMessageContaining(scope.tenantId().value())
            .withMessageContaining(SOURCE_PROPERTY);

        assertThatIllegalStateException()
            .isThrownBy(
                () -> JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, "  ", ReasonerSetting.OWL));
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

        MetaModelSnapshot fromStoredDocument =
            JenaOntologyPort.fromDocument(active.get().document(), ReasonerSetting.OWL).snapshot(scope);
        assertThat(port.snapshot(scope)).isEqualTo(fromStoredDocument);
    }

    @Test
    void noActiveDocumentAndInconsistentSourceNeverActivates() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();

        assertThatThrownBy(
                () ->
                    JenaOntologyPort.fromStore(
                        store, scope, SOURCE_PROPERTY, INCONSISTENT_SOURCE, ReasonerSetting.OWL))
            .isInstanceOf(OntologyException.class);

        assertThat(store.active(scope)).isEmpty();
    }

    @Test
    void activeDocumentPresentIsUsedInsteadOfSourceAndIsNotReactivated() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument stored = Fixtures.referenceOntology();
        store.activate(scope, stored);

        JenaOntologyPort port =
            JenaOntologyPort.fromStore(
                store, scope, SOURCE_PROPERTY, INCONSISTENT_SOURCE, ReasonerSetting.OWL);

        assertThat(store.active(scope)).isPresent();
        assertThat(store.active(scope).get().version()).isEqualTo(1);

        MetaModelSnapshot fromStoredDocument =
            JenaOntologyPort.fromDocument(stored, ReasonerSetting.OWL).snapshot(scope);
        assertThat(port.snapshot(scope)).isEqualTo(fromStoredDocument);
    }

    @Test
    void reloadReReadsWhateverIsCurrentlyActiveInTheStore() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument first = Fixtures.referenceOntology();
        store.activate(scope, first);

        JenaOntologyPort port =
            JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);

        OntologyDocument second = Fixtures.inverseTransitiveOntology();
        store.activate(scope, second);

        MetaModelSnapshot reloaded = port.reload(scope);
        MetaModelSnapshot expected = JenaOntologyPort.fromDocument(second, ReasonerSetting.OWL).snapshot(scope);
        assertThat(reloaded).isEqualTo(expected);
    }

    @Test
    void importDocumentActivatesInTheStoreBeforeSwapping() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        store.activate(scope, Fixtures.referenceOntology());

        JenaOntologyPort port =
            JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);

        OntologyDocument replacement = Fixtures.inverseTransitiveOntology();
        port.importDocument(scope, replacement, ImportMode.REPLACE);

        Optional<StoredOntologyDocument> active = store.active(scope);
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(2);
        assertThat(active.get().document()).isEqualTo(replacement);

        MetaModelSnapshot expected =
            JenaOntologyPort.fromDocument(replacement, ReasonerSetting.OWL).snapshot(scope);
        assertThat(port.snapshot(scope)).isEqualTo(expected);
    }

    @Test
    void importDocumentWithInconsistentReplacementNeverActivates() {
        InMemoryOntologyDocumentStore store = new InMemoryOntologyDocumentStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument original = Fixtures.referenceOntology();
        store.activate(scope, original);

        JenaOntologyPort port =
            JenaOntologyPort.fromStore(store, scope, SOURCE_PROPERTY, null, ReasonerSetting.OWL);
        MetaModelSnapshot before = port.snapshot(scope);

        assertThatThrownBy(
                () ->
                    port.importDocument(
                        scope, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
            .isInstanceOf(OntologyException.class);

        Optional<StoredOntologyDocument> active = store.active(scope);
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(1);
        assertThat(active.get().document()).isEqualTo(original);
        assertThat(port.snapshot(scope)).isEqualTo(before);
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
