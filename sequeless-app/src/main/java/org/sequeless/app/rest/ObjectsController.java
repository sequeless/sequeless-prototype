package org.sequeless.app.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@link BusinessObjectService} over HTTP: browse/read/add/edit/delete against {@code
 * /objects/{type}} and {@code /objects/{type}/{id}}, per plan.md §8.
 *
 * <p>{@code type} accepts a short name or a full IRI, the same as {@link TypesController} — passed
 * straight through to {@link BusinessObjectService}, which does all resolution, the same
 * thin-shim idiom {@link TypesController} and {@link WhoAmIController} establish. Property keys in
 * a request body may likewise be a short name or a full IRI ({@code ValueCoercer}'s own contract),
 * so this controller does no key translation on the way in — only the response mapper ({@link
 * ObjectPropertyMapper}) translates a stored object's full-IRI-keyed properties back to short
 * names, using the current request's resolved {@link TypeDefinition} (fetched once per request via
 * {@link MetaModelService#describeType}).
 *
 * <p><b>{@code PUT} version resolution</b> (plan.md §8, implemented exactly): {@link
 * BusinessObjectService#edit} requires a single, non-optional {@code long expectedVersion}, so this
 * controller must resolve one from two possible sources — {@link UpdateObjectRequest#version()}
 * and the {@code If-Match} header — before it can call in:
 *
 * <ul>
 *   <li>neither present → {@link PreconditionRequiredException} → HTTP 428
 *   <li>both present but disagreeing → {@link PreconditionMismatchException} → HTTP 400
 *   <li>otherwise → whichever one is present
 * </ul>
 *
 * <p>An {@code If-Match} header value arrives quoted per HTTP ETag syntax (e.g. {@code "1"});
 * {@link #stripQuotes} removes the surrounding quotes before parsing.
 *
 * <p>Every method scopes its call to {@link TenantId#DEFAULT} and {@link Principal#ANONYMOUS}, for
 * the same reason {@link TypesController} does: no inbound identity provider is wired up yet.
 */
@RestController
public class ObjectsController {

    private final BusinessObjectService businessObjectService;
    private final MetaModelService metaModelService;

    /**
     * @param businessObjectService the use case this controller delegates every BREAD operation
     *     to; must not be {@code null}
     * @param metaModelService the use case consulted for the current request's resolved {@link
     *     TypeDefinition}, used only to render the response's property names; must not be {@code
     *     null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ObjectsController(
            BusinessObjectService businessObjectService, MetaModelService metaModelService) {
        this.businessObjectService =
                Objects.requireNonNull(businessObjectService, "businessObjectService must not be null");
        this.metaModelService =
                Objects.requireNonNull(metaModelService, "metaModelService must not be null");
    }

    /**
     * @param type the type's short name or full IRI
     * @param page the 0-based page number; defaults to 0
     * @param size the requested page size; defaults to 20; capped at 200 (never rejected outright)
     * @return the matching page, rendered as {@link PageResultResponse}
     */
    @Operation(summary = "List objects of a type, one page at a time")
    @ApiResponse(responseCode = "200", description = "The requested page")
    @ApiResponse(
            responseCode = "404",
            description = "No such type",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/objects/{type}")
    public PageResultResponse browse(
            @PathVariable("type") String type,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Scope scope = currentScope();
        int effectiveSize = Math.min(size, 200);
        PageResult<BusinessObject> result =
                businessObjectService.browse(scope, type, new Page(page, effectiveSize));
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        List<BusinessObjectResponse> items =
                result.items().stream()
                        .map(object -> ObjectPropertyMapper.toResponse(object, requestType))
                        .toList();
        return new PageResultResponse(
                items, result.number(), result.size(), result.totalItems(), result.totalPages());
    }

    /**
     * @param type the type's short name or full IRI
     * @param id the object's id
     * @return 200 with the object, plus an {@code ETag} carrying its version
     */
    @Operation(summary = "Read a single object by id")
    @ApiResponse(responseCode = "200", description = "The matching object")
    @ApiResponse(
            responseCode = "404",
            description = "No such type, or no such object",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/objects/{type}/{id}")
    public ResponseEntity<BusinessObjectResponse> read(
            @PathVariable("type") String type, @PathVariable("id") UUID id) {
        Scope scope = currentScope();
        BusinessObject found = businessObjectService.read(scope, type, new ObjectId(id));
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        return ResponseEntity.ok()
                .eTag(Long.toString(found.version()))
                .body(ObjectPropertyMapper.toResponse(found, requestType));
    }

    /**
     * @param type the type's short name or full IRI
     * @param request the properties to create the object with
     * @return 201 with {@code Location}, an {@code ETag} carrying the new version (always 1), and
     *     the created object
     */
    @Operation(summary = "Create a new object of a type")
    @ApiResponse(responseCode = "201", description = "The created object")
    @ApiResponse(
            responseCode = "400",
            description = "A structural or SHACL validation failure",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such type",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/objects/{type}")
    public ResponseEntity<BusinessObjectResponse> add(
            @PathVariable("type") String type, @RequestBody CreateObjectRequest request) {
        Scope scope = currentScope();
        BusinessObject created = businessObjectService.add(scope, type, request.properties());
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        URI location = URI.create("/objects/" + type + "/" + created.id().value());
        return ResponseEntity.created(location)
                .eTag(Long.toString(created.version()))
                .body(ObjectPropertyMapper.toResponse(created, requestType));
    }

    /**
     * @param type the type's short name or full IRI
     * @param id the id of the object to edit
     * @param request the version (nullable) and the properties that fully replace the object's
     *     current ones
     * @param ifMatch the {@code If-Match} header, or {@code null} if absent
     * @return 200 with the updated object, plus an {@code ETag} carrying its new version
     */
    @Operation(summary = "Fully replace an existing object's properties")
    @ApiResponse(responseCode = "200", description = "The updated object")
    @ApiResponse(
            responseCode = "400",
            description = "A structural/SHACL validation failure, or a body/If-Match version mismatch",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such type, or no such object",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The stored object is not at the expected version",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description = "Neither a body version nor an If-Match header was supplied",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/objects/{type}/{id}")
    public ResponseEntity<BusinessObjectResponse> edit(
            @PathVariable("type") String type,
            @PathVariable("id") UUID id,
            @RequestBody UpdateObjectRequest request,
            @Parameter(description = "The version the caller expects the object to currently be at")
                    @RequestHeader(value = "If-Match", required = false)
                    String ifMatch) {
        Scope scope = currentScope();
        long expectedVersion = resolveExpectedVersion(request.version(), ifMatch);
        BusinessObject updated =
                businessObjectService.edit(
                        scope, type, new ObjectId(id), expectedVersion, request.properties());
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        return ResponseEntity.ok()
                .eTag(Long.toString(updated.version()))
                .body(ObjectPropertyMapper.toResponse(updated, requestType));
    }

    /**
     * @param type the type's short name or full IRI
     * @param id the id of the object to delete
     * @param ifMatch the {@code If-Match} header, or {@code null} to delete whatever version is
     *     currently stored
     * @return 204
     */
    @Operation(summary = "Soft-delete an existing object")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
            responseCode = "404",
            description = "No such type, or no such object",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The stored object is not at the expected version",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/objects/{type}/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("type") String type,
            @PathVariable("id") UUID id,
            @Parameter(description = "The version the caller expects the object to currently be at")
                    @RequestHeader(value = "If-Match", required = false)
                    String ifMatch) {
        OptionalLong expectedVersion =
                ifMatch == null ? OptionalLong.empty() : OptionalLong.of(parseVersion(ifMatch));
        businessObjectService.delete(currentScope(), type, new ObjectId(id), expectedVersion);
        return ResponseEntity.noContent().build();
    }

    private static long resolveExpectedVersion(Long bodyVersion, String ifMatch) {
        Long headerVersion = ifMatch == null ? null : parseVersion(ifMatch);
        if (bodyVersion == null && headerVersion == null) {
            throw new PreconditionRequiredException();
        }
        if (bodyVersion != null && headerVersion != null && !bodyVersion.equals(headerVersion)) {
            throw new PreconditionMismatchException(bodyVersion, headerVersion);
        }
        return bodyVersion != null ? bodyVersion : headerVersion;
    }

    private static long parseVersion(String ifMatch) {
        return Long.parseLong(stripQuotes(ifMatch));
    }

    private static String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Scope currentScope() {
        return new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    }
}
