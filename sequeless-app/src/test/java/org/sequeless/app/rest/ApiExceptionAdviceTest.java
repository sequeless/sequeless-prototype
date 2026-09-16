package org.sequeless.app.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit-tests {@link ApiExceptionAdvice}'s three exception-to-HTTP mappings directly against a
 * plain, hand-constructed {@link ApiExceptionAdvice} instance — no {@code @SpringBootTest}, no
 * {@code MockMvc}, and therefore no Jena on this test's path at all: the class under test has no
 * dependencies of its own to wire up, so calling its {@code @ExceptionHandler} methods exactly as
 * Spring's exception-resolution machinery would is enough to prove the mapping, without paying for
 * a servlet container or an ontology load. No mocking library is used anywhere in this codebase;
 * every exception here is a real instance built from real collaborator types.
 */
class ApiExceptionAdviceTest {

    private final ApiExceptionAdvice advice = new ApiExceptionAdvice();

    @Test
    void ontologyExceptionMapsTo422WithReport() {
        OntologyReport report =
                new OntologyReport(
                        false,
                        List.of(
                                new OntologyIssue(
                                        Severity.ERROR,
                                        Optional.of("https://sequeless.dev/ns/ref#Cyborg"),
                                        "ex:Cyborg is both a Machine and a Person, which are disjoint")));

        ResponseEntity<OntologyReportResponse> response =
                advice.handleOntologyException(new OntologyException(report));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().consistent()).isFalse();
        assertThat(response.getBody().issues()).hasSize(1);
        assertThat(response.getBody().issues().get(0).subjectIri())
                .isEqualTo("https://sequeless.dev/ns/ref#Cyborg");
    }

    @Test
    void typeNotFoundExceptionMapsTo404WithMessage() {
        ResponseEntity<ErrorResponse> response =
                advice.handleTypeNotFound(new TypeNotFoundException("NoSuchType"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("No type found for 'NoSuchType'"));
    }

    @Test
    void authorizationExceptionMapsTo403WithReason() {
        ResponseEntity<ErrorResponse> response =
                advice.handleAuthorizationException(
                        new AuthorizationException(AccessDecision.deny("no ADMIN role")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("no ADMIN role"));
    }
}
