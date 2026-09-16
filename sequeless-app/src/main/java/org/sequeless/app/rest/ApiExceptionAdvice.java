package org.sequeless.app.rest;

import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.spi.ontology.OntologyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The application's first {@code @RestControllerAdvice} — before this class, no global exception
 * handler existed at all, so a denial on {@code GET /whoami} fell through to a generic HTTP 500.
 * Because a controller advice is global, {@code /whoami} gains correct {@link
 * AuthorizationException} handling as a side effect of introducing this class for {@link
 * TypesController}; that is an intentional behaviour change, not scope creep.
 *
 * <p>Maps exactly three exception types, each escaping from {@link
 * org.sequeless.core.api.MetaModelService} (or, for {@link AuthorizationException}, from any use
 * case):
 *
 * <ul>
 *   <li>{@link OntologyException} → 422, body {@link OntologyReportResponse} built from {@link
 *       OntologyException#report()}, the same shape {@code POST /types/reload} renders on success
 *   <li>{@link TypeNotFoundException} → 404, body {@link ErrorResponse} carrying the exception's
 *       own pre-formatted message
 *   <li>{@link AuthorizationException} → 403, body {@link ErrorResponse} carrying {@link
 *       AuthorizationException#decision()}'s {@code reason()}
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionAdvice {

    /**
     * @param exception the inconsistency escaping from the ontology port; must not be {@code null}
     * @return 422 with the exception's {@link OntologyException#report()} rendered as {@link
     *     OntologyReportResponse}
     */
    @ExceptionHandler(OntologyException.class)
    public ResponseEntity<OntologyReportResponse> handleOntologyException(OntologyException exception) {
        return ResponseEntity.unprocessableEntity().body(OntologyReportResponse.from(exception.report()));
    }

    /**
     * @param exception the resolution failure; must not be {@code null}
     * @return 404 with {@link ErrorResponse} carrying {@link TypeNotFoundException#getMessage()}
     */
    @ExceptionHandler(TypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTypeNotFound(TypeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    /**
     * @param exception the denial escaping from a use case; must not be {@code null}
     * @return 403 with {@link ErrorResponse} carrying {@link AuthorizationException#decision()}'s
     *     reason
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(exception.decision().reason()));
    }
}
