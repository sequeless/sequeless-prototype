package org.sequeless.app.query;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.Sort;

/** Shared fixtures for the query adapter tests: a tenant, a schema, hand-built released scopes and fakes. */
final class QueryFixtures {

    static final String NS = "https://sequeless.dev/ns/query#";
    static final String WORK_ITEM_IRI = NS + "WorkItem";
    static final String TASK_IRI = NS + "Task";
    static final String PROJECT_IRI = NS + "Project";
    static final String TITLE_IRI = NS + "title";
    static final String TAGS_IRI = NS + "tags";

    static final TenantId TENANT = new TenantId("acme");
    static final Scope SCOPE = new Scope(TENANT, new Principal("alice", Set.of("member")));
    static final OntologyVersion VERSION = new OntologyVersion(1);
    static final Schema SCHEMA =
            new Schema(VERSION, NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of());

    private QueryFixtures() {}

    static ClassRef classRef(String iri) {
        String slug = iri.substring(iri.lastIndexOf('#') + 1);
        return new ClassRef(
                UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)),
                slug,
                iri,
                new LocalizedText(slug, "und"));
    }

    /** A single-segment (direct) field, as the released schema adapter builds one for a root property. */
    static Field facet(String iri, boolean sortable) {
        String slug = iri.substring(iri.lastIndexOf('#') + 1);
        FieldPath path = new FieldPath(List.of(
                new PathSegment(UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)), iri, slug, null)));
        return new Field(
                path,
                FieldType.STRING,
                null,
                sortable ? Cardinality.SINGLE : Cardinality.MANY,
                new Capabilities(true, true, false, sortable),
                new LocalizedText(slug, "und"),
                new LocalizedText("", "und"),
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.MAY_BE_INFERRED,
                Set.of(new OntologyVersionRef(1)),
                Optional.empty());
    }

    /** A released scope for a root, with its concrete types and facets. */
    static FieldScope scope(String rootIri, Set<String> concreteIris, List<Field> facets) {
        ClassRef root = classRef(rootIri);
        Set<ClassRef> concrete = new java.util.LinkedHashSet<>();
        concreteIris.forEach(iri -> concrete.add(classRef(iri)));
        FieldScopeRequest request = new FieldScopeRequest(
                TENANT,
                root,
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
        return new FieldScope(root, facets, List.of(), "stamp", Set.of(new OntologyVersionRef(1)), concrete, request);
    }

    static Criterion criterion(Operator operator, Value value) {
        return new Criterion(TITLE_IRI, operator, Optional.ofNullable(value));
    }

    static Query query(
            String rootIri, Set<String> types, List<Criterion> criteria, List<Sort> sorts, List<String> facets) {
        return new Query(types, criteria, Optional.empty(), sorts, new Page(0, 20), facets, false, rootIri);
    }

    /** Records every call and returns canned results. */
    static final class FakeQueryService implements QueryService {

        final List<FieldScopeRequest> requests = new ArrayList<>();
        final List<BrowseQuery> browses = new ArrayList<>();
        final List<TenantId> prepareTenants = new ArrayList<>();
        final List<OntologyVersionRef> prepareVersions = new ArrayList<>();
        final List<ObjectId> reads = new ArrayList<>();
        BrowseResult browseResult = new BrowseResult(List.of(), 0, Map.of());
        Optional<org.sequeless.query.api.ObjectView> readResult = Optional.empty();
        IndexPreparationResult prepareResult = IndexPreparationResult.EMPTY;

        @Override
        public BrowseResult browse(FieldScopeRequest request, BrowseQuery query) {
            requests.add(request);
            browses.add(query);
            return browseResult;
        }

        @Override
        public Optional<org.sequeless.query.api.ObjectView> read(
                Scope scope, org.sequeless.query.api.ReadRequest request) {
            reads.add(request.id());
            return readResult;
        }

        @Override
        public IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version) {
            prepareTenants.add(tenant);
            prepareVersions.add(version);
            return prepareResult;
        }
    }

    /** Returns one prepared scope for any request, and records the requests. */
    static final class FakeFieldSource implements FieldSource {

        private final FieldScope scope;
        final List<FieldScopeRequest> requests = new ArrayList<>();

        FakeFieldSource(FieldScope scope) {
            this.scope = scope;
        }

        @Override
        public FieldScope resolve(FieldScopeRequest request) {
            requests.add(request);
            return scope;
        }

        @Override
        public FieldScope expand(FieldScope scope, FieldPath relationship) {
            throw new UnsupportedOperationException("not used by the query adapters");
        }

        @Override
        public Field describe(FieldScopeRequest request, FieldPath path) {
            throw new UnsupportedOperationException("not used by the query adapters");
        }

        @Override
        public List<ClassRef> rootClasses(TenantId tenant) {
            throw new UnsupportedOperationException("not used by the query adapters");
        }
    }
}
