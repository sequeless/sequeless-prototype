package org.sequeless.app.config;

import java.time.Clock;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.core.api.OntologyAdministration;
import org.sequeless.core.api.WhoAmI;
import org.sequeless.core.usecase.DefaultBusinessObjectService;
import org.sequeless.core.usecase.DefaultMetaModelService;
import org.sequeless.core.usecase.DefaultOntologyAdministration;
import org.sequeless.core.usecase.DefaultWhoAmI;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.validation.ValidationPort;
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
     * @return a {@link DefaultBusinessObjectService} consulting all five ports on every call, with
     *     its clock drawn from {@link Clock#systemUTC()}
     */
    @Bean
    public BusinessObjectService businessObjectService(
            @Lazy OntologyPort ontologyPort,
            @Lazy ObjectStorePort objectStorePort,
            @Lazy ValidationPort validationPort,
            @Lazy AuthorizationPort authorizationPort,
            @Lazy QueryPort queryPort) {
        return new DefaultBusinessObjectService(
            ontologyPort, objectStorePort, validationPort, authorizationPort, queryPort,
            Clock.systemUTC());
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
}
