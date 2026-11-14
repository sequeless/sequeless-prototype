package org.sequeless.testkit.query;

import static org.sequeless.ontology.testfixtures.Fixtures.ASSIGNED_TO_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.BELONGS_TO_PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.CREATED_AT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.DELIVERABLE_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.DESCRIPTION_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.DUE_DATE_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.ESTIMATED_HOURS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.HAS_TASK_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.OPEN_TASK_COUNT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PERSON_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PRIORITY_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.REFERENCE_ONTOLOGY_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.STATUS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TASK_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TITLE_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TOTAL_ESTIMATED_HOURS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.WORK_ITEM_IRI;

import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * A hand-built {@link Schema} mirroring {@code reference.ttl} exactly — the same {@code
 * Deliverable}/{@code WorkItem}/{@code Task}/{@code Project}/{@code Person} hierarchy {@link
 * org.sequeless.ontology.testfixtures.Fixtures} already declares IRI constants for — constructed
 * directly from SPI records rather than parsed from Turtle, since this testkit is deliberately
 * Jena-free.
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
 * <p>The two {@code sq:Rollup} derivations are not part of the Schema: they are held by the
 * Behaviour half, which {@link #behaviour()} builds.
 *
 * <p>Every property and type here uses {@link DisplayHints#none()} — query logic never reads
 * display hints, so building meaningful ones would only be noise.
 */
public final class QueryFixtures {

    /**
     * {@code ex:name}, declared here rather than in the released {@code Fixtures}, which has no such
     * member. The value is the released {@code REFERENCE_ONTOLOGY_IRI} plus {@code #name}.
     */
    public static final String NAME_IRI = REFERENCE_ONTOLOGY_IRI + "#name";

    /**
     * {@code ex:email}, declared here rather than in the released {@code Fixtures}, which has no such
     * member. The value is the released {@code REFERENCE_ONTOLOGY_IRI} plus {@code #email}.
     */
    public static final String EMAIL_IRI = REFERENCE_ONTOLOGY_IRI + "#email";

    private QueryFixtures() {}

    /**
     * The Schema of the reference ontology, as a fixture.
     *
     * @return a fresh {@link Schema} at {@link OntologyVersion} 1 mirroring {@code reference.ttl},
     *     post-T1 (a searchable {@code WorkItem.description}, and {@code sq:displayLabel} on {@code
     *     Deliverable.title} and {@code Person.name})
     */
    public static Schema schema() {
        List<TypeDefinition> types = List.of(deliverable(), workItem(), task(), project(), person());
        return new Schema(
                new OntologyVersion(1),
                REFERENCE_ONTOLOGY_IRI,
                Optional.empty(),
                Map.of("ex", REFERENCE_ONTOLOGY_IRI + "#", "sq", "https://sequeless.dev/ns/meta#"),
                types);
    }

    /**
     * The Behaviour of the reference ontology, as a fixture, for use with {@link #schema()}.
     *
     * @return a fresh {@link Behaviour} at {@link OntologyVersion} 1 for {@link #schema()}: no state
     *     machine, and the two {@code sq:Rollup} derivations of {@code ex:Project}, keyed by the IRI
     *     of the property each one derives ({@code ex:openTaskCount} and {@code
     *     ex:totalEstimatedHours})
     */
    public static Behaviour behaviour() {
        return new Behaviour(
                new OntologyVersion(1),
                Map.of(),
                Map.of(
                        OPEN_TASK_COUNT_IRI, openTaskCountRule(),
                        TOTAL_ESTIMATED_HOURS_IRI, totalEstimatedHoursRule()));
    }

    /** {@code ex:Deliverable} — abstract, no supertypes, declares {@code title} and {@code createdAt}. */
    public static TypeDefinition deliverable() {
        return new TypeDefinition(
                DELIVERABLE_IRI,
                "Deliverable",
                List.of(),
                List.of(titleOptional(), createdAt()),
                DisplayHints.none(),
                true);
    }

    /**
     * {@code ex:WorkItem} — abstract, subclass of {@link #deliverable()}; properties list carries
     * {@code Deliverable}'s inherited properties plus {@code WorkItem}'s own {@code status} and
     * {@code description}.
     */
    public static TypeDefinition workItem() {
        return new TypeDefinition(
                WORK_ITEM_IRI,
                "Work Item",
                List.of(DELIVERABLE_IRI),
                List.of(titleOptional(), createdAt(), status(), description()),
                DisplayHints.none(),
                true);
    }

    /**
     * {@code ex:Task} — concrete, subclass of {@link #workItem()} (and transitively {@link
     * #deliverable()}); properties list is every inherited property plus Task's own five, nine in
     * total. {@code title} here carries Task's own {@code min 1} restriction, not the {@code
     * optional()} view every other type sees.
     */
    public static TypeDefinition task() {
        return new TypeDefinition(
                TASK_IRI,
                "Task",
                List.of(WORK_ITEM_IRI, DELIVERABLE_IRI),
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
                false);
    }

    /**
     * {@code ex:Project} — concrete, subclass of {@link #workItem()} (and transitively {@link
     * #deliverable()}); properties list is every inherited property plus Project's own {@code
     * hasTask, openTaskCount, totalEstimatedHours}, seven in total. The latter two are {@code
     * readOnly}, and their {@code sq:Rollup} rules are held by {@link #behaviour()}, matching the
     * exact {@link RollupRule} shape {@code SnapshotMapper} produces from {@code reference.ttl}.
     */
    public static TypeDefinition project() {
        return new TypeDefinition(
                PROJECT_IRI,
                "Project",
                List.of(WORK_ITEM_IRI, DELIVERABLE_IRI),
                List.of(
                        titleOptional(),
                        createdAt(),
                        status(),
                        description(),
                        hasTask(),
                        openTaskCount(),
                        totalEstimatedHours()),
                DisplayHints.none(),
                false);
    }

    /** {@code ex:Person} — concrete, standalone, declares {@code name} (required) and {@code email}. */
    public static TypeDefinition person() {
        return new TypeDefinition(
                PERSON_IRI, "Person", List.of(), List.of(name(), email()), DisplayHints.none(), false);
    }

    // -- Attribute definitions -------------------------------------------------------------

    private static AttributeDefinition titleOptional() {
        return attribute(TITLE_IRI, "Title", Cardinality.optional(), false, false, true, false, true, Datatype.STRING);
    }

    private static AttributeDefinition titleRequired() {
        return attribute(TITLE_IRI, "Title", Cardinality.required(), false, false, true, false, true, Datatype.STRING);
    }

    private static AttributeDefinition status() {
        return attribute(
                STATUS_IRI, "Status", Cardinality.optional(), true, true, false, false, false, Datatype.STRING);
    }

    private static AttributeDefinition description() {
        return attribute(
                DESCRIPTION_IRI,
                "Description",
                Cardinality.optional(),
                false,
                false,
                true,
                false,
                false,
                Datatype.STRING);
    }

    private static AttributeDefinition createdAt() {
        return attribute(
                CREATED_AT_IRI,
                "Created At",
                Cardinality.optional(),
                false,
                false,
                false,
                true,
                false,
                Datatype.DATE_TIME);
    }

    private static AttributeDefinition priority() {
        return attribute(
                PRIORITY_IRI, "Priority", Cardinality.optional(), false, false, false, false, false, Datatype.INTEGER);
    }

    /**
     * {@code max 1} (T5's {@code owl:maxCardinality 1} restriction on {@code ex:Task}) — a change
     * from this fixture's earlier unbounded cardinality, made so a stored value is a bare tagged
     * value rather than a JSON list, which is what lets {@link #totalEstimatedHours()}'s rollup sum
     * it in SQL.
     */
    private static AttributeDefinition estimatedHours() {
        return attribute(
                ESTIMATED_HOURS_IRI,
                "Estimated Hours",
                Cardinality.atMost(1),
                false,
                false,
                false,
                false,
                false,
                Datatype.DECIMAL);
    }

    /**
     * {@code ex:openTaskCount} — {@code xsd:integer}, {@code readOnly}. Its {@code sq:Rollup} rule is
     * held by {@link #behaviour()}, not by this attribute.
     */
    private static AttributeDefinition openTaskCount() {
        return attribute(
                OPEN_TASK_COUNT_IRI,
                "Open Tasks",
                Cardinality.atMost(1),
                false,
                false,
                false,
                true,
                false,
                Datatype.INTEGER);
    }

    /**
     * {@code ex:totalEstimatedHours} — {@code xsd:decimal}, {@code readOnly}. Its {@code sq:Rollup}
     * rule is held by {@link #behaviour()}, not by this attribute.
     */
    private static AttributeDefinition totalEstimatedHours() {
        return attribute(
                TOTAL_ESTIMATED_HOURS_IRI,
                "Total Estimated Hours",
                Cardinality.atMost(1),
                false,
                false,
                false,
                true,
                false,
                Datatype.DECIMAL);
    }

    private static AttributeDefinition dueDate() {
        return attribute(
                DUE_DATE_IRI, "Due Date", Cardinality.optional(), false, false, false, false, false, Datatype.DATE);
    }

    private static AttributeDefinition name() {
        return attribute(NAME_IRI, "Name", Cardinality.required(), false, false, false, false, true, Datatype.STRING);
    }

    private static AttributeDefinition email() {
        return attribute(
                EMAIL_IRI, "Email", Cardinality.optional(), false, false, false, false, false, Datatype.STRING);
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
                iri,
                label,
                cardinality,
                facet,
                indexed,
                searchable,
                readOnly,
                displayLabel,
                DisplayHints.none(),
                datatype);
    }

    // -- Derivation rules (held by behaviour()) -----------------------------------------------

    /**
     * {@code ex:openTaskCount}'s rule: {@code count} of {@link org.sequeless.ontology.testfixtures.Fixtures#TASK_IRI}
     * via {@link org.sequeless.ontology.testfixtures.Fixtures#BELONGS_TO_PROJECT_IRI}, filtered to
     * {@code status != "done"}. Matches the exact {@link RollupRule} {@code SnapshotMapper} produces
     * from {@code reference.ttl}'s {@code ex:openTaskCount} declaration: a single {@code sq:Criterion}
     * is its own {@link FieldFilter}, with the filter-core op {@code "is not"} and a JSON text value.
     */
    private static RollupRule openTaskCountRule() {
        return new RollupRule(
                TASK_IRI,
                BELONGS_TO_PROJECT_IRI,
                AggregateFunction.COUNT,
                Optional.empty(),
                Optional.of(new FieldFilter(STATUS_IRI, "is not", TextNode.valueOf("done"))),
                false);
    }

    /**
     * {@code ex:totalEstimatedHours}'s rule: {@code sum} of {@link
     * org.sequeless.ontology.testfixtures.Fixtures#ESTIMATED_HOURS_IRI} over {@link
     * org.sequeless.ontology.testfixtures.Fixtures#TASK_IRI} via {@link
     * org.sequeless.ontology.testfixtures.Fixtures#BELONGS_TO_PROJECT_IRI}, no filter. Matches the
     * exact {@link RollupRule} {@code SnapshotMapper} produces from {@code reference.ttl}'s {@code
     * ex:totalEstimatedHours} declaration, which has no {@code sq:filter}: the criteria are empty.
     */
    private static RollupRule totalEstimatedHoursRule() {
        return new RollupRule(
                TASK_IRI,
                BELONGS_TO_PROJECT_IRI,
                AggregateFunction.SUM,
                Optional.of(ESTIMATED_HOURS_IRI),
                Optional.empty(),
                false);
    }

    // -- Relationship definitions ------------------------------------------------------------

    private static RelationshipDefinition assignedTo() {
        return relationship(
                ASSIGNED_TO_IRI,
                "Assigned To",
                Cardinality.atMost(1),
                true,
                false,
                false,
                false,
                false,
                PERSON_IRI,
                Optional.empty(),
                false);
    }

    private static RelationshipDefinition belongsToProject() {
        return relationship(
                BELONGS_TO_PROJECT_IRI,
                "Belongs To Project",
                Cardinality.atMost(1),
                false,
                false,
                false,
                false,
                false,
                PROJECT_IRI,
                Optional.of(HAS_TASK_IRI),
                false);
    }

    private static RelationshipDefinition hasTask() {
        return relationship(
                HAS_TASK_IRI,
                "Has Task",
                Cardinality.optional(),
                false,
                false,
                false,
                false,
                false,
                TASK_IRI,
                Optional.empty(),
                false);
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
                iri,
                label,
                cardinality,
                facet,
                indexed,
                searchable,
                readOnly,
                displayLabel,
                DisplayHints.none(),
                targetTypeIri,
                inverseIri,
                transitive);
    }
}
