package org.sequeless.testkit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.spi.query.ReadRequest;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link ReadPort}'s
 * interface-level javadoc. Every {@link ReadPort} implementation — adapter or test double — is
 * expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever {@link Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectStorePort} paired with a {@link
 * ReadPort} that reads the same underlying storage:
 *
 * <pre>{@code
 * class MyAdapterReadContractTest extends ReadContract {
 *     protected Environment freshEnvironment() {
 *         ObjectStorePort store = new MyAdapter();
 *         return new Environment(store, MyReadAdapter.over(store));
 *     }
 * }
 * }</pre>
 *
 * <p>Every fixture object uses arbitrary {@link TypeRef} and {@link PropertyRef} IRIs of its own
 * devising, because {@link ReadPort} has no notion of an ontology: a conforming implementation
 * must not need one to answer a read by id.
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion depends on a particular
 * storage technology. Every assertion is about what {@link ReadPort}'s javadoc documents: null
 * safety, the round trip of a committed object, the empty result for an unknown, soft-deleted or
 * other-tenant id, and the latest version after an update.
 */
public abstract class ReadContract {

    private static final TypeRef TYPE = new TypeRef("https://example.org/read#Thing");

    private static final PropertyRef TEXT_PROP = new PropertyRef("https://example.org/read#text");
    private static final PropertyRef INTEGER_PROP = new PropertyRef("https://example.org/read#integer");
    private static final PropertyRef DECIMAL_PROP = new PropertyRef("https://example.org/read#decimal");
    private static final PropertyRef BOOL_PROP = new PropertyRef("https://example.org/read#bool");
    private static final PropertyRef DATETIME_PROP = new PropertyRef("https://example.org/read#dateTime");
    private static final PropertyRef DATE_PROP = new PropertyRef("https://example.org/read#date");
    private static final PropertyRef REF_PROP = new PropertyRef("https://example.org/read#ref");
    private static final PropertyRef LIST_PROP = new PropertyRef("https://example.org/read#list");

    /**
     * A {@link ObjectStorePort} paired with the {@link ReadPort} that reads the same underlying
     * storage: a conforming {@link ReadPort} only makes sense against objects committed through a
     * particular store.
     *
     * @param store the store to seed fixture data into via {@link ObjectStorePort#commit}; must not
     *     be {@code null}
     * @param readPort the port under test, expected to answer reads over whatever {@code store}
     *     holds; must not be {@code null}
     */
    public record Environment(ObjectStorePort store, ReadPort readPort) {

        public Environment {
            Objects.requireNonNull(store, "store must not be null");
            Objects.requireNonNull(readPort, "readPort must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} pairing a brand-new, empty {@link ObjectStorePort} with a
     *     {@link ReadPort} that reads it; invoked fresh for every {@code @Test} method, so
     *     implementors may return new instances each time or shared ones, but the returned
     *     environment must not carry over state from a previous test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void readRejectsNullArguments() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        ReadRequest request = new ReadRequest(ObjectId.random());

        assertThatNullPointerException().isThrownBy(() -> env.readPort().read(null, request));
        assertThatNullPointerException().isThrownBy(() -> env.readPort().read(scope, null));
    }

    @Test
    void readReturnsCommittedObjectWithItsAudit() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        ObjectId id = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(
                TEXT_PROP, Value.text("hello"),
                INTEGER_PROP, Value.integer(42),
                DECIMAL_PROP, Value.decimal(new BigDecimal("12.50")),
                BOOL_PROP, Value.bool(true),
                DATETIME_PROP, Value.dateTime(at),
                DATE_PROP, Value.date(LocalDate.of(2026, 1, 1)),
                REF_PROP, Value.ref(ObjectId.random()),
                LIST_PROP, Value.list(List.of(Value.text("a"), Value.text("b"))));
        BusinessObject stored = objectAt(scope, id, properties, at, 1, false);

        env.store().commit(scope, new ChangeSet(List.of(new Create(stored)), List.of()));

        Optional<BusinessObject> found = env.readPort().read(scope, new ReadRequest(id));
        assertThat(found).contains(stored);
        assertThat(found.orElseThrow().audit()).isEqualTo(stored.audit());
    }

    @Test
    void readOfUnknownIdIsEmpty() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();

        assertThat(env.readPort().read(scope, new ReadRequest(ObjectId.random())))
                .isEmpty();
    }

    @Test
    void readReturnsLatestVersionAfterUpdate() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        ObjectId id = ObjectId.random();

        env.store()
                .commit(
                        scope,
                        new ChangeSet(
                                List.of(new Create(
                                        objectAt(scope, id, Map.of(TEXT_PROP, Value.text("v1")), at, 1, false))),
                                List.of()));
        env.store()
                .commit(
                        scope,
                        new ChangeSet(
                                List.of(new Update(
                                        objectAt(scope, id, Map.of(TEXT_PROP, Value.text("v2")), at, 1, false), 1)),
                                List.of()));

        Optional<BusinessObject> found = env.readPort().read(scope, new ReadRequest(id));
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().version()).isEqualTo(2);
        assertThat(found.orElseThrow().properties()).containsEntry(TEXT_PROP, Value.text("v2"));
    }

    @Test
    void readOfSoftDeletedObjectIsEmpty() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        ObjectId id = ObjectId.random();

        env.store()
                .commit(
                        scope,
                        new ChangeSet(List.of(new Create(objectAt(scope, id, Map.of(), at, 1, false))), List.of()));
        env.store().commit(scope, new ChangeSet(List.of(new Delete(id, 1, at.plusSeconds(1), "tester")), List.of()));

        assertThat(env.readPort().read(scope, new ReadRequest(id))).isEmpty();
    }

    @Test
    void readIsTenantScoped() {
        Environment env = freshEnvironment();
        Scope alice = Fixtures.scope("alice");
        Scope otherTenant = new Scope(new TenantId("other-tenant"), Principal.ANONYMOUS);
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        ObjectId id = ObjectId.random();

        env.store()
                .commit(
                        alice,
                        new ChangeSet(List.of(new Create(objectAt(alice, id, Map.of(), at, 1, false))), List.of()));

        assertThat(env.readPort().read(otherTenant, new ReadRequest(id))).isEmpty();
        assertThat(env.readPort().read(alice, new ReadRequest(id))).isPresent();
    }

    private static BusinessObject objectAt(
            Scope scope, ObjectId id, Map<PropertyRef, Value> properties, Instant at, long version, boolean deleted) {
        String principalId = scope.principal().id();
        Audit audit = new Audit(at, principalId, at, principalId);
        return new BusinessObject(id, TYPE, scope.tenantId(), version, Optional.empty(), properties, audit, deleted);
    }
}
