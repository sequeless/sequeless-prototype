package org.sequeless.app.port;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.SpiVersion;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

/**
 * Fail-fast validator for every configured port slot in the application: for each {@link
 * PortDefinition}, confirms the selecting property is set, names a known adapter, that adapter is
 * compatible with the running SPI, and exactly one matching bean exists in the application
 * context — throwing a clear, actionable {@link PortBindingException} the moment any check fails.
 *
 * <p><b>Why {@link SmartInitializingSingleton} and not another lifecycle hook.</b> {@code
 * InitializingBean} runs per-bean during {@code preInstantiateSingletons()} — the very pass whose
 * ordering hazard {@code CoreConfiguration}'s {@code @Lazy} injection exists to dodge — so using it
 * here would reintroduce that hazard for this bean's own construction. {@code
 * ApplicationListener<ContextRefreshedEvent>} runs only after Spring has already reported the
 * context refreshed, which is too late to fail startup cleanly. {@link
 * SmartInitializingSingleton#afterSingletonsInstantiated()} runs in a dedicated second pass, after
 * every singleton (including the {@code @Lazy} proxy in {@code CoreConfiguration.whoAmI}) is
 * already constructed, which is exactly when counting real {@link
 * org.sequeless.spi.authz.AuthorizationPort} beans becomes meaningful.
 *
 * <p>Bean counting uses {@link ListableBeanFactory#getBeanNamesForType(Class, boolean, boolean)}
 * with {@code allowEagerInit = false}, so counting itself can never instantiate a bean — this
 * matters because dereferencing the {@code @Lazy} proxy this early would defeat the very ordering
 * guarantee that proxy exists for.
 */
public class PortRegistry implements SmartInitializingSingleton, BeanFactoryAware {

    private final Environment environment;
    private final List<PortDefinition> definitions;
    private ListableBeanFactory beanFactory;

    /**
     * @param environment source of each {@link PortDefinition#property()}'s configured value; must
     *     not be {@code null}
     * @param definitions every port slot to validate; must not be {@code null}
     */
    public PortRegistry(Environment environment, List<PortDefinition> definitions) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions must not be null"));
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ListableBeanFactory listableBeanFactory)) {
            throw new IllegalStateException(
                "PortRegistry requires a ListableBeanFactory, but got " + beanFactory.getClass());
        }
        this.beanFactory = listableBeanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        definitions.forEach(this::validate);
    }

    private void validate(PortDefinition definition) {
        List<AdapterDescriptor> available = discoverDescriptors(definition.portType());

        String configuredValue = environment.getProperty(definition.property());
        if (configuredValue == null || configuredValue.isBlank()) {
            throw PortBindingException.propertyNotSet(definition, available);
        }

        AdapterDescriptor descriptor =
            available.stream()
                .filter(candidate -> candidate.name().equals(configuredValue))
                .findFirst()
                .orElseThrow(
                    () -> PortBindingException.unknownAdapter(configuredValue, definition, available));

        if (!SpiVersion.isCompatibleWith(descriptor.spiVersionRange())) {
            throw PortBindingException.incompatibleVersion(descriptor, definition);
        }

        String[] beanNames = beanFactory.getBeanNamesForType(definition.portType(), true, false);
        if (beanNames.length == 0) {
            throw PortBindingException.noBeanFound(descriptor, definition);
        }
        if (beanNames.length > 1) {
            throw PortBindingException.multipleBeansFound(descriptor, definition, beanNames);
        }
    }

    private static List<AdapterDescriptor> discoverDescriptors(Class<?> portType) {
        return ServiceLoader.load(AdapterDescriptorProvider.class).stream()
            .map(ServiceLoader.Provider::get)
            .map(AdapterDescriptorProvider::descriptor)
            .filter(descriptor -> descriptor.port().equals(portType))
            .toList();
    }
}
