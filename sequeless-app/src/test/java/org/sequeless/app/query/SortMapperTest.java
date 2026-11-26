package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.app.query.QueryFixtures.NS;
import static org.sequeless.app.query.QueryFixtures.TAGS_IRI;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.FieldScope;

class SortMapperTest {

    private static final FieldScope SCOPE = QueryFixtures.scope(
            TASK_IRI,
            Set.of(TASK_IRI),
            List.of(QueryFixtures.facet(TITLE_IRI, true), QueryFixtures.facet(TAGS_IRI, false)));

    @Test
    void aSortMapsToTheResolvedFacetPathAndItsDirection() {
        List<Sort> released = SortMapper.toReleased(
                List.of(new org.sequeless.spi.query.Sort(TITLE_IRI, org.sequeless.spi.query.Direction.DESC)), SCOPE);

        assertThat(released).containsExactly(new Sort(SCOPE.facets().get(0).path(), Direction.DESC));
    }

    @Test
    void aSortOnANonSortableFacetStillMapsSoTheReleasedServiceCanReject() {
        List<Sort> released = SortMapper.toReleased(
                List.of(new org.sequeless.spi.query.Sort(TAGS_IRI, org.sequeless.spi.query.Direction.ASC)), SCOPE);

        assertThat(released).containsExactly(new Sort(SCOPE.facets().get(1).path(), Direction.ASC));
        assertThat(SCOPE.facets().get(1).capabilities().sortable()).isFalse();
    }

    @Test
    void aSortOnAPropertyTheScopeLacksIsAnInvalidQuery() {
        String missing = NS + "missing";

        assertThatThrownBy(() -> SortMapper.toReleased(
                        List.of(new org.sequeless.spi.query.Sort(missing, org.sequeless.spi.query.Direction.ASC)),
                        SCOPE))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining(missing);
    }

    @Test
    void sortsKeepTheirOrder() {
        List<Sort> released = SortMapper.toReleased(
                List.of(
                        new org.sequeless.spi.query.Sort(TAGS_IRI, org.sequeless.spi.query.Direction.ASC),
                        new org.sequeless.spi.query.Sort(TITLE_IRI, org.sequeless.spi.query.Direction.DESC)),
                SCOPE);

        assertThat(released).extracting(Sort::direction).containsExactly(Direction.ASC, Direction.DESC);
        assertThat(released)
                .extracting(Sort::path)
                .containsExactly(
                        SCOPE.facets().get(1).path(), SCOPE.facets().get(0).path());
    }
}
