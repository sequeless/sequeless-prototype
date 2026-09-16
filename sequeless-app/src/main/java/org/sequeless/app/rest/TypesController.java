package org.sequeless.app.rest;

import java.util.List;
import java.util.Objects;
import org.sequeless.app.config.TomcatEncodedSlashConfiguration;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@link MetaModelService} over HTTP: {@code GET /types}, {@code GET /types/{name}}, and
 * {@code POST /types/reload}.
 *
 * <p>Resolution lives in core, not here: this controller passes the raw {@code nameOrIri} path
 * variable straight through to {@link MetaModelService#describeType}, which does all
 * short-name-vs-IRI disambiguation. REST stays a thin shim, mirroring {@link WhoAmIController}.
 *
 * <p>Every method scopes its call to {@link TenantId#DEFAULT} and {@link Principal#ANONYMOUS}, for
 * the same reason {@link WhoAmIController} does: Phase 0 has no inbound identity provider wired up
 * yet.
 */
@RestController
public class TypesController {

    private final MetaModelService metaModelService;

    /**
     * @param metaModelService the use case this controller delegates to; must not be {@code null}
     * @throws NullPointerException if {@code metaModelService} is {@code null}
     */
    public TypesController(MetaModelService metaModelService) {
        this.metaModelService =
                Objects.requireNonNull(metaModelService, "metaModelService must not be null");
    }

    /**
     * @return every type in the current snapshot, summarised
     */
    @GetMapping("/types")
    public List<TypeSummaryResponse> listTypes() {
        MetaModelSnapshot snapshot = metaModelService.snapshot(currentScope());
        return TypeResponseMapper.toSummaries(snapshot);
    }

    /**
     * The path variable is matched with the {@code {nameOrIri:.+}} wildcard, not the default
     * single-segment {@code {nameOrIri}}, because a full type IRI — once Tomcat has decoded a
     * URL-encoded {@code %2F} back into a literal {@code /} (see {@link
     * TomcatEncodedSlashConfiguration}) — contains literal slashes that a single-segment variable
     * would not capture.
     *
     * @param nameOrIri the type's short name or full IRI, taken verbatim from the path
     * @return the resolved type's full detail, including its properties in display order
     */
    @GetMapping("/types/{nameOrIri:.+}")
    public TypeDetailResponse describeType(@PathVariable("nameOrIri") String nameOrIri) {
        TypeDefinition type = metaModelService.describeType(currentScope(), nameOrIri);
        return TypeResponseMapper.toDetail(type);
    }

    /**
     * @return the snapshot report of the freshly reloaded ontology, rendered the same way {@code
     *     ApiExceptionAdvice} renders one on failure — one shape for a client to handle either
     *     outcome
     */
    @PostMapping("/types/reload")
    public OntologyReportResponse reload() {
        MetaModelSnapshot snapshot = metaModelService.reload(currentScope());
        return OntologyReportResponse.from(snapshot.report());
    }

    private static Scope currentScope() {
        return new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    }
}
