package org.sequeless.app.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Objects;
import org.sequeless.core.api.OntologyAdministration;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@link OntologyAdministration} over HTTP: {@code POST /ontology} (Turtle import) and
 * {@code GET /ontology} (Turtle export), per plan.md §8.
 *
 * <p>Both endpoints trade in raw Turtle text, not JSON, on the request/response body itself —
 * {@code consumes}/{@code produces} of {@code text/turtle} — which is why neither method takes or
 * returns a record type for the body; the plain {@link String} content is exactly what {@link
 * OntologyAdministration#importTurtle} accepts and {@link OntologyDocument#content} returns. Only
 * the import outcome is rendered as JSON ({@link OntologyImportResponse}).
 *
 * <p>An inconsistent import is not handled here: {@link OntologyAdministration#importTurtle} throws
 * {@code OntologyException} in that case, and the existing {@link
 * ApiExceptionAdvice#handleOntologyException} — introduced for {@code POST /types/reload}, untouched
 * by this controller — already renders that as HTTP 422. This controller's {@code importOntology}
 * method is only ever reached on the accepted path.
 *
 * <p>Scopes every call to {@link TenantId#DEFAULT} and {@link Principal#ANONYMOUS}, the same
 * thin-shim idiom {@link TypesController} and {@link ObjectsController} follow: no inbound identity
 * provider is wired up yet.
 */
@RestController
public class OntologyController {

    private final OntologyAdministration ontologyAdministration;

    /**
     * @param ontologyAdministration the use case this controller delegates to; must not be {@code
     *     null}
     * @throws NullPointerException if {@code ontologyAdministration} is {@code null}
     */
    public OntologyController(OntologyAdministration ontologyAdministration) {
        this.ontologyAdministration =
                Objects.requireNonNull(ontologyAdministration, "ontologyAdministration must not be null");
    }

    /**
     * @param turtle the replacement ontology, as raw Turtle text
     * @return the accepted import's outcome, per {@link OntologyImportResponse}
     */
    @Operation(summary = "Replace the ontology with a new Turtle document")
    @ApiResponse(responseCode = "200", description = "The import was accepted")
    @ApiResponse(
            responseCode = "422",
            description = "The resulting ontology is inconsistent",
            content = @Content(schema = @Schema(implementation = OntologyReportResponse.class)))
    @PostMapping(path = "/ontology", consumes = "text/turtle")
    public OntologyImportResponse importOntology(@RequestBody String turtle) {
        ImportReport report = ontologyAdministration.importTurtle(currentScope(), turtle);
        return OntologyImportResponse.from(report);
    }

    /**
     * @return the currently loaded ontology, serialised as Turtle
     */
    @Operation(summary = "Export the current ontology as Turtle")
    @ApiResponse(
            responseCode = "200",
            description = "The current ontology",
            content = @Content(mediaType = "text/turtle", schema = @Schema(implementation = String.class)))
    @GetMapping(path = "/ontology", produces = "text/turtle")
    public String exportOntology() {
        return ontologyAdministration.export(currentScope()).content();
    }

    private static Scope currentScope() {
        return new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    }
}
