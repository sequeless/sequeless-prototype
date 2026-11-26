package org.sequeless.app.automation;

import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.TransitionService;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Registers the host's bindings of the released automation ports (spec 17, section 3).
 *
 * <p>The released {@code automation-spring-boot-autoconfig} builds {@code ActionExecutor} and {@code TriggerEvaluator}
 * over these two beans. {@code AutomationCoreAutoConfiguration} gates each of them on the beans being present, so
 * this class must register them.
 *
 * <p>The behaviour port is {@code @Lazy} for the reason {@code CoreConfiguration} gives: an eager port would be resolved
 * in the first singleton pass. The bindings never import {@code ObjectStorePort}: the id-to-type lookup is
 * {@code BusinessObjectService#typeOf}.
 */
@Configuration(proxyBeanMethods = false)
public class AutomationBindingConfiguration {

    /**
     * @param businessObjectService the Command use case that serves automation reads and resolves types
     * @return the host's {@link ObjectReadPort}
     */
    @Bean
    public ObjectReadPort objectReadPort(BusinessObjectService businessObjectService) {
        return new BusinessObjectReadPort(new ObjectTypes(businessObjectService), businessObjectService);
    }

    /**
     * @param businessObjectService the Command use case that serves automation creates and property sets, and resolves
     *     types
     * @param transitionService the Command use case that fires automated transitions
     * @param behaviourSource the behaviour the derived properties are read from; lazily resolved
     * @param schemaSource the schema the declared cardinalities are read from, for the value shaping; lazily resolved
     * @return the host's {@link StoreCommandPort}
     */
    @Bean
    public StoreCommandPort storeCommandPort(
            BusinessObjectService businessObjectService,
            TransitionService transitionService,
            @Lazy BehaviourSource behaviourSource,
            @Lazy SchemaSource schemaSource) {
        return new BusinessObjectStoreCommandPort(
                new ObjectTypes(businessObjectService),
                businessObjectService,
                transitionService,
                behaviourSource,
                new ShapeCoercion(schemaSource));
    }
}
