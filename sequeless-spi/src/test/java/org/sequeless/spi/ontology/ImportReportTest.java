package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImportReportTest {

    private static final OntologyReport CONSISTENT = new OntologyReport(true, List.of());

    @Test
    void carriesAcceptedReportAndTypeCount() {
        ImportReport report = new ImportReport(true, CONSISTENT, 3);

        assertThat(report.accepted()).isTrue();
        assertThat(report.report()).isEqualTo(CONSISTENT);
        assertThat(report.typeCount()).isEqualTo(3);
    }

    @Test
    void allowsZeroTypeCount() {
        ImportReport report = new ImportReport(true, CONSISTENT, 0);

        assertThat(report.typeCount()).isZero();
    }

    @Test
    void rejectsNegativeTypeCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImportReport(true, CONSISTENT, -1));
    }

    @Test
    void rejectsNullReport() {
        assertThatNullPointerException().isThrownBy(() -> new ImportReport(true, null, 0));
    }
}
