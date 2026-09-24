package org.sequeless.app.config;

import java.time.Clock;
import java.time.Duration;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.core.api.OntologyAdministration;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.api.WhoAmI;
import org.sequeless.core.automation.DefaultActionExecutor;
import org.sequeless.core.automation.DefaultTriggerEvaluator;
import org.sequeless.core.derivation.DefaultDerivationRecomputer;
import org.sequeless.core.derivation.DerivationPlanner;
import org.sequeless.core.derivation.DerivationPluginRegistry;
import org.sequeless.core.usecase.DefaultBusinessObjectService;
import org.sequeless.core.usecase.DefaultMetaModelService;
import org.sequeless.core.usecase.DefaultOntologyAdministration;
import org.sequeless.core.usecase.DefaultTransitionService;
import org.sequeless.core.usecase.DefaultWhoAmI;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.validation.ValidationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Wires sequeless-core's use cases into the Spring context.
 *
 * <p>{@code proxyBeanMethods = false} is safe here: {@link #whoAmI(AuthorizationPort)} never calls
 * another {@code @Bean} method on {@code this}, so there is nothing for CGLIB inter-bean-call
 * interception to preserve, and skipping it avoids generating a proxy subclass for no benefit.
 */
@Configuration(proxyBeanMethods = false)
public class CoreConfiguration {

    /**
     * Builds the {@link WhoAmI} use case around whichever {@link AuthorizationPort} adapter is
     * configured.
     *
     * <p><b>Why the parameter is {@code @Lazy}, and why that must not be "simplified" away:</b> if
     * {@code authorizationPort} bound eagerly, Spring would have to resolve an {@link
     * AuthorizationPort} bean while it is still running {@code preInstantiateSingletons()} — the
     * first singleton-creation pass. But {@code PortRegistry} (see {@code org.sequeless.app.port})
     * only validates the configured adapter — including producing its own clean, property-naming
     * {@code PortBindingException} when zero or more than one {@link AuthorizationPort} bean
     * exists — in {@code afterSingletonsInstantiated()}, the <em>second</em> pass, via {@code
     * SmartInitializingSingleton}. With an unset or misconfigured {@code sequeless.authz.adapter}
     * there may be zero {@link AuthorizationPort} beans at all, and an eager dependency here would
     * let Spring's own generic {@code UnsatisfiedDependencyException} fire during pass one, before
     * {@code PortRegistry} ever gets a chance to run and report the actionable error.
     *
     * <p>{@code @Lazy} on an interface-typed parameter makes Spring inject a JDK dynamic proxy
     * immediately, deferring actual candidate resolution to the first real method call on it. That
     * lets this bean — and the {@code WhoAmIController} that depends on it — construct cleanly in
     * pass one regardless of how many (zero, one, or many) {@link AuthorizationPort} beans exist,
     * so {@code PortRegistry}'s pass-two check becomes the first thing that actually counts beans
     * and reports a failure. The proxy is dereferenced only on the happy path, once a request
     * actually calls {@link WhoAmI#whoAmI}, by which point {@code PortRegistry} has already
     * guaranteed exactly one bean exists.
     *
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @return a {@link DefaultWhoAmI} consulting {@code authorizationPort} on every call
     */
    @Bean
    public WhoAmI whoAmI(@Lazy AuthorizationPort authorizationPort) {
        return new DefaultWhoAmI(authorizationPort);
    }

    /**
     * Builds the {@link MetaModelService} use case around whichever {@link OntologyPort} and {@link
     * AuthorizationPort} adapters are configured.
     *
     * <p><b>Both parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above, and neither may be "simplified" away.</b> {@code
     * PortRegistry} now validates two port slots — {@code sequeless.authz.adapter} and {@code
     * sequeless.ontology.adapter} — but it still does so in {@code afterSingletonsInstantiated()},
     * the second singleton pass. An eager parameter here would force Spring to resolve that port
     * during {@code preInstantiateSingletons()}, the first pass, where an unset or misconfigured
     * {@code sequeless.ontology.adapter} means zero {@link OntologyPort} beans exist and Spring's
     * own generic {@code UnsatisfiedDependencyException} would fire before {@code PortRegistry}
     * ever ran. The whole point of {@code PortRegistry} is that a misconfigured adapter produces a
     * message naming the property and listing the adapters actually available; binding eagerly here
     * would throw that away for the ontology slot exactly as it would for the authz slot.
     *
     * <p>Note that {@code @Lazy} also means nothing dereferences the {@link OntologyPort} during
     * startup on its own. Since an inconsistent ontology must fail startup rather than surface on a
     * first request, {@link OntologyStartupValidator} exists to force that first call at a point in
     * the lifecycle where it is safe — see its javadoc.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @return a {@link DefaultMetaModelService} consulting both ports on every call
     */
    @Bean
    public MetaModelService metaModelService(
            @Lazy OntologyPort ontologyPort, @Lazy AuthorizationPort authorizationPort) {
        return new DefaultMetaModelService(ontologyPort, authorizationPort);
    }

    /**
     * Builds the {@link DerivationPlanner} shared by {@link #businessObjectService} (recomputing
     * every derived property a {@code read}/{@code browse} response returns) and {@link
     * #derivationRecomputer} (recomputing a single materialised rule for a single target off a
     * domain event) — one instance, so both callers resolve {@code sq:Rollup}/{@code sq:Plugin}
     * rules through the exact same code path rather than two independently constructed planners
     * drifting apart. Previously {@link DefaultBusinessObjectService} built its own {@code
     * DerivationPlanner} inline in its convenience constructor; extracting it to a bean here is what
     * lets {@link #derivationRecomputer} reuse it instead of constructing a second, divergent one.
     *
     * <p>{@code queryPort} is {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above.
     *
     * @param queryPort the lazily-resolved query port adapter, used both to resolve rollups and to
     *     hand to plug-ins
     * @return a {@link DerivationPlanner} backed by every {@link
     *     org.sequeless.spi.derivation.DerivationPlugin} on the classpath
     */
    @Bean
    public DerivationPlanner derivationPlanner(@Lazy QueryPort queryPort) {
        return new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader());
    }

    /**
     * Builds the {@link BusinessObjectService} use case around whichever {@link OntologyPort},
     * {@link ObjectStorePort}, {@link ValidationPort}, {@link AuthorizationPort}, and {@link
     * QueryPort} adapters are configured.
     *
     * <p><b>All five port-typed parameters are {@code @Lazy} for exactly the reason spelled out on
     * {@link #whoAmI(AuthorizationPort)} above, and none may be "simplified" away.</b> {@code
     * PortRegistry} now validates five port slots, including {@code sequeless.persistence.adapter},
     * {@code sequeless.validation.adapter}, and {@code sequeless.query.adapter}, but it still does
     * so in {@code afterSingletonsInstantiated()}, the second singleton pass. An eager parameter
     * here would force Spring to resolve that port during {@code preInstantiateSingletons()}, the
     * first pass, before {@code PortRegistry} ever ran.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param validationPort the lazily-resolved validation port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @param queryPort the lazily-resolved query port adapter
     * @param derivationPlanner the {@link DerivationPlanner} bean built above, shared with {@link
     *     #derivationRecomputer}
     * @return a {@link DefaultBusinessObjectService} consulting all five ports on every call, with
     *     its clock drawn from {@link Clock#systemUTC()}
     */
    @Bean
    public BusinessObjectService businessObjectService(
            @Lazy OntologyPort ontologyPort,
            @Lazy ObjectStorePort objectStorePort,
            @Lazy ValidationPort validationPort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy QueryPort queryPort,
            DerivationPlanner derivationPlanner) {
        return new DefaultBusinessObjectService(
            ontologyPort, objectStorePort, validationPort, authorizationPort, queryPort,
            Clock.systemUTC(), derivationPlanner);
    }

    /**
     * Builds the {@link OntologyAdministration} use case around whichever {@link OntologyPort},
     * {@link AuthorizationPort}, and {@link QueryPort} adapters are configured.
     *
     * <p>All three parameters are {@code @Lazy} for the same reason as {@link #metaModelService}.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @param queryPort the lazily-resolved query port adapter, primed with fresh indexes by {@link
     *     DefaultOntologyAdministration#importTurtle} after a successful import
     * @return a {@link DefaultOntologyAdministration} consulting all three ports on every call
     */
    @Bean
    public OntologyAdministration ontologyAdministration(
            @Lazy OntologyPort ontologyPort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy QueryPort queryPort) {
        return new DefaultOntologyAdministration(ontologyPort, authorizationPort, queryPort);
    }

    /**
     * Builds the {@link ActionExecutor} implementation around whichever {@link ExpressionPort} and
     * {@link ObjectStorePort} adapters are configured. Unlike every other bean in this class, {@code
     * ActionExecutor} is not consumed by a REST controller in this module — it is consumed by
     * whichever automation adapter ({@code sequeless-adapter-automation-inprocess} or {@code
     * sequeless-adapter-automation-temporal}) is on the classpath and selected by {@code
     * sequeless.automation.adapter}. Both adapters' own {@code @AutoConfiguration} classes declare,
     * in their javadoc, that they take an {@link ActionExecutor} as a plain {@code @Bean} method
     * parameter and expect "some other, application-level configuration" to have already registered
     * it on the context by the time they run — this bean method is that configuration. Neither
     * adapter module may depend on {@code sequeless-core} (the {@code noAdapterDependsOnCore}
     * architecture rule), which is exactly why {@link DefaultActionExecutor} has to be wired here,
     * in one of the two packages this reactor permits to depend on {@code sequeless-core}.
     *
     * <p>Both parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above, and neither may be "simplified" away.
     *
     * <p>No shared {@link Clock} bean exists in this application — {@link Clock#systemUTC()} is
     * called inline here, matching {@link #businessObjectService}'s existing convention, rather than
     * introducing a new shared bean for one call site each.
     *
     * @param expressionPort the lazily-resolved expression port adapter
     * @param objectStorePort the lazily-resolved object store port adapter
     * @return a {@link DefaultActionExecutor} consulting both ports on every call
     */
    @Bean
    public ActionExecutor actionExecutor(
            @Lazy ExpressionPort expressionPort, @Lazy ObjectStorePort objectStorePort) {
        return new DefaultActionExecutor(expressionPort, objectStorePort, Clock.systemUTC());
    }

    /**
     * Builds the {@link TransitionService} use case around whichever {@link OntologyPort}, {@link
     * ObjectStorePort}, {@link AuthorizationPort}, and {@link ExpressionPort} adapters are
     * configured.
     *
     * <p>All four port-typed parameters are {@code @Lazy} for exactly the reason spelled out on
     * {@link #whoAmI(AuthorizationPort)} above, and none may be "simplified" away.
     *
     * <p>No shared {@link Clock} bean exists in this application; see {@link #actionExecutor}'s
     * javadoc for why {@link Clock#systemUTC()} is called inline here too.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @param expressionPort the lazily-resolved expression port adapter
     * @return a {@link DefaultTransitionService} consulting all four ports on every call
     */
    @Bean
    public TransitionService transitionService(
            @Lazy OntologyPort ontologyPort,
            @Lazy ObjectStorePort objectStorePort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy ExpressionPort expressionPort) {
        return new DefaultTransitionService(
            ontologyPort, objectStorePort, authorizationPort, expressionPort, Clock.systemUTC());
    }

    /**
     * Builds the {@link TriggerEvaluator} implementation an automation adapter calls back into once
     * an {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted} event, an elapsed timer,
     * or a signal might make an {@code OnChange}/{@code Timer}/{@code ExternalSignal}-triggered
     * transition available. Exactly the same reasoning as {@link #actionExecutor} applies here:
     * {@code TriggerEvaluator} is not consumed by a REST controller in this module, it is consumed
     * by whichever automation adapter is configured, and neither adapter module may depend on {@code
     * sequeless-core} — this bean is that unavoidable wiring seam.
     *
     * <p>{@link DefaultTriggerEvaluator} takes the already-built {@link TransitionService} bean
     * above (an ordinary intra-configuration reference, not a port, so it needs no {@code @Lazy})
     * rather than duplicating {@link DefaultTransitionService}'s own constructor dependencies — every
     * actual state move, including guard evaluation and the commit, is {@link
     * TransitionService#fireAutomated}'s job, not this class's.
     *
     * <p>Both port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param transitionService the {@link TransitionService} bean built above
     * @return a {@link DefaultTriggerEvaluator} consulting both ports and {@code transitionService}
     *     on every call
     */
    @Bean
    public TriggerEvaluator triggerEvaluator(
            @Lazy OntologyPort ontologyPort,
            @Lazy ObjectStorePort objectStorePort,
            TransitionService transitionService) {
        return new DefaultTriggerEvaluator(ontologyPort, objectStorePort, transitionService);
    }

    /**
     * Builds the {@link DerivationRecomputer} implementation an automation adapter calls back into
     * once an {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted} event might have
     * invalidated a materialised derived property. Exactly the same reasoning as {@link
     * #actionExecutor} and {@link #triggerEvaluator} applies here: {@code DerivationRecomputer} is
     * not consumed by a REST controller in this module, it is consumed by whichever automation
     * adapter is configured, and neither adapter module may depend on {@code sequeless-core} (the
     * {@code noAdapterDependsOnCore} architecture rule) — this bean is that unavoidable wiring seam.
     *
     * <p>{@link DefaultDerivationRecomputer} takes the {@link #derivationPlanner} bean built above
     * rather than constructing its own, so a recompute driven by a domain event resolves {@code
     * sq:Rollup} rules through the exact same code path {@link #businessObjectService}'s read/browse
     * path already uses.
     *
     * <p>Both port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above. {@code maxAttempts}/{@code baseDelay} are ordinary {@code
     * @Value}-annotated bean-method parameters, not port slots, so they need no such treatment; the
     * defaults (8 attempts, a 20ms jittered base delay) match this phase's design document.
     *
     * <p>No shared {@link Clock} bean exists in this application; see {@link #actionExecutor}'s
     * javadoc for why {@link Clock#systemUTC()} is called inline here too.
     *
     * @param ontologyPort the lazily-resolved ontology port adapter
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param derivationPlanner the {@link DerivationPlanner} bean built above
     * @param maxAttempts the maximum number of commit attempts per recompute before a stale-version
     *     conflict is allowed to propagate, from {@code
     *     sequeless.automation.recompute.retry.max-attempts}, defaulting to 8
     * @param baseDelayText the ISO-8601 base of the jittered retry backoff, from {@code
     *     sequeless.automation.recompute.retry.base-delay}, defaulting to 20ms. Bound as a plain
     *     {@link String} and parsed with {@link Duration#parse} in this method body, deliberately
     *     not as a {@code @Value}-injected {@link Duration} parameter directly: plain {@code
     *     @Value} type conversion goes through the bean factory's {@code TypeConverter}, which only
     *     knows how to parse a {@link Duration} from a string when Spring Boot's {@code
     *     ApplicationConversionService} has been installed as the context's conversion service —
     *     true for a real {@code SpringApplication} run, but not guaranteed for every {@code
     *     ApplicationContextRunner}-based test, which does not always go through that bootstrap
     *     path. Parsing the raw string here has no such dependency.
     * @return a {@link DefaultDerivationRecomputer} consulting both ports and {@code
     *     derivationPlanner} on every call
     */
    @Bean
    public DerivationRecomputer derivationRecomputer(
            @Lazy OntologyPort ontologyPort,
            @Lazy ObjectStorePort objectStorePort,
            DerivationPlanner derivationPlanner,
            @Value("${sequeless.automation.recompute.retry.max-attempts:8}") int maxAttempts,
            @Value("${sequeless.automation.recompute.retry.base-delay:PT0.02S}") String baseDelayText) {
        return new DefaultDerivationRecomputer(
            ontologyPort, objectStorePort, derivationPlanner, maxAttempts, Duration.parse(baseDelayText),
            Clock.systemUTC());
    }
}
