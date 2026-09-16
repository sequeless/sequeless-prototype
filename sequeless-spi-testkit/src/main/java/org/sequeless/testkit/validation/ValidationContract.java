package org.sequeless.testkit.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link ValidationPort}'s
 * interface-level javadoc. Every {@link ValidationPort} implementation — adapter or test double —
 * is expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever fixture {@link #fixtureFor(OntologyDocument)} supplies for a given ontology
 * document.
 *
 * <p>Unlike {@code ObjectStoreContract}, this contract cannot be ontology-agnostic: exercising a
 * real shape violation needs a real {@link MetaModelSnapshot} built from an ontology document, and
 * this testkit deliberately carries no OWL/RDF library capable of building one. Building the
 * snapshot is delegated entirely to whichever adapter's contract test extends this class:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends ValidationContract {
 *     protected Fixture fixtureFor(OntologyDocument document) {
 *         MetaModelSnapshot snapshot = MyOntologyAdapter.snapshotOf(document);
 *         return new Fixture(MyAdapter.forSnapshot(snapshot), snapshot);
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here dictates which
 * violations a specific ontology's shapes must produce for an arbitrary object — that is entirely
 * shape-specific and belongs in the adapter's own test suite, against its own shapes. This contract
 * asserts only null-safety, determinism, and the "unmodifiable, non-null list" shape of the result,
 * plus two representative violations against the reference-domain ontology's own {@code
 * ex:TaskShape} and {@code ex:PersonShape} — enough to prove {@link #fixtureFor(OntologyDocument)}
 * actually wires SHACL evaluation through, not to enumerate every constraint the reference ontology
 * declares.
 */
public abstract class ValidationContract {

    /**
     * A {@link ValidationPort} paired with the {@link MetaModelSnapshot} it was built to validate
     * against, since a conforming {@link ValidationPort} implementation is typically tied to the
     * ontology its shapes were read from.
     *
     * @param port the validation port under test; must not be {@code null}
     * @param snapshot the type system {@code port} validates objects against; must not be {@code
     *     null}
     */
    public record Fixture(ValidationPort port, MetaModelSnapshot snapshot) {

        public Fixture {
            Objects.requireNonNull(port, "port must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }
    }

    /**
     * @param document the ontology document to build a {@link ValidationPort} and matching {@link
     *     MetaModelSnapshot} from; must not be {@code null}
     * @return a fresh {@link Fixture} pairing an implementation under test with the snapshot it
     *     validates against; invoked fresh for every {@code @Test} method
     */
    protected abstract Fixture fixtureFor(OntologyDocument document);

    @Test
    void validateRejectsNullScope() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject task = task(Map.of(new PropertyRef(Fixtures.TITLE_IRI), Value.text("x")));

        assertThatNullPointerException()
            .isThrownBy(() -> fixture.port().validate(null, fixture.snapshot(), task));
    }

    @Test
    void validateRejectsNullSnapshot() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject task = task(Map.of(new PropertyRef(Fixtures.TITLE_IRI), Value.text("x")));

        assertThatNullPointerException()
            .isThrownBy(() -> fixture.port().validate(Fixtures.defaultScope(), null, task));
    }

    @Test
    void validateRejectsNullObject() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());

        assertThatNullPointerException()
            .isThrownBy(
                () -> fixture.port().validate(Fixtures.defaultScope(), fixture.snapshot(), null));
    }

    @Test
    void validReferenceTaskProducesNoViolations() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject task =
            task(
                Map.of(
                    new PropertyRef(Fixtures.TITLE_IRI), Value.text("Write plan"),
                    new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI), Value.decimal(new BigDecimal("40"))));

        List<Violation> violations =
            fixture.port().validate(Fixtures.defaultScope(), fixture.snapshot(), task);

        assertThat(violations).isEmpty();
    }

    @Test
    void outOfRangeEstimatedHoursProducesViolationOnItsPath() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject task =
            task(
                Map.of(
                    new PropertyRef(Fixtures.TITLE_IRI), Value.text("Too big"),
                    new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI),
                        Value.decimal(new BigDecimal("5000"))));

        List<Violation> violations =
            fixture.port().validate(Fixtures.defaultScope(), fixture.snapshot(), task);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(Violation::path).contains(Fixtures.ESTIMATED_HOURS_IRI);
    }

    @Test
    void invalidPersonEmailProducesViolationOnItsPath() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject person =
            person(
                Map.of(
                    new PropertyRef(Fixtures.NAME_IRI), Value.text("Alice"),
                    new PropertyRef(Fixtures.EMAIL_IRI), Value.text("not-an-email")));

        List<Violation> violations =
            fixture.port().validate(Fixtures.defaultScope(), fixture.snapshot(), person);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(Violation::path).contains(Fixtures.EMAIL_IRI);
    }

    @Test
    void validateIsDeterministic() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();
        BusinessObject task =
            task(
                Map.of(
                    new PropertyRef(Fixtures.TITLE_IRI), Value.text("Determinism check"),
                    new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI),
                        Value.decimal(new BigDecimal("5000"))));

        List<Violation> first = fixture.port().validate(scope, fixture.snapshot(), task);
        List<Violation> second = fixture.port().validate(scope, fixture.snapshot(), task);

        assertThat(second).containsExactlyInAnyOrderElementsOf(first);
    }

    @Test
    void violationsListIsUnmodifiable() {
        Fixture fixture = fixtureFor(Fixtures.referenceOntology());
        BusinessObject task =
            task(
                Map.of(
                    new PropertyRef(Fixtures.TITLE_IRI), Value.text("Immutable check"),
                    new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI),
                        Value.decimal(new BigDecimal("5000"))));

        List<Violation> violations =
            fixture.port().validate(Fixtures.defaultScope(), fixture.snapshot(), task);

        assertThat(violations).isNotEmpty();
        assertThatThrownBy(() -> violations.add(new Violation("", "extra")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static BusinessObject task(Map<PropertyRef, Value> properties) {
        return businessObject(new TypeRef(Fixtures.TASK_IRI), properties);
    }

    private static BusinessObject person(Map<PropertyRef, Value> properties) {
        return businessObject(new TypeRef(Fixtures.PERSON_IRI), properties);
    }

    private static BusinessObject businessObject(TypeRef type, Map<PropertyRef, Value> properties) {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Audit audit = new Audit(at, "tester", at, "tester");
        return new BusinessObject(
            ObjectId.random(), type, TenantId.DEFAULT, 1, Optional.empty(), properties, audit, false);
    }
}
