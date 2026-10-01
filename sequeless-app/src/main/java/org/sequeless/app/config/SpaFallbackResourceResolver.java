package org.sequeless.app.config;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * The {@link PathResourceResolver} subclass that {@code SpaWebConfiguration} installs on its
 * {@code /**} resource handler so that the Angular UI's client-side routes (deep links such as
 * {@code /ontology/classes/Project}) are answered with the SPA's {@code index.html} instead of a
 * 404, while every real file, every reserved server prefix and every missing asset still resolves
 * exactly as they would without the fallback.
 *
 * <p><b>Why a fallback at all.</b> Angular is a client-side router: the browser owns the URL bar
 * and the server only ever sees the initial document request. A user who reloads the page on
 * {@code /ontology/classes/Project} — or opens that URL from a bookmark — sends a plain
 * {@code GET /ontology/classes/Project} to the server, which has no such static file and would
 * 404. Answering such requests with {@code index.html} lets the Angular router take over and
 * render the page, which is the standard "SPA fallback" pattern.
 *
 * <p><b>Why the fallback must be surgical.</b> The {@code /**} handler sits in front of the
 * server's own endpoints, so a careless fallback would shadow them: an API client hitting an
 * unknown {@code /api/...} URL would receive 200 {@code text/html} (the UI shell) and fail with a
 * confusing JSON parse error instead of a clear 404; an unexposed Actuator endpoint would start
 * answering probes with HTML. That is why the reserved prefixes — the API base path (default
 * {@code /api}, see {@code sequeless.api.base-path} and {@code ApiPathPrefixConfiguration}) plus
 * the unprefixed server-owned paths springdoc ({@code /swagger-ui}, {@code /v3/api-docs}),
 * Actuator ({@code /actuator}), Boot's error controller ({@code /error}) and webjars
 * ({@code /webjars}) — are passed in by the configuration and never answered with the index.
 *
 * <p><b>Why the dot guard.</b> After a redeploy, a browser holding a stale {@code index.html}
 * asks for a bundle hash that no longer exists (e.g. {@code assets/app-deadbeef.js}). If the
 * fallback answered that with {@code index.html}, the browser would receive HTML where it
 * expected JavaScript and surface the baffling "Unexpected token '<'" / strict-MIME-type error
 * instead of an honest 404. Any path whose last segment contains a {@code .} is therefore
 * treated as an asset request and left to 404.
 *
 * <p><b>Why the index-existence check.</b> The resolver is installed unconditionally; when no UI
 * has been built (the normal {@code mvn verify} state) there is no {@code index.html} to serve.
 * Returning {@code null} in that case yields plain 404s rather than handing Spring a resource it
 * cannot read.
 *
 * <p><b>Why extend {@code PathResourceResolver} rather than replace it.</b> The first step of
 * {@link #getResource(String, Resource)} delegates to {@code super}, which preserves
 * {@code PathResourceResolver}'s path-traversal and "is the resource under the location" checks
 * for real files; the fallback logic only runs for paths that genuinely resolved to nothing.
 *
 * <p><b>Deliberately no {@code org.sequeless.core} imports.</b> The ArchUnit rule
 * {@code onlyAppRestAndConfigMayDependOnCore} in {@code ArchitectureTest} allows classes in
 * {@code org.sequeless.app.config} to depend on the core; this class simply has no need to, and
 * is pure JDK + Spring.
 *
 * <p><b>Contract, in evaluation order</b> (see {@code SpaFallbackResourceResolverTest}):
 *
 * <ol>
 *   <li>a real file found by {@code super.getResource} is returned untouched;
 *   <li>when the fallback is disabled, nothing else is ever returned;
 *   <li>a path equal to a reserved prefix, or nested beneath one ({@code prefix + "/"}), is never
 *       answered with the index — so {@code /api/nope} still 404s as JSON rather than rendering
 *       the UI shell, and {@code /apifoo} (a sibling, not a child) is NOT treated as reserved;
 *   <li>a path whose last segment contains a {@code .} is treated as an asset request and never
 *       answered with the index (the "dot guard");
 *   <li>otherwise the index is returned, provided it exists.
 * </ol>
 */
public class SpaFallbackResourceResolver extends PathResourceResolver {

    private final Resource index;

    private final List<String> reservedPrefixes;

    private final boolean enabled;

    /**
     * @param index the SPA's {@code index.html} to answer deep links with; must not be null (it
     *     may, however, point at a file that does not exist yet — see the class javadoc)
     * @param reservedPrefixes server-owned path prefixes (e.g. {@code /api}, {@code /actuator})
     *     that must never be answered with the index; must not be null; defensively copied, so
     *     the caller's list may be mutated afterwards
     * @param enabled whether the fallback is active at all; {@code false} turns this resolver
     *     back into a plain {@code PathResourceResolver} (the {@code sequeless.web.spa.enabled}
     *     escape hatch)
     */
    public SpaFallbackResourceResolver(
            Resource index, List<String> reservedPrefixes, boolean enabled) {
        this.index = Objects.requireNonNull(index, "index");
        this.reservedPrefixes =
                List.copyOf(Objects.requireNonNull(reservedPrefixes, "reservedPrefixes"));
        this.enabled = enabled;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluates the five rules in the order documented in the class javadoc: real file first,
     * then the disabled switch, the reserved-prefix guard, the dot guard, and finally the index
     * (if it exists).
     */
    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        // Rule 1: a real file wins, always. Delegating to super first also keeps
        // PathResourceResolver's path-traversal and location-containment checks in force for
        // every file we do serve.
        Resource found = super.getResource(resourcePath, location);
        if (found != null) {
            return found;
        }

        // Rule 2: disabled means "behave exactly like a plain PathResourceResolver".
        if (!enabled) {
            return null;
        }

        // Rule 3: reserved prefixes are matched on a segment boundary — path.equals(prefix) or
        // path.startsWith(prefix + "/") — so "/apifoo" is a legitimate client-side route, not
        // part of the API.
        String path = "/" + resourcePath;
        for (String prefix : reservedPrefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return null;
            }
        }

        // Rule 4: the dot guard. A missing asset must 404 instead of returning HTML, otherwise a
        // browser holding a stale index.html after a redeploy gets HTML for a missing JS bundle
        // and shows a baffling "Unexpected token '<'" / strict MIME type error.
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.contains(".")) {
            return null;
        }

        // Rule 5: the index, provided it exists (the "no UI built" state yields plain 404s).
        return index.exists() ? index : null;
    }
}
