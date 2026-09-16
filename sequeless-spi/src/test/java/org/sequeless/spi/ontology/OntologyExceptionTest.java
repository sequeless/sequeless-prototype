package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OntologyExceptionTest {

    @Test
    void messageEqualsReportDescribe() {
        OntologyIssue issue = new OntologyIssue(Severity.ERROR, Optional.of("ex:Cyborg"), "conflicting types");
        OntologyReport report = new OntologyReport(false, List.of(issue));

        OntologyException exception = new OntologyException(report);

        assertThat(exception.getMessage()).isEqualTo(report.describe());
        assertThat(exception.report()).isEqualTo(report);
    }

    @Test
    void rejectsNullReport() {
        assertThatNullPointerException().isThrownBy(() -> new OntologyException(null));
    }

    @Test
    void rejectsAConsistentReport() {
        OntologyReport consistent = new OntologyReport(true, List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyException(consistent));
    }
}
