package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OntologyReportTest {

    @Test
    void consistentWithNoIssuesDescribesItself() {
        OntologyReport report = new OntologyReport(true, List.of());

        assertThat(report.describe()).isEqualTo("Ontology is consistent.");
    }

    @Test
    void inconsistentWithNoIssuesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyReport(false, List.of()));
    }

    @Test
    void inconsistentRequiresAtLeastOneErrorIssue() {
        List<OntologyIssue> onlyWarnings =
            List.of(new OntologyIssue(Severity.WARNING, Optional.empty(), "just a warning"));

        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyReport(false, onlyWarnings));
    }

    @Test
    void inconsistentWithAnErrorIssueDescribesItselfWithSubject() {
        OntologyIssue issue = new OntologyIssue(Severity.ERROR, Optional.of("ex:Cyborg"), "conflicting types");
        OntologyReport report = new OntologyReport(false, List.of(issue));

        assertThat(report.describe())
            .isEqualTo("Ontology is inconsistent (1 issue(s)):\n  [ERROR] ex:Cyborg: conflicting types");
    }

    @Test
    void describeOmitsSubjectPrefixWhenAbsentAndPreservesIssueOrder() {
        OntologyIssue first = new OntologyIssue(Severity.ERROR, Optional.of("ex:Cyborg"), "conflicting types");
        OntologyIssue second = new OntologyIssue(Severity.WARNING, Optional.empty(), "malformed document");
        OntologyReport report = new OntologyReport(false, List.of(first, second));

        assertThat(report.describe())
            .isEqualTo(
                "Ontology is inconsistent (2 issue(s)):\n"
                    + "  [ERROR] ex:Cyborg: conflicting types\n"
                    + "  [WARNING] malformed document");
    }

    @Test
    void consistentWithWarningsDescribesItself() {
        OntologyIssue warning = new OntologyIssue(Severity.WARNING, Optional.empty(), "unrecognised datatype");
        OntologyReport report = new OntologyReport(true, List.of(warning));

        assertThat(report.describe()).isEqualTo("Ontology is consistent (1 issue(s)):\n  [WARNING] unrecognised datatype");
    }

    @Test
    void rejectsNullIssues() {
        assertThatNullPointerException().isThrownBy(() -> new OntologyReport(true, null));
    }

    @Test
    void issuesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerList() {
        List<OntologyIssue> callerIssues = new ArrayList<>();
        callerIssues.add(new OntologyIssue(Severity.WARNING, Optional.empty(), "note"));

        OntologyReport report = new OntologyReport(true, callerIssues);
        callerIssues.add(new OntologyIssue(Severity.WARNING, Optional.empty(), "added later"));

        assertThat(report.issues()).hasSize(1);
        assertThatThrownBy(() -> report.issues().add(new OntologyIssue(Severity.WARNING, Optional.empty(), "x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
