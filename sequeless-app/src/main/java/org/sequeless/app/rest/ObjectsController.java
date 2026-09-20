package org.sequeless.app.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sequeless.core.api.BrowseQuery;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.statemachine.StateMachineInterpreter;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.query.QueryResult;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
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
 * /objects/{type}} and {@code /objects/{type}/{id}}, per plan.md §8. Also exposes {@link
 * TransitionService} over HTTP: {@code GET .../transitions} ({@link #transitions}) and {@code POST
 * .../transitions/{name}} ({@link #fireTransition}), per plan.md §8's state-machine addendum —
 * {@code GET} reads availability computed the same way the {@code POST} write path itself decides
 * whether the named transition may fire, via the shared {@link StateMachineInterpreter}.
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
    private final TransitionService transitionService;
    private final ExpressionPort expressionPort;

    /**
     * @param businessObjectService the use case this controller delegates every BREAD operation
     *     to; must not be {@code null}
     * @param metaModelService the use case consulted for the current request's resolved {@link
     *     TypeDefinition}, used only to render the response's property names; must not be {@code
     *     null}
     * @param transitionService the use case {@link #fireTransition} delegates to; must not be
     *     {@code null}
     * @param expressionPort the port {@link #transitions} evaluates transition guards through, via
     *     a fresh {@link StateMachineInterpreter} built per request, exactly as {@link
     *     org.sequeless.core.usecase.DefaultTransitionService} does internally on the write path;
     *     must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public ObjectsController(
            BusinessObjectService businessObjectService,
            MetaModelService metaModelService,
            TransitionService transitionService,
            ExpressionPort expressionPort) {
        this.businessObjectService =
                Objects.requireNonNull(businessObjectService, "businessObjectService must not be null");
        this.metaModelService =
                Objects.requireNonNull(metaModelService, "metaModelService must not be null");
        this.transitionService =
                Objects.requireNonNull(transitionService, "transitionService must not be null");
        this.expressionPort =
                Objects.requireNonNull(expressionPort, "expressionPort must not be null");
    }

    /**
     * @param type the type's short name or full IRI
     * @param page the 0-based page number; defaults to 0
     * @param size the requested page size; defaults to 20; capped at 200 (never rejected outright)
     * @param q a free-text search term, matched against the type's {@code sq:searchable}
     *     properties, or {@code null}/blank for no text search
     * @param sort comma-separated sort keys; a leading {@code -} means descending, e.g. {@code
     *     sort=-priority,dueDate}; {@code null}/blank for no explicit sort
     * @param facets comma-separated property short names (or full IRIs) to compute facet counts
     *     for; {@code null}/blank for no facets
     * @param allParams every query parameter on the request, used only to pick out {@code
     *     filter[<property>][<op>]=<value>} clauses (see {@link #parseFilters})
     * @return the matching page, rendered as {@link QueryResultResponse}
     */
    @Operation(summary = "List objects of a type, filtered, sorted, faceted, and free-text searched")
    @ApiResponse(responseCode = "200", description = "The requested page")
    @ApiResponse(
            responseCode = "400",
            description = "An invalid filter/sort/facet property or operator",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such type",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/objects/{type}")
    public QueryResultResponse browse(
            @PathVariable("type") String type,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Free-text search, matched against the type's sq:searchable properties")
                    @RequestParam(name = "q", required = false)
                    String q,
            @Parameter(
                            description =
                                    "Comma-separated sort keys; a leading '-' means descending, e.g."
                                            + " sort=-priority,dueDate")
                    @RequestParam(name = "sort", required = false)
                    String sort,
            @Parameter(description = "Comma-separated property short names to compute facet counts for")
                    @RequestParam(name = "facets", required = false)
                    String facets,
            @Parameter(
                            description =
                                    "Filter clauses as filter[<property>][<op>]=<value>, repeatable; <op> is"
                                        + " one of eq, ne, in, lt, lte, gt, gte, contains, startswith, isnull,"
                                        + " notnull (case-insensitive); multiple filters AND together, including"
                                        + " multiple filters on the same property (e.g."
                                        + " dueDate[gte]=...&dueDate[lte]=... for a range); 'in' takes a"
                                        + " comma-separated value; 'isnull'/'notnull' ignore any value")
                    @RequestParam
                    MultiValueMap<String, String> allParams) {
        Scope scope = currentScope();
        int effectiveSize = Math.min(size, 200);
        List<BrowseQuery.Filter> filters = parseFilters(allParams);
        List<BrowseQuery.SortKey> sortKeys = parseSort(sort);
        List<String> facetNames = parseCommaList(facets);
        BrowseQuery browseQuery =
                new BrowseQuery(
                        filters,
                        Optional.ofNullable(q).filter(s -> !s.isBlank()),
                        sortKeys,
                        new Page(page, effectiveSize),
                        facetNames);
        QueryResult result = businessObjectService.browse(scope, type, browseQuery);
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        return QueryResultResponse.from(result, requestType, page, effectiveSize);
    }

    private static final Pattern FILTER_KEY = Pattern.compile("filter\\[([^\\]]+)\\]\\[([^\\]]+)\\]");

    /**
     * Picks {@code filter[<property>][<op>]=<value>} entries out of every query parameter on the
     * request. {@code page}/{@code size}/{@code q}/{@code sort}/{@code facets} are all bound to
     * their own {@code @RequestParam} arguments already, but Spring still hands every parameter to
     * this {@link MultiValueMap} too — they are simply skipped here, since none of them matches
     * {@link #FILTER_KEY}.
     *
     * @param allParams every query parameter on the request; must not be {@code null}
     * @return one {@link BrowseQuery.Filter} per matching {@code key=value} pair, in encounter
     *     order; a property repeated with the same or different operators yields one filter per
     *     occurrence, which {@link BrowseQuery} itself ANDs together (e.g. a {@code gte}/{@code
     *     lte} range on the same property)
     */
    private static List<BrowseQuery.Filter> parseFilters(MultiValueMap<String, String> allParams) {
        List<BrowseQuery.Filter> filters = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : allParams.entrySet()) {
            Matcher matcher = FILTER_KEY.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            String property = matcher.group(1);
            String operator = matcher.group(2);
            for (String value : entry.getValue()) {
                filters.add(new BrowseQuery.Filter(property, operator, Optional.of(value)));
            }
        }
        return filters;
    }

    /**
     * @param sort comma-separated sort keys, a leading {@code -} meaning descending; {@code null}
     *     or blank for no sort
     * @return one {@link BrowseQuery.SortKey} per token, in the order given; empty if {@code sort}
     *     is {@code null}/blank
     */
    private static List<BrowseQuery.SortKey> parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return List.of();
        }
        List<BrowseQuery.SortKey> sorts = new ArrayList<>();
        for (String token : sort.split(",")) {
            String trimmed = token.trim();
            if (trimmed.startsWith("-")) {
                sorts.add(new BrowseQuery.SortKey(trimmed.substring(1), "desc"));
            } else {
                sorts.add(new BrowseQuery.SortKey(trimmed, "asc"));
            }
        }
        return sorts;
    }

    /**
     * @param value a comma-separated list, or {@code null}/blank for none
     * @return {@code value}'s comma-separated tokens, trimmed, with any blank tokens dropped;
     *     empty if {@code value} is {@code null}/blank
     */
    private static List<String> parseCommaList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
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

    /**
     * @param type the type's short name or full IRI
     * @param id the object's id
     * @return every transition departing from the object's current state, each with its
     *     availability and (when unavailable) a reason, computed the same way {@link
     *     TransitionService#fire} itself decides whether a requested transition may fire
     */
    @Operation(summary = "List every transition available from an object's current state")
    @ApiResponse(responseCode = "200", description = "The available transitions, possibly empty")
    @ApiResponse(
            responseCode = "404",
            description = "No such type, or no such object",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/objects/{type}/{id}/transitions")
    public List<TransitionResponse> transitions(
            @PathVariable("type") String type, @PathVariable("id") UUID id) {
        Scope scope = currentScope();
        BusinessObject object = businessObjectService.read(scope, type, new ObjectId(id));
        MetaModelSnapshot snapshot = metaModelService.snapshot(scope);
        StateMachineInterpreter interpreter = new StateMachineInterpreter(expressionPort);
        return interpreter.availableTransitions(scope, snapshot, object).stream()
                .map(TransitionResponse::from)
                .toList();
    }

    /**
     * @param type the type's short name or full IRI
     * @param id the id of the object to transition
     * @param name the name of the transition to fire
     * @param request the version (nullable), or {@code null} if the request has no body at all —
     *     unlike {@link #edit}, a transition has no other body content, so a client relying purely
     *     on {@code If-Match} may send no body whatsoever
     * @param ifMatch the {@code If-Match} header, or {@code null} if absent
     * @return 200 with the object after the transition, plus an {@code ETag} carrying its new
     *     version
     */
    @Operation(summary = "Fire a named transition on an existing object")
    @ApiResponse(responseCode = "200", description = "The object after the transition")
    @ApiResponse(
            responseCode = "404",
            description = "No such type, or no such object",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "The stored object is not at the expected version, or the named transition is"
                            + " not available (no such transition, wrong current state, or its guard"
                            + " failed)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description = "Neither a body version nor an If-Match header was supplied",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/objects/{type}/{id}/transitions/{name}")
    public ResponseEntity<BusinessObjectResponse> fireTransition(
            @PathVariable("type") String type,
            @PathVariable("id") UUID id,
            @PathVariable("name") String name,
            @RequestBody(required = false) TransitionRequest request,
            @Parameter(description = "The version the caller expects the object to currently be at")
                    @RequestHeader(value = "If-Match", required = false)
                    String ifMatch) {
        Scope scope = currentScope();
        Long bodyVersion = request == null ? null : request.expectedVersion();
        long expectedVersion = resolveExpectedVersion(bodyVersion, ifMatch);
        BusinessObject updated =
                transitionService.fire(scope, type, new ObjectId(id), name, expectedVersion);
        TypeDefinition requestType = metaModelService.describeType(scope, type);
        return ResponseEntity.ok()
                .eTag(Long.toString(updated.version()))
                .body(ObjectPropertyMapper.toResponse(updated, requestType));
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
