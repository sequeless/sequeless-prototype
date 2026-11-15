package org.sequeless.app.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.sequeless.core.api.TypeDescription;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.ontology.behaviour.api.DerivationRule;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * Unit-tests the rollup summary that {@link TypeResponseMapper#toDetail} renders for a rollup with an
 * {@code is in} filter. The bridge gives such a criterion a list value, so its summary shows the value
 * as a bracketed list, not as a scalar.
 */
class TypeResponseMapperTest {

    private static final String NS = "https://sequeless.org/vocab/sq";
    private static final String TASK_IRI = NS + "#Task";
    private static final String PROJECT_IRI = NS + "#Project";
    private static final String STATUS_IRI = NS + "#status";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "#belongsToProject";
    private static final String OPEN_TASK_COUNT_IRI = NS + "#openTaskCount";

    private static final AttributeDefinition STATUS = new AttributeDefinition(
            STATUS_IRI,
            "status",
            new Cardinality(0, OptionalInt.of(1)),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.STRING);

    private static final AttributeDefinition OPEN_TASK_COUNT = new AttributeDefinition(
            OPEN_TASK_COUNT_IRI,
            "openTaskCount",
            new Cardinality(0, OptionalInt.of(1)),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Datatype.INTEGER);

    private static final TypeDefinition TASK =
            new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(STATUS), DisplayHints.none(), false);

    private static final TypeDefinition PROJECT =
            new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(OPEN_TASK_COUNT), DisplayHints.none(), false);

    private static final Schema SCHEMA =
            new Schema(new OntologyVersion(1), NS, Optional.empty(), Map.of(), List.of(TASK, PROJECT));

    @Test
    void rollupSummaryRendersAnInCriterionValueAsAList() {
        FieldFilter statusIn = new FieldFilter(
                STATUS_IRI, "is in", JsonNodeFactory.instance.arrayNode().add("x"));
        RollupRule openTaskCount = new RollupRule(
                TASK_IRI,
                BELONGS_TO_PROJECT_IRI,
                AggregateFunction.COUNT,
                Optional.empty(),
                Optional.of(statusIn),
                true);
        Map<String, DerivationRule> derivations = Map.of(OPEN_TASK_COUNT_IRI, openTaskCount);
        TypeDescription description = new TypeDescription(PROJECT, Optional.empty(), derivations, SCHEMA);

        TypeDetailResponse detail = TypeResponseMapper.toDetail(description, SCHEMA);

        AttributePropertyResponse property =
                (AttributePropertyResponse) detail.properties().get(0);
        assertThat(property.derivation().summary()).isEqualTo("count(Task via belongsToProject where status in ['x'])");
    }
}
