package org.sequeless.spi.automation;

import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The inbound port an automation adapter calls back into to keep every materialised derived
 * property (per {@code DerivationRule.materialised()}) correct in {@code sq_object.props}
 * whenever a domain event could have changed it — exactly what keeps {@code
 * ex:Project.openTaskCount} filterable via {@code filter[openTaskCount][eq]=0} instead of only
 * ever visible on read. See this package's {@code package-info.java} for why this port's
 * dependency direction is the mirror image of every outbound port in this SPI: {@code
 * sequeless-core} implements this interface ({@code
 * org.sequeless.core.usecase.DefaultDerivationRecomputer}), and an automation adapter (the
 * in-process relay, or a Temporal activity) calls it, rather than the other way around.
 *
 * <p>{@code recompute}'s payload carries {@code objectId}, {@code tenantId}, and {@code
 * principalId}, reconstructing the {@link Scope} the recompute runs under exactly as {@link
 * ActionExecutor}'s methods do; there is no dedicated {@code Recompute} outbox kind — recompute is
 * driven directly off the ordinary {@code ObjectCreated}/{@code ObjectUpdated}/{@code
 * ObjectDeleted} events the store already writes.
 */
public interface DerivationRecomputer {

    /**
     * From the object named by {@code changeEvent}'s {@code objectId}, finds every materialised
     * rule whose {@code sourceTypeIri} covers that object's type and whose {@code viaIri} is
     * declared on it, resolves the current target(s) that property points at (exactly as {@code
     * ex:Task.ex:belongsToProject} points at the {@code ex:Project} whose {@code openTaskCount}
     * must be kept current), and for each distinct {@code (target, propertyIri)} pair: reads the
     * target fresh, recomputes the one rule's value, and commits the change only if it actually
     * differs from what is currently stored. That commit-only-if-changed rule is deliberate: it is
     * both the load-test guarantee (a recompute that finds nothing changed writes nothing, so
     * racing recomputes converge without lost updates) and the loop terminator (a recompute that
     * does write commits an {@code ObjectUpdated}, which dispatches another recompute that this
     * time finds the value already correct and stops).
     *
     * <p>The commit bypasses {@code ValueCoercer}/{@code StructuralValidator} — both of which
     * reject client writes to a derived property — because this is core writing a value it computed
     * itself, not a client-supplied one; that is precisely what keeps a materialised property
     * {@code readOnly} to clients while still being writable from this path. A {@code
     * StaleObjectException} from the commit is retried (default 8 attempts, jittered ~20ms base);
     * because every attempt re-reads and re-aggregates from scratch, a retry always converges on
     * the true value regardless of how many other writers raced it.
     *
     * @param scope the tenant and principal to read and recompute on behalf of; must not be {@code
     *     null}
     * @param changeEvent the {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}-kind
     *     outbox entry describing the change that may have invalidated a materialised value; must
     *     not be {@code null}
     */
    void recompute(Scope scope, OutboxEntry changeEvent);
}
