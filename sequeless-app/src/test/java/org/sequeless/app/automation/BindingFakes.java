package org.sequeless.app.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sequeless.app.query.ObjectQueryService;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.PluginRule;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.store.core.api.BrowseQuery;
import org.sequeless.store.core.api.BusinessObjectService;
import org.sequeless.store.core.api.TransitionService;

/**
 * Hand-built doubles for the automation binding tests, in the repo's no-mocking-library style. Each double records
 * what it was asked and fails with the exception a test configures, so a test can show that a rejected write left
 * nothing behind.
 */
final class BindingFakes {

    static final Scope SCOPE =
            new Scope(new TenantId("tenant-1"), new org.sequeless.kernel.identity.Principal("automation", Set.of()));

    private BindingFakes() {}

    static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by this test");
    }

    /**
     * A {@link BusinessObjectService} over a map of current views. Reads return the view; {@code add} and {@code edit}
     * record their call, unless a failure is configured, in which case the failure is thrown first and nothing is
     * recorded.
     */
    static final class RecordingObjects implements BusinessObjectService, ObjectQueryService {

        final Map<ObjectId, ObjectView> views = new HashMap<>();
        final List<Added> added = new ArrayList<>();
        final List<Edited> edited = new ArrayList<>();
        RuntimeException readFailure;
        RuntimeException addFailure;
        RuntimeException editFailure;

        record Added(ObjectId id, String type, Map<String, Object> properties) {}

        record Edited(ObjectId id, long expectedVersion, Map<String, Object> properties) {}

        @Override
        public Optional<TypeRef> typeOf(Scope scope, ObjectId id) {
            return Optional.ofNullable(views.get(id)).map(ObjectView::type);
        }

        @Override
        public ObjectView read(Scope scope, String type, ObjectId id) {
            if (readFailure != null) {
                throw readFailure;
            }
            ObjectView view = views.get(id);
            if (view == null) {
                throw new org.sequeless.kernel.values.ObjectNotFoundException(id);
            }
            return view;
        }

        @Override
        public BusinessObject add(Scope scope, String type, Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public BusinessObject add(Scope scope, String type, ObjectId id, Map<String, Object> properties) {
            if (addFailure != null) {
                throw addFailure;
            }
            added.add(new Added(id, type, Map.copyOf(properties)));
            return null;
        }

        @Override
        public BusinessObject edit(
                Scope scope, String type, ObjectId id, long expectedVersion, Map<String, Object> properties) {
            if (editFailure != null) {
                throw editFailure;
            }
            edited.add(new Edited(id, expectedVersion, new HashMap<>(properties)));
            return null;
        }

        @Override
        public QueryResult browse(Scope scope, String type, BrowseQuery query) {
            throw unsupported();
        }

        @Override
        public void delete(Scope scope, String type, ObjectId id, java.util.OptionalLong expectedVersion) {
            throw unsupported();
        }
    }

    /** A {@link TransitionService} whose {@code fireAutomated} returns or throws what the test configures. */
    static final class RecordingTransitions implements TransitionService {

        Optional<BusinessObject> result = Optional.empty();
        RuntimeException failure;
        final List<TriggerKind> kinds = new ArrayList<>();

        @Override
        public BusinessObject fire(Scope scope, String type, ObjectId id, String transitionName, long expectedVersion) {
            throw unsupported();
        }

        @Override
        public Optional<BusinessObject> fireAutomated(
                Scope scope, ObjectId id, String transitionName, TriggerKind expected) {
            kinds.add(expected);
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public void signal(Scope scope, String type, ObjectId id, String signalName, Map<String, Object> body) {
            throw unsupported();
        }
    }

    /**
     * A {@link SchemaSource} with one type, {@code typeIri}, that declares each given property with its cardinality. The
     * shaping reads the declared cardinality from here, so a test chooses which properties are multi-valued.
     */
    static SchemaSource schemaDeclaring(String typeIri, Map<String, Cardinality> cardinalities) {
        List<PropertyDefinition> properties = cardinalities.entrySet().stream()
                .<PropertyDefinition>map(entry -> new AttributeDefinition(
                        entry.getKey(),
                        entry.getKey(),
                        entry.getValue(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        DisplayHints.none(),
                        Datatype.STRING))
                .toList();
        TypeDefinition type = new TypeDefinition(typeIri, "Thing", List.of(), properties, DisplayHints.none(), false);
        Schema schema = new Schema(
                new OntologyVersion(1), "https://sequeless.dev/ns/test", Optional.empty(), Map.of(), List.of(type));
        return tenant -> schema;
    }

    /** A {@link BehaviourSource} whose behaviour marks the given property IRIs as derived. */
    static BehaviourSource behaviourWithDerived(String... derivedIris) {
        Map<String, org.sequeless.ontology.behaviour.api.DerivationRule> derivations = new HashMap<>();
        for (String iri : derivedIris) {
            derivations.put(iri, new PluginRule("plugin", false));
        }
        Behaviour behaviour = new Behaviour(new OntologyVersion(1), Map.of(), derivations);
        return tenant -> behaviour;
    }

    /** Builds a live {@link BusinessObject} of {@code typeIri}, for a fake transition to return. */
    static BusinessObject businessObject(ObjectId id, String typeIri) {
        Audit audit = new Audit(
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                "automation",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                "automation");
        return new BusinessObject(
                id, new TypeRef(typeIri), SCOPE.tenantId(), 4, Optional.empty(), Map.of(), audit, false);
    }

    /** Builds an {@link ObjectView} from property values keyed by IRI. */
    static ObjectView view(
            ObjectId id, String typeIri, long version, Optional<String> state, Map<String, Value> props) {
        Map<PropertyRef, Value> properties = new HashMap<>();
        props.forEach((iri, value) -> properties.put(new PropertyRef(iri), value));
        Audit audit = new Audit(
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                "automation",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                "automation");
        return new ObjectView(id, new TypeRef(typeIri), SCOPE.tenantId(), version, state, properties, audit, false);
    }
}
