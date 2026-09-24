package org.sequeless.core.derivation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Reference implementation of {@link DerivationRecomputer}. See that interface's javadoc for the
 * full contract this class implements: candidate resolution off a materialised {@link RollupRule},
 * commit-only-if-changed, and a jittered intra-call retry loop on {@link StaleObjectException}.
 *
 * <p>Like {@link org.sequeless.core.automation.DefaultTriggerEvaluator}, this class is an
 * inbound-port implementation an automation adapter calls back into, never the other way around.
 * Unlike that class, this one writes directly: it builds a {@link ChangeSet} and calls {@link
 * ObjectStorePort#commit} itself, deliberately bypassing {@code ValueCoercer}/{@code
 * StructuralValidator} (both of which reject a client write to a derived property) because this is
 * core writing a value it computed itself, not a client-supplied one. That is precisely what keeps a
 * materialised property {@code readOnly} to clients while still being writable from this path.
 */
public final class DefaultDerivationRecomputer implements DerivationRecomputer {

    private final OntologyPort ontologyPort;
    private final ObjectStorePort objectStorePort;
    private final DerivationPlanner derivationPlanner;
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Clock clock;

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every candidate/target read and every commit goes through;
     *     must not be {@code null}
     * @param derivationPlanner the planner whose {@link DerivationPlanner#computeRule} this class
     *     calls to recompute a single rule for a single target; must not be {@code null}
     * @param maxAttempts the maximum number of commit attempts per {@code (target, propertyIri)}
     *     pair before a {@link StaleObjectException} is allowed to propagate; must be at least 1
     * @param baseDelay the base of the jittered backoff slept between retry attempts; must not be
     *     {@code null}, must not be negative
     * @param clock the clock the {@link Audit#updatedAt()} timestamp on every commit is drawn from;
     *     must not be {@code null}
     * @throws NullPointerException if any reference-typed argument is {@code null}
     * @throws IllegalArgumentException if {@code maxAttempts} is less than 1 or {@code baseDelay} is
     *     negative
     */
    public DefaultDerivationRecomputer(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        DerivationPlanner derivationPlanner,
        int maxAttempts,
        Duration baseDelay,
        Clock clock) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.derivationPlanner =
            Objects.requireNonNull(derivationPlanner, "derivationPlanner must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Objects.requireNonNull(baseDelay, "baseDelay must not be null");
        if (baseDelay.isNegative()) {
            throw new IllegalArgumentException("baseDelay must not be negative");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void recompute(Scope scope, OutboxEntry changeEvent) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeEvent, "changeEvent must not be null");

        Map<String, Object> payload = changeEvent.payload();
        ObjectId changedId = ObjectId.parse((String) payload.get("objectId"));
        String changedTypeIri = (String) payload.get("typeIri");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        Optional<TypeDefinition> maybeChangedType = snapshot.type(changedTypeIri);
        if (maybeChangedType.isEmpty()) {
            return;
        }
        TypeDefinition changedType = maybeChangedType.get();

        boolean deleted = OutboxEntry.KIND_OBJECT_DELETED.equals(changeEvent.kind());
        Map<PropertyRef, Value> changedProperties;
        if (deleted) {
            changedProperties = decodePropertiesSnapshot(payload);
        } else {
            Optional<BusinessObject> found = objectStorePort.find(scope, changedId);
            if (found.isEmpty()) {
                // Stale event: the object has since been deleted. No-op.
                return;
            }
            changedProperties = found.get().properties();
        }

        // (targetId, propertyIri) -> the rule to recompute for it. A LinkedHashMap keyed on both
        // so the same target/property pair discovered via more than one matching rule collapses to
        // one recompute, deterministically, in discovery order.
        record TargetProperty(ObjectId targetId, String propertyIri) {}
        Map<TargetProperty, RollupRule> work = new LinkedHashMap<>();

        for (TypeDefinition type : snapshot.types()) {
            for (PropertyDefinition property : type.properties()) {
                if (property.derivation().isEmpty()) {
                    continue;
                }
                // A materialised PluginRule cannot occur here at all: the Jena mapper now rejects
                // sq:materialised true on a sq:Plugin at ontology activation time, because a
                // PluginRule carries no sourceTypeIri/viaIri and so offers no generic way to
                // discover which targets a changed object affects. The instanceof guard below is
                // defensive - it should be unreachable - rather than the primary safeguard.
                if (!(property.derivation().get() instanceof RollupRule rollup) || !rollup.materialised()) {
                    continue;
                }
                if (!TypeHierarchy.isSubtypeOf(snapshot, changedType.iri(), rollup.sourceTypeIri())) {
                    continue;
                }
                Value viaValue = changedProperties.get(new PropertyRef(rollup.viaIri()));
                if (viaValue == null) {
                    continue;
                }
                for (ObjectId targetId : collectReferencedIds(viaValue)) {
                    work.putIfAbsent(new TargetProperty(targetId, property.iri()), rollup);
                }
            }
        }

        for (Map.Entry<TargetProperty, RollupRule> entry : work.entrySet()) {
            recomputeOne(
                scope, snapshot, entry.getKey().targetId(), entry.getKey().propertyIri(), entry.getValue());
        }
    }

    /**
     * Recomputes and, if needed, commits the value of {@code propertyIri} on {@code targetId}
     * according to {@code rule}, retrying up to {@link #maxAttempts} times on {@link
     * StaleObjectException}. Every attempt re-reads {@code targetId} fresh and re-runs {@link
     * DerivationPlanner#computeRule} from scratch, so a retry always converges on the value that is
     * true as of that attempt's read, regardless of how many other writers raced it.
     *
     * <p>Deliberately calls {@link DerivationPlanner#computeRule} once per {@code (target, rule)}
     * pair here, not once per rule across a whole page the way {@link DerivationPlanner#apply} does
     * — that page-wide batching bound belongs to a single request serving one page of objects; this
     * path is driven by independent async events, one changed object and its affected targets at a
     * time, so there is no page to batch across.
     */
    private void recomputeOne(
        Scope scope, MetaModelSnapshot snapshot, ObjectId targetId, String propertyIri, RollupRule rule) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<BusinessObject> found = objectStorePort.find(scope, targetId);
            if (found.isEmpty()) {
                // Stale/deleted target: nothing to recompute.
                return;
            }
            BusinessObject target = found.get();

            Map<ObjectId, Value> computed =
                derivationPlanner.computeRule(scope, snapshot, rule, Set.of(targetId), List.of(target));
            Value newValue = computed.get(targetId);
            PropertyRef propertyRef = new PropertyRef(propertyIri);
            Value currentValue = target.properties().get(propertyRef);

            if (Objects.equals(newValue, currentValue)) {
                // Commit-only-if-changed: both the load-test guarantee (a no-op recompute writes
                // nothing) and the loop terminator (a recompute that does write dispatches another
                // recompute that this time finds nothing changed and stops here).
                return;
            }

            Map<PropertyRef, Value> newProperties = new HashMap<>(target.properties());
            if (newValue == null) {
                newProperties.remove(propertyRef);
            } else {
                newProperties.put(propertyRef, newValue);
            }

            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            BusinessObject candidate =
                new BusinessObject(
                    target.id(),
                    target.type(),
                    target.tenant(),
                    target.version() + 1,
                    target.state(),
                    newProperties,
                    new Audit(
                        target.audit().createdAt(),
                        target.audit().createdBy(),
                        now,
                        scope.principal().id()),
                    target.deleted());

            ChangeSet changeSet =
                new ChangeSet(
                    List.of(new Update(candidate, target.version())),
                    List.of(objectUpdatedEntry(targetId, target.version() + 1, target.type().iri(), now)));

            try {
                objectStorePort.commit(scope, changeSet);
                return;
            } catch (StaleObjectException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                sleepJittered(attempt);
                // Loop again: re-read, re-aggregate, recompute from scratch.
            }
        }
    }

    /**
     * Sleeps a jittered backoff before the next retry attempt: {@link #baseDelay} scaled by a
     * random factor in {@code [0.5, 1.5)}, so concurrently racing recomputers do not retry in
     * lockstep.
     */
    private void sleepJittered(int attempt) {
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble();
        long millis = Math.max(1, (long) (baseDelay.toMillis() * jitter));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds an {@code ObjectUpdated} outbox entry whose payload matches the shape {@code
     * DefaultBusinessObjectService.outboxPayload} produces — duplicated here rather than shared,
     * exactly as {@code DefaultActionExecutor} already keeps its own copy of the same small
     * map-builder, since that method is private to a different class.
     */
    private static OutboxEntry objectUpdatedEntry(
        ObjectId id, long version, String typeIri, Instant occurredAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", id.value().toString());
        payload.put("version", version);
        payload.put("typeIri", typeIri);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_UPDATED, payload, occurredAt);
    }

    /**
     * Collects the {@link ObjectId}s a relationship {@link Value} currently references: a bare
     * {@link ReferenceValue}, or every {@link ReferenceValue} element of a {@link ListValue}. A
     * scalar value or a {@link ListValue} of non-references contributes no ids.
     */
    private static Set<ObjectId> collectReferencedIds(Value value) {
        Set<ObjectId> out = new LinkedHashSet<>();
        if (value instanceof ReferenceValue reference) {
            out.add(reference.target());
        } else if (value instanceof ListValue list) {
            for (Value element : list.values()) {
                if (element instanceof ReferenceValue reference) {
                    out.add(reference.target());
                }
            }
        }
        return out;
    }

    /**
     * Decodes an {@code ObjectDeleted} payload's {@code properties} snapshot — a full-IRI-keyed,
     * {@link PayloadValueCodec}-encoded map captured at delete time precisely because {@link
     * ObjectStorePort#find} can never read a soft-deleted object back. Same decode idiom as {@link
     * org.sequeless.core.automation.DefaultTriggerEvaluator#decodePropertiesSnapshot}.
     */
    @SuppressWarnings("unchecked")
    private static Map<PropertyRef, Value> decodePropertiesSnapshot(Map<String, Object> payload) {
        Map<String, Object> raw = (Map<String, Object>) payload.get("properties");
        Map<PropertyRef, Value> result = new HashMap<>();
        raw.forEach(
            (iri, tagged) -> result.put(new PropertyRef(iri), PayloadValueCodec.fromPayload(tagged)));
        return result;
    }
}
