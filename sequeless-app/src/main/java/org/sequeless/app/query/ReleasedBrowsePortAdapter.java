package org.sequeless.app.query;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;

/**
 * The prototype's {@link BrowsePort} over the released {@link QueryService}. A browse names its root class by
 * {@link Query#rootIri()}, which the core sets to the type the caller asked for. The released service resolves the
 * root's fields and concrete types from that IRI.
 *
 * <p>The released scope is resolved here as well, because sorts and facets are mapped to the released paths of its
 * fields. The released service resolves it again inside {@link QueryService#browse}; the cost is one extra schema
 * read per browse.
 *
 * <p>This adapter is reached only through the core's browse ({@code BusinessObjectService#browse}), which has already
 * run its pre-checks: a filter, sort or facet on a multi-valued property, or on a derived property that is not
 * materialised, is refused before this port is called. A caller that skips the core must not rely on what the
 * released scope allows. The released filter does not gate on cardinality, and it accepts a derived property that
 * the core would refuse, so a browse that bypasses the core can return results the core would not.
 */
public final class ReleasedBrowsePortAdapter implements BrowsePort {

    private static final String UNDETERMINED_LANGUAGE = "und";

    private final QueryService queryService;
    private final FieldSource fieldSource;
    private final ReferenceFacetLabels referenceFacetLabels;

    /**
     * @param queryService the released query service; must not be {@code null}
     * @param fieldSource the released field source, used to map sorts and facets to paths; must not be {@code null}
     */
    public ReleasedBrowsePortAdapter(QueryService queryService, FieldSource fieldSource) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.fieldSource = Objects.requireNonNull(fieldSource, "fieldSource must not be null");
        this.referenceFacetLabels = new ReferenceFacetLabels(queryService);
    }

    @Override
    public QueryResult browse(Scope scope, Schema schema, Query query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (query.includeDeleted()) {
            throw new UnsupportedOperationException(
                    "the released browse never returns soft-deleted objects, so includeDeleted=true has no released"
                            + " equivalent");
        }

        FieldScopeRequest request = new FieldScopeRequest(
                scope.tenantId(),
                rootClassRef(query.rootIri(), schema),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
        FieldScope fieldScope = fieldSource.resolve(request);
        requireSameConcreteTypes(query, fieldScope);

        BrowseQuery browseQuery = new BrowseQuery(
                CriterionMapper.toFilter(query.criteria()),
                query.text(),
                SortMapper.toReleased(query.sorts(), fieldScope),
                new Page(query.page().number(), query.page().size()),
                FacetMapper.toReleased(query.facetProperties(), fieldScope));
        BrowseResult result = queryService.browse(request, browseQuery);
        return new QueryResult(
                result.items().stream().map(ObjectViewMapper::toPrototype).toList(),
                result.total(),
                FacetMapper.toPrototype(referenceFacetLabels.apply(scope, schema, fieldScope, result.facets())));
    }

    /**
     * Builds the released {@link ClassRef} of a root type from its IRI. The released schema adapter builds the same
     * value from the schema's type definition, in a package-private helper this module cannot call, so it is derived
     * here by the same rules: the id is the name-based UUID of the IRI, the slug is the IRI's local name (after the
     * last {@code #}, {@code /} or {@code :}), and the label is the type's label from the schema in the language
     * {@code und}. The released root lookup reads only the IRI, so these fields matter for equality, not for
     * resolution.
     *
     * @param iri the root type's IRI; must not be {@code null} or blank
     * @param schema the prototype schema the browse runs against; must not be {@code null}
     * @return the released class reference for that IRI
     */
    static ClassRef rootClassRef(String iri, Schema schema) {
        String slug = localName(iri);
        String label = schema.type(iri).map(TypeDefinition::label).orElse(slug);
        return new ClassRef(
                UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)),
                slug,
                iri,
                new LocalizedText(label, UNDETERMINED_LANGUAGE));
    }

    private static String localName(String iri) {
        int cut = Math.max(iri.lastIndexOf('#'), Math.max(iri.lastIndexOf('/'), iri.lastIndexOf(':')));
        String local = iri.substring(cut + 1);
        return local.isEmpty() ? iri : local;
    }

    /**
     * The core's concrete-type expansion and the released schema's must agree, or the browse would search different
     * types from the ones the core asked for. A mismatch is a defect, so it fails loudly.
     */
    private static void requireSameConcreteTypes(Query query, FieldScope fieldScope) {
        Set<String> released =
                fieldScope.concreteTypes().stream().map(ClassRef::iri).collect(Collectors.toUnmodifiableSet());
        if (!released.equals(query.types())) {
            throw new IllegalStateException("concrete types of root " + query.rootIri()
                    + " differ: the released schema has " + released + " but the core asked for " + query.types());
        }
    }
}
