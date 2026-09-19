package org.sequeless.testkit.derivation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sequeless.spi.derivation.DerivationContext;
import org.sequeless.spi.derivation.DerivationPlugin;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.testkit.Fixtures;

/**
 * Sample {@code sq:Plugin}-backed derivation: {@code ex:Person.workload} is the sum of {@code
 * ex:estimatedHours} over every {@code ex:Task} assigned to that person ({@code ex:assignedTo}).
 * Registered as a {@link java.util.ServiceLoader} provider for {@link DerivationPlugin} under this
 * module's {@code META-INF/services/org.sequeless.spi.derivation.DerivationPlugin}, and referenced
 * by name ({@code "workload"}) from {@code reference-plugin.ttl}'s {@code ex:workload} declaration
 * ({@code sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ]}).
 *
 * <p>This is deliberately written to demonstrate the pattern a real plug-in is expected to follow,
 * rather than the simplest possible implementation: instead of loading every {@code ex:Task}
 * assigned to any of {@code objects} into memory and summing them here, it issues a single {@link
 * org.sequeless.spi.query.QueryPort#aggregate} call for the whole batch of Person ids at once —
 * exactly the "one request per rule per page, never one per object" bound the planner already
 * gives a declarative {@code sq:Rollup} for free. A plug-in that instead queried once per object,
 * or pulled an unbounded working set into memory to compute its own aggregate, would defeat the
 * entire point of computing derived properties through {@link DerivationContext#queryPort()}
 * rather than {@link DerivationContext#snapshot()} plus ad hoc storage access.
 *
 * <p>A no-arg constructor is required: {@code ServiceLoader} instantiates providers reflectively.
 */
public final class WorkloadDerivationPlugin implements DerivationPlugin {

    private static final Set<String> SOURCE_TYPES = Set.of(Fixtures.TASK_IRI);

    public WorkloadDerivationPlugin() {}

    @Override
    public String name() {
        return "workload";
    }

    @Override
    public Map<ObjectId, Value> derive(DerivationContext context, List<BusinessObject> objects) {
        Set<ObjectId> targetIds =
            objects.stream().map(BusinessObject::id).collect(Collectors.toUnmodifiableSet());
        AggregateRequest request =
            new AggregateRequest(
                SOURCE_TYPES,
                Fixtures.ASSIGNED_TO_IRI,
                targetIds,
                AggregateFunction.SUM,
                Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                List.of());
        AggregateResult result =
            context.queryPort().aggregate(context.scope(), context.snapshot(), request);
        return result.values();
    }
}
