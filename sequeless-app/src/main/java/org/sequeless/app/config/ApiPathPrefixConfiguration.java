package org.sequeless.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Moves every REST controller in {@code org.sequeless.app.rest} — {@code OntologyController},
 * {@code TypesController}, {@code ObjectsController}, {@code WhoAmIController} — under a
 * configurable base path, defaulting to {@code /api} and overridable via the {@code
 * sequeless.api.base-path} property in {@code application.yaml}.
 *
 * <p>This is a <b>move, not an addition</b>: once the prefix is registered, the unprefixed paths
 * ({@code /whoami}, {@code /types/{nameOrIri}}, ...) no longer resolve and return 404. Clients
 * must address the API at {@code /api/whoami}, {@code /api/types/{nameOrIri}}, and so on.
 *
 * <p><b>Why the predicate is package-scoped rather than annotation-scoped.</b> The prefix is
 * applied with {@link HandlerTypePredicate#forBasePackage} on {@code org.sequeless.app.rest}
 * instead of {@code HandlerTypePredicate.forAnnotation(RestController.class)} on purpose:
 * springdoc-openapi itself exposes {@code @RestController} endpoints ({@code /v3/api-docs},
 * {@code /swagger-ui.html}), and an annotation-scoped predicate would prefix and move those too,
 * breaking every tool that hardcodes the conventional OpenAPI locations. Scoping by the
 * application's own package excludes springdoc's handlers while still covering all of the
 * application's controllers. Actuator is unaffected either way — it is served by its own handler
 * mapping, not the one this prefix is registered on — so {@code /actuator/health} stays put.
 *
 * <p>Because springdoc reads the registered {@code RequestMappingInfo} of each handler when
 * generating the OpenAPI document, the generated spec automatically advertises the prefixed paths
 * (e.g. {@code /api/types/{nameOrIri}}) with no extra configuration.
 *
 * <p>The encoded-slash handling from {@link TomcatEncodedSlashConfiguration} keeps working under
 * the prefix: it operates at the Tomcat connector level, before path matching, so {@code
 * /api/types/{iri}} with a percent-encoded slash in the IRI resolves exactly as it did before
 * the prefix existed.
 *
 * <p><b>Must be a top-level class, not nested inside another class.</b> See the note in {@link
 * TomcatEncodedSlashConfiguration} about a nested {@code @Configuration} class silently failing
 * to register via component scan: for the same reason, this class is kept in its own top-level
 * file rather than nested inside another class.
 */
@Configuration(proxyBeanMethods = false)
public class ApiPathPrefixConfiguration implements WebMvcConfigurer {

    /**
     * Base package of the application's REST controllers. The path prefix is applied to handlers
     * in this package (and subpackages) only, which deliberately excludes springdoc's own
     * {@code @RestController} endpoints — see the class javadoc for why an annotation-scoped
     * predicate would be wrong here.
     */
    private static final String REST_PACKAGE = "org.sequeless.app.rest";

    private final String basePath;

    /**
     * @param basePath the base path to prefix the REST controllers with, from the {@code
     *     sequeless.api.base-path} property (default {@code /api}); a {@code null} or blank value
     *     is an explicit opt-out that serves the controllers at the root
     */
    public ApiPathPrefixConfiguration(@Value("${sequeless.api.base-path:/api}") String basePath) {
        this.basePath = basePath;
    }

    /**
     * Registers the base path as a path prefix for every handler in {@link #REST_PACKAGE}.
     *
     * <p>A {@code null} or blank value registers nothing on purpose: it is an explicit opt-out
     * that serves the controllers at the root, and calling {@code addPathPrefix} with an empty
     * prefix would at best be a no-op and at worst be misleading, so the blank case is skipped
     * outright. The value is trimmed so that an accidentally padded property value (e.g. {@code
     * " /api "}) still produces the clean {@code /api} prefix instead of a broken one.
     *
     * @param configurer the path-match configurer to register the prefix with
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        if (basePath == null || basePath.trim().isBlank()) {
            return;
        }
        configurer.addPathPrefix(basePath.trim(), HandlerTypePredicate.forBasePackage(REST_PACKAGE));
    }
}
