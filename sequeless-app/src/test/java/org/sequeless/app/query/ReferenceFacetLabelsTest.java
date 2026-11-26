package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.app.query.QueryFixtures.NS;
import static org.sequeless.app.query.QueryFixtures.SCOPE;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TENANT;
import static org.sequeless.app.query.QueryFixtures.VERSION;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.Capabilities;
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

class ReferenceFacetLabelsTest {

    private static final String PERSON_IRI = NS + "Person";
    private static final String NAME_IRI = NS + "name";
    private static final String ASSIGNED_TO_IRI = NS + "assignedTo";
    private static final String OWNER_IRI = NS + "owner";
    private static final String STATUS_IRI = NS + "status";
    private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");

    private static final ObjectId ADA = id("0c1d2e3f-0000-4000-8000-000000000001");
    private static final ObjectId BEA = id("0c1d2e3f-0000-4000-8000-000000000002");
    private static final ObjectId CY = id("0c1d2e3f-0000-4000-8000-000000000003");
    private static final ObjectId ABSENT = id("0c1d2e3f-0000-4000-8000-000000000004");
    private static final ObjectId NAMELESS = id("0c1d2e3f-0000-4000-8000-000000000005");

    private static final FieldPath ASSIGNED_TO =
            referenceFacet(ASSIGNED_TO_IRI, PERSON_IRI).path();
    private static final FieldPath OWNER = referenceFacet(OWNER_IRI, PERSON_IRI).path();
    private static final Field STATUS = QueryFixtures.facet(STATUS_IRI, false);

    @Test
    void sumsBucketsThatShareALabel() {
        CountingReads reads = new CountingReads(view(ADA, Optional.of("Ada")), view(BEA, Optional.of("Ada")));

        Map<FieldPath, List<FacetBucket>> result =
                labels(reads, labelledSchema(), SCOPE, facets(ASSIGNED_TO, bucket(ADA, 2), bucket(BEA, 3)));

        assertThat(result.get(ASSIGNED_TO)).containsExactly(new FacetBucket("Ada", 5L));
    }

    @Test
    void ordersByCountDescendingThenByLabel() {
        CountingReads reads = new CountingReads(
                view(ADA, Optional.of("Ada")), view(BEA, Optional.of("Bea")), view(CY, Optional.of("Cy")));

        Map<FieldPath, List<FacetBucket>> result = labels(
                reads, labelledSchema(), SCOPE, facets(ASSIGNED_TO, bucket(BEA, 2), bucket(CY, 5), bucket(ADA, 2)));

        assertThat(result.get(ASSIGNED_TO))
                .containsExactly(new FacetBucket("Cy", 5L), new FacetBucket("Ada", 2L), new FacetBucket("Bea", 2L));
    }

    @Test
    void dropsABucketWhoseTargetIsAbsent() {
        CountingReads reads = new CountingReads(view(ADA, Optional.of("Ada")));

        Map<FieldPath, List<FacetBucket>> result =
                labels(reads, labelledSchema(), SCOPE, facets(ASSIGNED_TO, bucket(ABSENT, 4), bucket(ADA, 1)));

        assertThat(result.get(ASSIGNED_TO)).containsExactly(new FacetBucket("Ada", 1L));
    }

    @Test
    void dropsABucketWhoseLabelIsNull() {
        CountingReads reads = new CountingReads(view(NAMELESS, Optional.empty()), view(ADA, Optional.of("Ada")));

        Map<FieldPath, List<FacetBucket>> result =
                labels(reads, labelledSchema(), SCOPE, facets(ASSIGNED_TO, bucket(NAMELESS, 2), bucket(ADA, 1)));

        assertThat(result.get(ASSIGNED_TO)).containsExactly(new FacetBucket("Ada", 1L));
    }

    @Test
    void fallsBackToTheIdTextWhenTheTargetDeclaresNoDisplayLabel() {
        CountingReads reads = new CountingReads(view(ADA, Optional.of("Ada")));

        Map<FieldPath, List<FacetBucket>> result =
                labels(reads, unlabelledSchema(), SCOPE, facets(ASSIGNED_TO, bucket(ADA, 3)));

        assertThat(result.get(ASSIGNED_TO))
                .containsExactly(new FacetBucket(ADA.value().toString(), 3L));
        assertThat(reads.reads).isEmpty();
    }

    @Test
    void readsEachDistinctTargetOnceAcrossTheFacets() {
        CountingReads reads = new CountingReads(view(ADA, Optional.of("Ada")), view(BEA, Optional.of("Bea")));

        labels(
                reads,
                labelledSchema(),
                SCOPE,
                Map.of(
                        ASSIGNED_TO, List.of(bucket(ADA, 1), bucket(BEA, 1)),
                        OWNER, List.of(bucket(ADA, 2), bucket(BEA, 1))));

        assertThat(reads.reads).containsExactlyInAnyOrder(ADA, BEA);
    }

    @Test
    void aNonReferenceFacetPassesThroughUnchanged() {
        FieldPath status = STATUS.path();
        CountingReads reads = new CountingReads();
        List<FacetBucket> buckets = List.of(new FacetBucket("OPEN", 2L));

        Map<FieldPath, List<FacetBucket>> result = labels(reads, labelledSchema(), SCOPE, Map.of(status, buckets));

        assertThat(result.get(status)).isEqualTo(buckets);
        assertThat(reads.reads).isEmpty();
    }

    // --- fixtures --------------------------------------------------------------------------------

    private static Map<FieldPath, List<FacetBucket>> labels(
            QueryService queryService, Schema schema, Scope scope, Map<FieldPath, List<FacetBucket>> facets) {
        FieldScope fieldScope = QueryFixtures.scope(
                TASK_IRI,
                Set.of(TASK_IRI),
                List.of(referenceFacet(ASSIGNED_TO_IRI, PERSON_IRI), referenceFacet(OWNER_IRI, PERSON_IRI), STATUS));
        return new ReferenceFacetLabels(queryService).apply(scope, schema, fieldScope, facets);
    }

    private static Map<FieldPath, List<FacetBucket>> facets(FieldPath path, FacetBucket... buckets) {
        return Map.of(path, List.of(buckets));
    }

    private static FacetBucket bucket(ObjectId target, long count) {
        return new FacetBucket(target.value().toString(), count);
    }

    private static Schema labelledSchema() {
        return schema(true);
    }

    private static Schema unlabelledSchema() {
        return schema(false);
    }

    private static Schema schema(boolean personHasDisplayLabel) {
        AttributeDefinition name = new AttributeDefinition(
                NAME_IRI,
                "Name",
                Cardinality.optional(),
                false,
                false,
                false,
                false,
                personHasDisplayLabel,
                DisplayHints.none(),
                Datatype.STRING);
        TypeDefinition person =
                new TypeDefinition(PERSON_IRI, "Person", List.of(), List.of(name), DisplayHints.none(), false);
        TypeDefinition task = new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(), DisplayHints.none(), false);
        return new Schema(VERSION, NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of(person, task));
    }

    private static ObjectView view(ObjectId id, Optional<String> name) {
        Map<PropertyRef, Value> properties =
                name.isPresent() ? Map.of(new PropertyRef(NAME_IRI), Value.text(name.get())) : Map.of();
        return new ObjectView(
                id,
                new TypeRef(PERSON_IRI),
                TENANT,
                1L,
                Optional.empty(),
                properties,
                new Audit(NOW, "alice", NOW, "alice"),
                false);
    }

    private static ObjectId id(String uuid) {
        return new ObjectId(UUID.fromString(uuid));
    }

    /** A reference facet whose path's one segment names {@code targetIri} as its target class. */
    private static Field referenceFacet(String facetIri, String targetIri) {
        String slug = facetIri.substring(facetIri.lastIndexOf('#') + 1);
        FieldPath path = new FieldPath(List.of(new PathSegment(
                UUID.nameUUIDFromBytes(facetIri.getBytes(StandardCharsets.UTF_8)),
                facetIri,
                slug,
                QueryFixtures.classRef(targetIri))));
        return new Field(
                path,
                FieldType.OBJECT_REF,
                null,
                org.sequeless.query.field.api.Cardinality.SINGLE,
                new Capabilities(true, true, false, false),
                new LocalizedText(slug, "und"),
                new LocalizedText("", "und"),
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.MAY_BE_INFERRED,
                Set.of(new OntologyVersionRef(1)),
                Optional.empty());
    }

    /** Counts the released reads, and answers each id from a fixed set of views. */
    private static final class CountingReads implements QueryService {

        private final Map<ObjectId, ObjectView> views;
        final List<ObjectId> reads = new ArrayList<>();

        CountingReads(ObjectView... views) {
            this.views = new java.util.HashMap<>();
            for (ObjectView view : views) {
                this.views.put(view.id(), view);
            }
        }

        @Override
        public Optional<ObjectView> read(Scope scope, ReadRequest request) {
            reads.add(request.id());
            return Optional.ofNullable(views.get(request.id()));
        }

        @Override
        public BrowseResult browse(FieldScopeRequest request, BrowseQuery query) {
            throw new UnsupportedOperationException("not used by the labels");
        }

        @Override
        public IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version) {
            throw new UnsupportedOperationException("not used by the labels");
        }
    }
}
