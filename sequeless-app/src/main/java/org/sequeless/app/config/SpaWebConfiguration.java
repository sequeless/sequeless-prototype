package org.sequeless.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the application's {@code /**} static-resource handler, chaining the terminal
 * {@link SpaFallbackResourceResolver} that turns Angular client-side deep links into the SPA's
 * {@code index.html} instead of a 404 — while every real file, every reserved server prefix, and
 * every missing asset still resolves exactly as they would without the fallback.
 *
 * <p><b>Why a top-level non-bean class, not a nested one.</b> Same reason as {@link
 * TomcatEncodedSlashConfiguration}: reached directly by component scan as a candidate of
 * {@code @SpringBootApplication}'s package, a {@code @Configuration} class here is guaranteed to
 * be part of the context, so its {@code WebMvcConfigurer} callbacks are guaranteed to run. Nesting
 * it inside another top-level class is a well-known idiom in general, but the flat layout makes
 * the wiring visible at a glance and leaves no headroom for scan-order surprises.
 *
 * <p><b>Why {@code WebProperties} instead of reading the property directly.</b> The static
 * locations come from {@code WebProperties#getResources}().{@code getStaticLocations()}, i.e.
 * the live {@code spring.web.resources.static-locations} value the rest of Boot itself uses.
 * That is what makes the acceptance tests work: {@code SpaFallbackWithAssetsTest} pins that
 * property to {@code classpath:/test-spa/} (where the fixture UI build lives) and {@code
 * SpaFallbackWithoutAssetsTest} pins it to a location nothing provides, and whichever list they
 * set is the list this handler serves real files from. Reading the raw property string in
 * parallel would drift out of sync with Boot's own view the moment somebody overrides it.
 *
 * <p><b>Why the hardcoded fallback list.</b> {@code WebProperties.getResources().getStaticLocations()}
 * is non-null in practice, but a user can legitimately configure the list to be empty to turn
 * off Boot's static locations entirely. Rather than registering a {@code /**} handler with zero
 * locations and handing Spring an unusable handler, this falls back to Boot's own documented
 * default locations (the ones {@code WebProperties} ships with when the property is unset).
 * In the two acceptance tests the list is never empty, so this branch only matters for
 * misconfiguration — and it produces a sane handler instead of a broken one.
 *
 * <p><b>Why the resolver is the terminal one on the chain.</b> {@code resourceChain(true)} plus a
 * single terminal resolver is the shape Spring documents for custom resolver chains: our
 * {@link SpaFallbackResourceResolver} already delegates to {@code super} (i.e. Stock {@code
 * PathResourceResolver}) first, so real files are still served from the locations above with all
 * of {@code PathResourceResolver}'s traversal and containment checks. Adding any other resolver
 * after it would never be reached for paths the fallback answers, and would only muddy ownership
 * — the fallback is deliberately the last say the server has on this path before 404.
 *
 * <p><b>Why a fixed default registration order, and no {@code Ordered} here.</b> {@code
 * WebMvcConfigurer} itself already defines {@code getOrder()} as {@code
 * Ordered.LOWEST_PRECEDENCE}, which is the known-correct position for this bean: earlier
 * {@code WebMvcConfigurer} callbacks (including any app or library ones) run first, and this
 * handler simply adds its {@code /**} entry last, which is what we want. Re-declaring {@code
 * Ordered} on the configuration class would add a second, redundant knob for an ordering that
 * does not need adjusting, so the class intentionally does not implement it.
 *
 * <p><b>Why {@code SpaFallbackResourceResolver} is constructed here and not a {@code @Bean}.</b>
 * The index, reserved prefixes and the enabled switch are all resolved from properties in one
 * place; building the object inline keeps the three inputs and their consumer together, and
 * avoids a third bean whose existence in the context would be an accident of Spring wiring
 * rather than a documented dependency.
 *
 * <p><b>Deliberately no {@code org.sequeless.core} imports.</b> Same rule as {@code
 * SpaFallbackResourceResolver}: the ArchUnit rule {@code onlyAppRestAndConfigMayDependOnCore} in
 * {@code ArchitectureTest} allows classes in {@code org.sequeless.app.config} to depend on the
 * core; this class simply has no need to, and is pure JDK + Spring.
 *
 * <p><b>Why {@code @ConditionalOnWebApplication}.</b> This configuration is only meaningful in a
 * servlet web application — its whole purpose is registering an HTTP resource handler — so it
 * must not even be a candidate bean in a {@code WebApplicationType.NONE} application, both
 * because that context has no WebMvc notion to plug into, and because the {@code WebProperties}
 * bean this class's constructor requires is never auto-configured there.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class SpaWebConfiguration implements WebMvcConfigurer {

    private static final String[] DEFAULT_STATIC_LOCATIONS = {
        "classpath:/META-INF/resources/", "classpath:/resources/", "classpath:/static/", "classpath:/public/"
    };

    private final WebProperties webProperties;

    private final ApplicationContext applicationContext;

    /** Where the SPA's {@code index.html} is resolved from; see the class javadoc. */
    @Value("${sequeless.web.spa.location:classpath:/static/}")
    private String spaLocation;

    /** File name (relative to {@link #spaLocation}) that counts as the SPA's entry point. */
    @Value("${sequeless.web.spa.index:index.html}")
    private String spaIndex;

    /**
     * The API prefix the app serves its REST endpoints under (see {@code
     * ApiPathPrefixConfiguration}); kept as a string here only so it can be appended to the
     * reserved-prefix list when non-blank, never resolved against the filesystem.
     */
    @Value("${sequeless.api.base-path:/api}")
    private String apiBasePath;

    /** Master switch: {@code false} turns the fallback resolver into a plain path resolver. */
    @Value("${sequeless.web.spa.enabled:true}")
    private boolean spaEnabled;

    /**
     * @param webProperties the auto-configured Boot web properties, used to read the live
     *     {@code spring.web.resources.static-locations} value (see class javadoc)
     * @param applicationContext the context's {@code ApplicationContext}, used to resolve the
     *     SPA's {@code index.html} into a {@link Resource} without committing to one
     *     {@link org.springframework.core.io.Resource} implementation
     */
    public SpaWebConfiguration(WebProperties webProperties, ApplicationContext applicationContext) {
        this.webProperties = webProperties;
        this.applicationContext = applicationContext;
    }

    /**
     * Registers the single {@code /**} handler for the whole application, chained with
     * {@link SpaFallbackResourceResolver} as the terminal resolver. See the class javadoc for
     * why each element (locations source, fallback list, chain shape, ordering) is what it is.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] locations = webProperties.getResources().getStaticLocations();
        if (locations == null || locations.length == 0) {
            locations = DEFAULT_STATIC_LOCATIONS;
        }

        // Join defensively: both the property's default (classpath:/static/) and the tests'
        // fixture paths (classpath:/test-spa/) already carry a trailing slash, so a plain "+"
        // would produce "...test-spa//index.html". Exactly one "/" is needed between the parts.
        Resource index = applicationContext.getResource(indexLocationPrefix(spaLocation) + spaIndex);

        List<String> reservedPrefixes = new ArrayList<>();
        if (apiBasePath != null && !apiBasePath.isBlank()) {
            reservedPrefixes.add(apiBasePath);
        }
        reservedPrefixes.add("/actuator");
        reservedPrefixes.add("/swagger-ui");
        reservedPrefixes.add("/v3/api-docs");
        reservedPrefixes.add("/error");
        reservedPrefixes.add("/webjars");

        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver(index, reservedPrefixes, spaEnabled));
    }

    /**
     * Normalises {@code location} so that exactly one {@code /} separates it from the index file
     * name, whether the property is written as {@code classpath:/test-spa} or {@code
     * classpath:/test-spa/}.
     */
    private static String indexLocationPrefix(String location) {
        return location.endsWith("/") ? location : location + "/";
    }
}
