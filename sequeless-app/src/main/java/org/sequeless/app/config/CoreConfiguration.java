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
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.object.ObjectStorePort;
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
 *
 * <p>The ontology adapter is one bean that implements the three read ports and the admin port. The
 * use cases take the read ports they need. The admin port is wrapped in {@link
 * IndexPreparingOntologyAdministration} before it reaches {@link #metaModelService} and {@link
 * #ontologyAdministration}, so import and reload prepare query indexes. The wrapper is not a bean
 * of its own, because a second {@code OntologyAdministration} bean would break {@link
 * org.sequeless.app.port.PortRegistry}'s one-bean check.
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
     * Builds the {@link MetaModelService} use case around the schema and behaviour read ports, the
     * ontology admin port (wrapped in {@link IndexPreparingOntologyAdministration}, so {@code
     * reload} prepares indexes) and the {@link AuthorizationPort} adapter that are configured.
     *
     * <p><b>Every parameter is {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above, and none may be "simplified" away.</b> {@code
     * PortRegistry} validates the {@code sequeless.ontology.adapter} slot in {@code
     * afterSingletonsInstantiated()}, the second singleton pass. An eager parameter here would force
     * Spring to resolve the ontology adapter during {@code preInstantiateSingletons()}, the first
     * pass, where an unset or misconfigured {@code sequeless.ontology.adapter} means zero ontology
     * beans exist and Spring's own generic {@code UnsatisfiedDependencyException} would fire before
     * {@code PortRegistry} ever ran.
     *
     * <p>Nothing dereferences these ports during startup on its own, so an inconsistent ontology
     * would not fail startup from here. {@link OntologyStartupValidator} exists to force that first
     * read at a point in the lifecycle where it is safe — see its javadoc.
     *
     * @param schemaSource the lazily-resolved schema read port
     * @param behaviourSource the lazily-resolved behaviour read port
     * @param ontologyAdministration the lazily-resolved ontology admin port, the same bean the read
     *     ports come from
     * @param queryPort the lazily-resolved query port, whose indexes {@code reload} prepares
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @return a {@link DefaultMetaModelService} consulting the ports on every call
     */
    @Bean
    public MetaModelService metaModelService(
            @Lazy SchemaSource schemaSource,
            @Lazy BehaviourSource behaviourSource,
            @Lazy org.sequeless.spi.ontology.OntologyAdministration ontologyAdministration,
            @Lazy QueryPort queryPort,
            @Lazy AuthorizationPort authorizationPort) {
        return new DefaultMetaModelService(
                schemaSource,
                behaviourSource,
                new IndexPreparingOntologyAdministration(ontologyAdministration, schemaSource, queryPort),
                authorizationPort);
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
     * Builds the {@link BusinessObjectService} use case around whichever schema and behaviour read
     * ports, {@link ObjectStorePort}, {@link ValidationPort}, {@link AuthorizationPort}, and {@link
     * QueryPort} adapters are configured.
     *
     * <p><b>All port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above, and none may be "simplified" away.</b> {@code
     * PortRegistry} validates its port slots in {@code afterSingletonsInstantiated()}, the second
     * singleton pass. An eager parameter here would force Spring to resolve a port during {@code
     * preInstantiateSingletons()}, the first pass, before {@code PortRegistry} ever ran.
     *
     * @param schemaSource the lazily-resolved schema read port
     * @param behaviourSource the lazily-resolved behaviour read port
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param validationPort the lazily-resolved validation port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @param queryPort the lazily-resolved query port adapter
     * @param derivationPlanner the {@link DerivationPlanner} bean built above, shared with {@link
     *     #derivationRecomputer}
     * @return a {@link DefaultBusinessObjectService} consulting all of its ports on every call, with
     *     its clock drawn from {@link Clock#systemUTC()}
     */
    @Bean
    public BusinessObjectService businessObjectService(
            @Lazy SchemaSource schemaSource,
            @Lazy BehaviourSource behaviourSource,
            @Lazy ObjectStorePort objectStorePort,
            @Lazy ValidationPort validationPort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy QueryPort queryPort,
            DerivationPlanner derivationPlanner) {
        return new DefaultBusinessObjectService(
                schemaSource,
                behaviourSource,
                objectStorePort,
                validationPort,
                authorizationPort,
                queryPort,
                Clock.systemUTC(),
                derivationPlanner);
    }

    /**
     * Builds the {@link OntologyAdministration} use case around the ontology admin port (wrapped in
     * {@link IndexPreparingOntologyAdministration}, so {@code importTurtle} prepares indexes) and the
     * {@link AuthorizationPort} adapter that are configured.
     *
     * <p>All parameters are {@code @Lazy} for the same reason as {@link #metaModelService}.
     *
     * @param ontologyAdministration the lazily-resolved ontology admin port
     * @param schemaSource the lazily-resolved schema read port, used to read the imported Schema
     *     when indexes are prepared
     * @param queryPort the lazily-resolved query port, whose indexes are prepared after a successful
     *     import
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @return a {@link DefaultOntologyAdministration} consulting {@code authorizationPort} on every
     *     call
     */
    @Bean
    public OntologyAdministration ontologyAdministration(
            @Lazy org.sequeless.spi.ontology.OntologyAdministration ontologyAdministration,
            @Lazy SchemaSource schemaSource,
            @Lazy QueryPort queryPort,
            @Lazy AuthorizationPort authorizationPort) {
        return new DefaultOntologyAdministration(
                new IndexPreparingOntologyAdministration(ontologyAdministration, schemaSource, queryPort),
                authorizationPort);
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
    public ActionExecutor actionExecutor(@Lazy ExpressionPort expressionPort, @Lazy ObjectStorePort objectStorePort) {
        return new DefaultActionExecutor(expressionPort, objectStorePort, Clock.systemUTC());
    }

    /**
     * Builds the {@link TransitionService} use case around whichever schema, behaviour and
     * automation read ports, {@link ObjectStorePort}, {@link AuthorizationPort}, and {@link
     * ExpressionPort} adapters are configured.
     *
     * <p>All port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above, and none may be "simplified" away.
     *
     * <p>No shared {@link Clock} bean exists in this application; see {@link #actionExecutor}'s
     * javadoc for why {@link Clock#systemUTC()} is called inline here too.
     *
     * @param schemaSource the lazily-resolved schema read port
     * @param behaviourSource the lazily-resolved behaviour read port
     * @param automationSource the lazily-resolved automation read port
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param authorizationPort the lazily-resolved authorization port adapter
     * @param expressionPort the lazily-resolved expression port adapter
     * @return a {@link DefaultTransitionService} consulting all of its ports on every call
     */
    @Bean
    public TransitionService transitionService(
            @Lazy SchemaSource schemaSource,
            @Lazy BehaviourSource behaviourSource,
            @Lazy AutomationSource automationSource,
            @Lazy ObjectStorePort objectStorePort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy ExpressionPort expressionPort) {
        return new DefaultTransitionService(
                schemaSource,
                behaviourSource,
                automationSource,
                objectStorePort,
                authorizationPort,
                expressionPort,
                Clock.systemUTC());
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
     * <p>The port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above.
     *
     * @param schemaSource the lazily-resolved schema read port
     * @param behaviourSource the lazily-resolved behaviour read port
     * @param objectStorePort the lazily-resolved object store port adapter
     * @param transitionService the {@link TransitionService} bean built above
     * @return a {@link DefaultTriggerEvaluator} consulting its ports and {@code transitionService}
     *     on every call
     */
    @Bean
    public TriggerEvaluator triggerEvaluator(
            @Lazy SchemaSource schemaSource,
            @Lazy BehaviourSource behaviourSource,
            @Lazy ObjectStorePort objectStorePort,
            TransitionService transitionService) {
        return new DefaultTriggerEvaluator(schemaSource, behaviourSource, objectStorePort, transitionService);
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
     * <p>The port-typed parameters are {@code @Lazy} for exactly the reason spelled out on {@link
     * #whoAmI(AuthorizationPort)} above. {@code maxAttempts}/{@code baseDelay} are ordinary {@code
     * @Value}-annotated bean-method parameters, not port slots, so they need no such treatment; the
     * defaults (8 attempts, a 20ms jittered base delay) match this phase's design document.
     *
     * <p>No shared {@link Clock} bean exists in this application; see {@link #actionExecutor}'s
     * javadoc for why {@link Clock#systemUTC()} is called inline here too.
     *
     * @param schemaSource the lazily-resolved schema read port
     * @param behaviourSource the lazily-resolved behaviour read port
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
     * @return a {@link DefaultDerivationRecomputer} consulting its ports and {@code
     *     derivationPlanner} on every call
     */
    @Bean
    public DerivationRecomputer derivationRecomputer(
            @Lazy SchemaSource schemaSource,
            @Lazy BehaviourSource behaviourSource,
            @Lazy ObjectStorePort objectStorePort,
            DerivationPlanner derivationPlanner,
            @Value("${sequeless.automation.recompute.retry.max-attempts:8}") int maxAttempts,
            @Value("${sequeless.automation.recompute.retry.base-delay:PT0.02S}") String baseDelayText) {
        return new DefaultDerivationRecomputer(
                schemaSource,
                behaviourSource,
                objectStorePort,
                derivationPlanner,
                maxAttempts,
                Duration.parse(baseDelayText),
                Clock.systemUTC());
    }
}
