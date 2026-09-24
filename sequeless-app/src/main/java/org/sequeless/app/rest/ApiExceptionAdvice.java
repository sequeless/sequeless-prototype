package org.sequeless.app.rest;

import java.net.URI;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.InvalidQueryException;
import org.sequeless.core.api.TransitionNotAvailableException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.api.UnknownSignalException;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.validation.Violation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
 *
 * <p>The seven handlers below are {@link ObjectsController}'s, added alongside the three above
 * without touching them — {@link TypesController}'s error shapes ({@link ErrorResponse}, the raw
 * {@link OntologyReportResponse}) stay exactly as they were. {@link InvalidQueryException}'s
 * handler was added for {@link ObjectsController#browse}'s Phase 3 filter/sort/facet query
 * parameters; {@link TransitionNotAvailableException}'s is the newest of the seven, added for
 * {@link ObjectsController#fireTransition}'s Phase 5 state-machine endpoint. All seven render
 * {@link ProblemDetail} ({@code application/problem+json}), per plan.md §8's error table:
 *
 * <ul>
 *   <li>{@link ValidationException} → 400, {@code type} suffix {@code validation}, plus {@code
 *       source} and a {@code violations} array (see {@link #toViolationBody})
 *   <li>{@link ObjectNotFoundException} → 404, {@code type} suffix {@code object-not-found}
 *   <li>{@link StaleObjectException} → 409, {@code type} suffix {@code stale-object}, plus {@code
 *       expectedVersion}
 *   <li>{@link PreconditionRequiredException} → 428, {@code type} suffix {@code
 *       precondition-required}
 *   <li>{@link PreconditionMismatchException} → 400, {@code type} suffix {@code bad-request}
 *   <li>{@link InvalidQueryException} → 400, {@code type} suffix {@code invalid-query}, plus a
 *       {@code violations} array (see {@link #toViolationBody})
 *   <li>{@link TransitionNotAvailableException} → 409, {@code type} suffix {@code
 *       transition-not-available}, plus {@code transitionName}
 *   <li>{@link UnknownSignalException} → 400, {@code type} suffix {@code unknown-signal}, plus
 *       {@code typeIri} and {@code signalName}, added for {@link
 *       ObjectsController#signal}'s phase 6 {@code POST .../signals/{name}} endpoint, mirroring
 *       {@link TransitionNotAvailableException}'s handler exactly
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

    /**
     * @param exception the validation failure escaping from {@link
     *     org.sequeless.core.api.BusinessObjectService#add}/{@link
     *     org.sequeless.core.api.BusinessObjectService#edit}; must not be {@code null}
     * @return 400 with a {@link ProblemDetail} carrying {@code source} ({@code "structural"} or
     *     {@code "shacl"}) and a {@code violations} array
     */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/validation"));
        detail.setTitle("Validation failed");
        detail.setProperty(
                "source",
                exception.source() == ValidationException.Source.STRUCTURAL ? "structural" : "shacl");
        detail.setProperty(
                "violations", exception.violations().stream().map(ApiExceptionAdvice::toViolationBody).toList());
        return detail;
    }

    /**
     * @param exception the invalid filter/sort/facet property or operator escaping from {@link
     *     org.sequeless.core.api.BusinessObjectService#browse}; must not be {@code null}
     * @return 400 with a {@link ProblemDetail} carrying a {@code violations} array
     */
    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail handleInvalidQuery(InvalidQueryException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/invalid-query"));
        detail.setTitle("Invalid query");
        detail.setProperty(
                "violations", exception.violations().stream().map(ApiExceptionAdvice::toViolationBody).toList());
        return detail;
    }

    /**
     * @param exception the missing/deleted/mismatched-type object escaping from {@link
     *     org.sequeless.core.api.BusinessObjectService}; must not be {@code null}
     * @return 404 with a {@link ProblemDetail} carrying the exception's own pre-formatted message
     */
    @ExceptionHandler(ObjectNotFoundException.class)
    public ProblemDetail handleObjectNotFound(ObjectNotFoundException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/object-not-found"));
        detail.setTitle("Object not found");
        return detail;
    }

    /**
     * @param exception the optimistic-locking failure escaping from {@link
     *     org.sequeless.core.api.BusinessObjectService#edit}/{@link
     *     org.sequeless.core.api.BusinessObjectService#delete}; must not be {@code null}
     * @return 409 with a {@link ProblemDetail} carrying {@link StaleObjectException#expectedVersion()}
     */
    @ExceptionHandler(StaleObjectException.class)
    public ProblemDetail handleStaleObject(StaleObjectException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/stale-object"));
        detail.setTitle("Stale object version");
        detail.setProperty("expectedVersion", exception.expectedVersion());
        return detail;
    }

    /**
     * @param exception the missing-precondition failure {@link ObjectsController#edit} throws;
     *     must not be {@code null}
     * @return 428 (Precondition Required) with a {@link ProblemDetail}
     */
    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail handlePreconditionRequired(PreconditionRequiredException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.PRECONDITION_REQUIRED, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/precondition-required"));
        detail.setTitle("Precondition required");
        return detail;
    }

    /**
     * @param exception the disagreeing-versions failure {@link ObjectsController#edit} throws;
     *     must not be {@code null}
     * @return 400 (Bad Request) with a {@link ProblemDetail}
     */
    @ExceptionHandler(PreconditionMismatchException.class)
    public ProblemDetail handlePreconditionMismatch(PreconditionMismatchException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/bad-request"));
        detail.setTitle("Conflicting version");
        return detail;
    }

    /**
     * @param exception the unavailable-transition failure escaping from {@link
     *     org.sequeless.core.api.TransitionService#fire}; must not be {@code null}
     * @return 409 with a {@link ProblemDetail} carrying {@link
     *     TransitionNotAvailableException#transitionName()}, detail text taken from {@link
     *     TransitionNotAvailableException#reason()} (not {@code getMessage()}, even though the two
     *     are identical today — {@code reason()} is the self-documenting accessor for this specific
     *     purpose)
     */
    @ExceptionHandler(TransitionNotAvailableException.class)
    public ProblemDetail handleTransitionNotAvailable(TransitionNotAvailableException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.reason());
        detail.setType(URI.create("https://sequeless.dev/problems/transition-not-available"));
        detail.setTitle("Transition not available");
        detail.setProperty("transitionName", exception.transitionName());
        return detail;
    }

    /**
     * @param exception the unrecognised signal name escaping from {@link
     *     org.sequeless.core.api.TransitionService#signal}; must not be {@code null}
     * @return 400 with a {@link ProblemDetail} carrying {@link UnknownSignalException#typeIri()} and
     *     {@link UnknownSignalException#signalName()}
     */
    @ExceptionHandler(UnknownSignalException.class)
    public ProblemDetail handleUnknownSignal(UnknownSignalException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setType(URI.create("https://sequeless.dev/problems/unknown-signal"));
        detail.setTitle("Unknown signal");
        detail.setProperty("typeIri", exception.typeIri());
        detail.setProperty("signalName", exception.signalName());
        return detail;
    }

    /**
     * @param violation the violation to render; must not be {@code null}
     * @return a {@link ViolationResponse} with {@code property}/{@code propertyIri} both {@code
     *     null} when {@link Violation#path()} is blank (object-level, per that record's javadoc),
     *     or resolved from the property IRI's own local name otherwise
     */
    private static ViolationResponse toViolationBody(Violation violation) {
        if (violation.path().isBlank()) {
            return new ViolationResponse(null, null, violation.message());
        }
        return new ViolationResponse(shortName(violation.path()), violation.path(), violation.message());
    }

    /**
     * Duplicates {@code MetaModelSnapshot}'s own short-name rule, for the same reason {@link
     * TypeResponseMapper#shortName} and {@link ObjectPropertyMapper#shortName} do.
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
