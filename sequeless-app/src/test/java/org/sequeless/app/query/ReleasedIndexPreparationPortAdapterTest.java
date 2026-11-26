package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.app.query.QueryFixtures.SCHEMA;
import static org.sequeless.app.query.QueryFixtures.SCOPE;
import static org.sequeless.app.query.QueryFixtures.TENANT;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;
import static org.sequeless.app.query.QueryFixtures.VERSION;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.OntologyVersionRef;

class ReleasedIndexPreparationPortAdapterTest {

    @Test
    void anEmptyResultMeansNothingWasNewlyRegistered() {
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        queryService.prepareResult = IndexPreparationResult.EMPTY;

        boolean newlyRegistered = new ReleasedIndexPreparationPortAdapter(queryService).prepare(SCOPE, VERSION, SCHEMA);

        assertThat(newlyRegistered).isFalse();
    }

    @Test
    void aNewlyRegisteredIndexMakesTheBackfillRun() {
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        queryService.prepareResult = new IndexPreparationResult(
                List.of(QueryFixtures.facet(TITLE_IRI, true).path()));

        boolean newlyRegistered = new ReleasedIndexPreparationPortAdapter(queryService).prepare(SCOPE, VERSION, SCHEMA);

        assertThat(newlyRegistered).isTrue();
    }

    @Test
    void thePreparationIsForTheScopeTenantAndTheActivatedVersion() {
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();

        new ReleasedIndexPreparationPortAdapter(queryService).prepare(SCOPE, VERSION, SCHEMA);

        assertThat(queryService.prepareTenants).containsExactly(TENANT);
        assertThat(queryService.prepareVersions).containsExactly(new OntologyVersionRef(1));
    }
}
