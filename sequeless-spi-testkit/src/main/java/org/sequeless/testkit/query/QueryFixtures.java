package org.sequeless.testkit.query;

import static org.sequeless.ontology.testfixtures.Fixtures.BELONGS_TO_PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.ESTIMATED_HOURS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.OPEN_TASK_COUNT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.STATUS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TASK_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TOTAL_ESTIMATED_HOURS_IRI;

import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.OntologyVersion;

/**
 * Query-only fixtures for the reference ontology: its {@link Behaviour}, the two {@code sq:Rollup}
 * derivations of {@code ex:Project}. The Schema fixture is {@link org.sequeless.testkit.Fixtures#schema()}.
 */
public final class QueryFixtures {

    private QueryFixtures() {}

    /**
     * The Behaviour of the reference ontology, as a fixture, for use with {@link org.sequeless.testkit.Fixtures#schema()}.
     *
     * @return a fresh {@link Behaviour} at {@link OntologyVersion} 1 for {@link org.sequeless.testkit.Fixtures#schema()}: no state
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
}
