package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class OntologyIssueTest {

    @Test
    void constructsWithASubject() {
        OntologyIssue issue = new OntologyIssue(Severity.ERROR, Optional.of("ex:Cyborg"), "conflicting types");

        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.subjectIri()).contains("ex:Cyborg");
        assertThat(issue.message()).isEqualTo("conflicting types");
    }

    @Test
    void constructsWithoutASubject() {
        OntologyIssue issue = new OntologyIssue(Severity.WARNING, Optional.empty(), "malformed document");

        assertThat(issue.subjectIri()).isEmpty();
    }

    @Test
    void rejectsNullSeverity() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OntologyIssue(null, Optional.empty(), "message"));
    }

    @Test
    void rejectsNullSubjectIriWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OntologyIssue(Severity.ERROR, null, "message"));
    }

    @Test
    void rejectsNullOrBlankMessage() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OntologyIssue(Severity.ERROR, Optional.empty(), null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OntologyIssue(Severity.ERROR, Optional.empty(), "  "));
    }
}
