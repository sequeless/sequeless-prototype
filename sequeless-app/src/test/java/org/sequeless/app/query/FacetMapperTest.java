package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.app.query.QueryFixtures.TAGS_IRI;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;

class FacetMapperTest {

    private static final FieldScope SCOPE = QueryFixtures.scope(
            TASK_IRI,
            Set.of(TASK_IRI),
            List.of(QueryFixtures.facet(TITLE_IRI, true), QueryFixtures.facet(TAGS_IRI, false)));

    @Test
    void facetIrisMapToTheResolvedFacetPaths() {
        List<FieldPath> paths = FacetMapper.toReleased(List.of(TAGS_IRI, TITLE_IRI), SCOPE);

        assertThat(paths)
                .containsExactly(
                        SCOPE.facets().get(1).path(), SCOPE.facets().get(0).path());
    }

    @Test
    void aFacetOnAPropertyTheScopeLacksIsAnInvalidQuery() {
        assertThatThrownBy(() -> FacetMapper.toReleased(List.of(QueryFixtures.NS + "missing"), SCOPE))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void releasedBucketsComeBackKeyedByPropertyIri() {
        Map<FieldPath, List<FacetBucket>> released = new LinkedHashMap<>();
        released.put(SCOPE.facets().get(0).path(), List.of(new FacetBucket("OPEN", 2), new FacetBucket("DONE", 1)));

        Map<String, List<org.sequeless.spi.query.FacetBucket>> prototype = FacetMapper.toPrototype(released);

        assertThat(prototype).containsOnlyKeys(TITLE_IRI);
        assertThat(prototype.get(TITLE_IRI))
                .containsExactly(
                        new org.sequeless.spi.query.FacetBucket("OPEN", 2),
                        new org.sequeless.spi.query.FacetBucket("DONE", 1));
    }

    @Test
    void noFacetsIsAnEmptyMap() {
        assertThat(FacetMapper.toPrototype(Map.of())).isEmpty();
    }
}
