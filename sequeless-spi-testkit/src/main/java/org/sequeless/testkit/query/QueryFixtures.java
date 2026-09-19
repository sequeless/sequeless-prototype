package org.sequeless.testkit.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;
import org.sequeless.testkit.Fixtures;

/**
 * A hand-built {@link MetaModelSnapshot} mirroring {@code reference.ttl} exactly — the same
 * {@code Deliverable}/{@code WorkItem}/{@code Task}/{@code Project}/{@code Person} hierarchy
 * {@link Fixtures} already declares IRI constants for — constructed directly from SPI records
 * rather than parsed from Turtle, since this testkit is deliberately Jena-free.
 *
 * <p>{@link TypeDefinition#properties()} on each concrete type includes not only that type's own
 * properties but every property it inherits, exactly as a real {@code SnapshotMapper} would
 * produce: {@link #task()}'s properties list is {@code title, createdAt} (from {@code
 * Deliverable}), {@code status, description} (from {@code WorkItem}), plus {@code priority,
 * assignedTo, belongsToProject, estimatedHours, dueDate} (Task's own) — nine properties in total.
 * {@link #project()}'s properties list is {@code title, createdAt} (from {@code Deliverable}),
 * {@code status, description} (from {@code WorkItem}), plus {@code hasTask, openTaskCount,
 * totalEstimatedHours} (Project's own, the latter two {@code sq:Rollup}-derived) — seven
 * properties in total. {@code title}'s cardinality differs by which type's properties list it
 * appears in: {@link #task()} carries a {@code min 1} view of it (Task's own {@code
 * owl:minCardinality} restriction), while every other type carries the unrestricted {@code
 * optional()} view, matching {@code reference.ttl} precisely. {@code estimatedHours} carries a
 * {@code max 1} view (T5's {@code owl:maxCardinality 1} restriction on {@code ex:Task}), which is
 * what lets {@code ValueCoercer} store it as a bare tagged value rather than a JSON list — the
 * shape {@code totalEstimatedHours}'s rollup sums.
 *
 * <p>Every property and type here uses {@link DisplayHints#none()} — query logic never reads
 * display hints, so building meaningful ones would only be noise.
 */
public final class QueryFixtures {

    private QueryFixtures() {}

    /**
     * @return a fresh {@link MetaModelSnapshot} mirroring {@code reference.ttl}, post-T1 (a
     *     searchable {@code WorkItem.description}, and {@code sq:displayLabel} on {@code
     *     Deliverable.title} and {@code Person.name})
     */
    public static MetaModelSnapshot snapshot() {
        List<TypeDefinition> types =
            List.of(deliverable(), workItem(), task(), project(), person());
        return new MetaModelSnapshot(
            Fixtures.REFERENCE_ONTOLOGY_IRI,
            Optional.empty(),
            Map.of("ex", Fixtures.REFERENCE_ONTOLOGY_IRI + "#", "sq", "https://sequeless.dev/ns/meta#"),
            types,
            new OntologyReport(true, List.of()));
    }

    /** {@code ex:Deliverable} — abstract, no supertypes, declares {@code title} and {@code createdAt}. */
    public static TypeDefinition deliverable() {
        return new TypeDefinition(
            Fixtures.DELIVERABLE_IRI,
            "Deliverable",
            List.of(),
            List.of(titleOptional(), createdAt()),
            DisplayHints.none(),
            true,
            Optional.empty());
    }

    /**
     * {@code ex:WorkItem} — abstract, subclass of {@link #deliverable()}; properties list carries
     * {@code Deliverable}'s inherited properties plus {@code WorkItem}'s own {@code status} and
     * {@code description}.
     */
    public static TypeDefinition workItem() {
        return new TypeDefinition(
            Fixtures.WORK_ITEM_IRI,
            "Work Item",
            List.of(Fixtures.DELIVERABLE_IRI),
            List.of(titleOptional(), createdAt(), status(), description()),
            DisplayHints.none(),
            true,
            Optional.empty());
    }

    /**
     * {@code ex:Task} — concrete, subclass of {@link #workItem()} (and transitively {@link
     * #deliverable()}); properties list is every inherited property plus Task's own five, nine in
     * total. {@code title} here carries Task's own {@code min 1} restriction, not the {@code
     * optional()} view every other type sees.
     */
    public static TypeDefinition task() {
        return new TypeDefinition(
            Fixtures.TASK_IRI,
            "Task",
            List.of(Fixtures.WORK_ITEM_IRI, Fixtures.DELIVERABLE_IRI),
            List.of(
                titleRequired(),
                createdAt(),
                status(),
                description(),
                priority(),
                assignedTo(),
                belongsToProject(),
                estimatedHours(),
                dueDate()),
            DisplayHints.none(),
            false,
            Optional.empty());
    }

    /**
     * {@code ex:Project} — concrete, subclass of {@link #workItem()} (and transitively {@link
     * #deliverable()}); properties list is every inherited property plus Project's own {@code
     * hasTask, openTaskCount, totalEstimatedHours}, seven in total. The latter two are {@code
     * sq:Rollup}-derived (T5) and {@code readOnly}, matching the exact {@link RollupRule} shape
     * {@code SnapshotMapper} produces from {@code reference.ttl}.
     */
    public static TypeDefinition project() {
        return new TypeDefinition(
            Fixtures.PROJECT_IRI,
            "Project",
            List.of(Fixtures.WORK_ITEM_IRI, Fixtures.DELIVERABLE_IRI),
            List.of(
                titleOptional(),
                createdAt(),
                status(),
                description(),
                hasTask(),
                openTaskCount(),
                totalEstimatedHours()),
            DisplayHints.none(),
            false,
            Optional.empty());
    }

    /** {@code ex:Person} — concrete, standalone, declares {@code name} (required) and {@code email}. */
    public static TypeDefinition person() {
        return new TypeDefinition(
            Fixtures.PERSON_IRI,
            "Person",
            List.of(),
            List.of(name(), email()),
            DisplayHints.none(),
            false,
            Optional.empty());
    }

    // -- Attribute definitions -------------------------------------------------------------

    private static AttributeDefinition titleOptional() {
        return attribute(
            Fixtures.TITLE_IRI, "Title", Cardinality.optional(), false, false, true, false, true,
            Datatype.STRING);
    }

    private static AttributeDefinition titleRequired() {
        return attribute(
            Fixtures.TITLE_IRI, "Title", Cardinality.required(), false, false, true, false, true,
            Datatype.STRING);
    }

    private static AttributeDefinition status() {
        return attribute(
            Fixtures.STATUS_IRI, "Status", Cardinality.optional(), true, true, false, false, false,
            Datatype.STRING);
    }

    private static AttributeDefinition description() {
        return attribute(
            Fixtures.DESCRIPTION_IRI, "Description", Cardinality.optional(), false, false, true,
            false, false, Datatype.STRING);
    }

    private static AttributeDefinition createdAt() {
        return attribute(
            Fixtures.CREATED_AT_IRI, "Created At", Cardinality.optional(), false, false, false,
            true, false, Datatype.DATE_TIME);
    }

    private static AttributeDefinition priority() {
        return attribute(
            Fixtures.PRIORITY_IRI, "Priority", Cardinality.optional(), false, false, false, false,
            false, Datatype.INTEGER);
    }

    /**
     * {@code max 1} (T5's {@code owl:maxCardinality 1} restriction on {@code ex:Task}) — a change
     * from this fixture's earlier unbounded cardinality, made so a stored value is a bare tagged
     * value rather than a JSON list, which is what lets {@link #totalEstimatedHours()}'s rollup sum
     * it in SQL.
     */
    private static AttributeDefinition estimatedHours() {
        return attribute(
            Fixtures.ESTIMATED_HOURS_IRI, "Estimated Hours", Cardinality.atMost(1), false, false,
            false, false, false, Datatype.DECIMAL);
    }

    /**
     * {@code ex:openTaskCount} — {@code sq:Rollup}-derived: {@code count} of {@link
     * Fixtures#TASK_IRI} via {@link Fixtures#BELONGS_TO_PROJECT_IRI}, filtered to {@code status !=
     * "done"}. Matches the exact {@link RollupRule} {@code SnapshotMapper} produces from {@code
     * reference.ttl}'s {@code ex:openTaskCount} declaration, including the typed {@link TextValue}
     * criterion value.
     */
    private static AttributeDefinition openTaskCount() {
        return new AttributeDefinition(
            Fixtures.OPEN_TASK_COUNT_IRI,
            "Open Tasks",
            Cardinality.atMost(1),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Optional.of(
                new RollupRule(
                    Fixtures.TASK_IRI,
                    Fixtures.BELONGS_TO_PROJECT_IRI,
                    AggregateFunction.COUNT,
                    Optional.empty(),
                    List.of(
                        new Criterion(
                            Fixtures.STATUS_IRI, Operator.NE, Optional.of(new TextValue("done")))))),
            Datatype.INTEGER);
    }

    /**
     * {@code ex:totalEstimatedHours} — {@code sq:Rollup}-derived: {@code sum} of {@link
     * Fixtures#ESTIMATED_HOURS_IRI} over {@link Fixtures#TASK_IRI} via {@link
     * Fixtures#BELONGS_TO_PROJECT_IRI}, no filter. Matches the exact {@link RollupRule} {@code
     * SnapshotMapper} produces from {@code reference.ttl}'s {@code ex:totalEstimatedHours}
     * declaration.
     */
    private static AttributeDefinition totalEstimatedHours() {
        return new AttributeDefinition(
            Fixtures.TOTAL_ESTIMATED_HOURS_IRI,
            "Total Estimated Hours",
            Cardinality.atMost(1),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Optional.of(
                new RollupRule(
                    Fixtures.TASK_IRI,
                    Fixtures.BELONGS_TO_PROJECT_IRI,
                    AggregateFunction.SUM,
                    Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                    List.of())),
            Datatype.DECIMAL);
    }

    private static AttributeDefinition dueDate() {
        return attribute(
            Fixtures.DUE_DATE_IRI, "Due Date", Cardinality.optional(), false, false, false, false,
            false, Datatype.DATE);
    }

    private static AttributeDefinition name() {
        return attribute(
            Fixtures.NAME_IRI, "Name", Cardinality.required(), false, false, false, false, true,
            Datatype.STRING);
    }

    private static AttributeDefinition email() {
        return attribute(
            Fixtures.EMAIL_IRI, "Email", Cardinality.optional(), false, false, false, false, false,
            Datatype.STRING);
    }

    private static AttributeDefinition attribute(
        String iri,
        String label,
        Cardinality cardinality,
        boolean facet,
        boolean indexed,
        boolean searchable,
        boolean readOnly,
        boolean displayLabel,
        Datatype datatype) {
        return new AttributeDefinition(
            iri, label, cardinality, facet, indexed, searchable, readOnly, displayLabel,
            DisplayHints.none(), Optional.empty(), datatype);
    }

    // -- Relationship definitions ------------------------------------------------------------

    private static RelationshipDefinition assignedTo() {
        return relationship(
            Fixtures.ASSIGNED_TO_IRI, "Assigned To", Cardinality.atMost(1), true, false, false,
            false, false, Fixtures.PERSON_IRI, Optional.empty(), false);
    }

    private static RelationshipDefinition belongsToProject() {
        return relationship(
            Fixtures.BELONGS_TO_PROJECT_IRI, "Belongs To Project", Cardinality.atMost(1), false,
            false, false, false, false, Fixtures.PROJECT_IRI, Optional.of(Fixtures.HAS_TASK_IRI),
            false);
    }

    private static RelationshipDefinition hasTask() {
        return relationship(
            Fixtures.HAS_TASK_IRI, "Has Task", Cardinality.optional(), false, false, false, false,
            false, Fixtures.TASK_IRI, Optional.empty(), false);
    }

    private static RelationshipDefinition relationship(
        String iri,
        String label,
        Cardinality cardinality,
        boolean facet,
        boolean indexed,
        boolean searchable,
        boolean readOnly,
        boolean displayLabel,
        String targetTypeIri,
        Optional<String> inverseIri,
        boolean transitive) {
        return new RelationshipDefinition(
            iri, label, cardinality, facet, indexed, searchable, readOnly, displayLabel,
            DisplayHints.none(), Optional.empty(), targetTypeIri, inverseIri, transitive);
    }
}
