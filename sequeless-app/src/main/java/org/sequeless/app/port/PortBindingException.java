package org.sequeless.app.port;

import java.util.List;
import java.util.stream.Collectors;
import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.SpiVersion;

/**
 * Thrown by {@link PortRegistry} when a configured port cannot be bound to exactly one compatible
 * adapter bean, for one of the reasons enumerated by this class's static factories.
 *
 * <p>Every instance carries both a {@link #getMessage() message} describing what went wrong and an
 * {@link #action()} describing what an operator should do about it. {@link
 * PortBindingFailureAnalyzer} surfaces both to the console via Spring Boot's failure-analysis
 * mechanism instead of a raw stack trace, so this exception's messages are themselves the
 * user-facing deliverable — not an implementation detail. Construct instances only through the
 * static factories below; they guarantee the message and action stay in sync with each other and
 * with what {@link PortRegistry} actually observed.
 */
public final class PortBindingException extends RuntimeException {

    private final String action;

    private PortBindingException(String message, String action) {
        super(message);
        this.action = action;
    }

    /**
     * @return a human-readable next step an operator can take to resolve this failure
     */
    public String action() {
        return action;
    }

    /**
     * @param definition the port whose selecting property was not set
     * @param available every adapter descriptor discovered for {@link
     *     PortDefinition#portType()}
     * @return an exception reporting that {@link PortDefinition#property()} is unset
     */
    public static PortBindingException propertyNotSet(
            PortDefinition definition, List<AdapterDescriptor> available) {
        String names = joinNames(available);
        String message =
            "Property '%s' is not set; it must select one of the available %s adapters: [%s]"
                .formatted(definition.property(), portTypeName(definition), names);
        return new PortBindingException(message, setPropertyAction(definition, names));
    }

    /**
     * @param configuredValue the value the property was actually set to
     * @param definition the port whose selecting property named an unknown adapter
     * @param available every adapter descriptor discovered for {@link
     *     PortDefinition#portType()}
     * @return an exception reporting that {@code configuredValue} names no known adapter
     */
    public static PortBindingException unknownAdapter(
            String configuredValue, PortDefinition definition, List<AdapterDescriptor> available) {
        String names = joinNames(available);
        String message =
            "Property '%s' is set to '%s', which is not a known %s adapter. Available adapters: [%s]"
                .formatted(definition.property(), configuredValue, portTypeName(definition), names);
        return new PortBindingException(message, setPropertyAction(definition, names));
    }

    /**
     * @param descriptor the selected adapter's descriptor, whose {@link
     *     AdapterDescriptor#spiVersionRange()} the running SPI does not satisfy
     * @param definition the port the adapter was selected for
     * @return an exception reporting the SPI version mismatch
     */
    public static PortBindingException incompatibleVersion(
            AdapterDescriptor descriptor, PortDefinition definition) {
        String message =
            "Adapter '%s' for property '%s' requires SPI '%s', but this application runs SPI %s"
                .formatted(
                    descriptor.name(), definition.property(), descriptor.spiVersionRange(),
                    SpiVersion.VERSION);
        String action =
            "Upgrade sequeless-spi or use a build of '%s' compatible with SPI %s"
                .formatted(descriptor.name(), SpiVersion.VERSION);
        return new PortBindingException(message, action);
    }

    /**
     * @param descriptor the selected adapter's descriptor
     * @param definition the port for which no bean was found in the application context
     * @return an exception reporting the missing bean
     */
    public static PortBindingException noBeanFound(AdapterDescriptor descriptor, PortDefinition definition) {
        String message =
            "Property '%s' selects adapter '%s', but no %s bean was found in the application context"
                .formatted(definition.property(), descriptor.name(), portTypeName(definition));
        String action =
            "Ensure the adapter's auto-configuration is on the classpath and its "
                + "@ConditionalOnProperty condition matches";
        return new PortBindingException(message, action);
    }

    /**
     * @param descriptor the selected adapter's descriptor
     * @param definition the port for which more than one bean was found
     * @param beanNames the names of every conflicting bean found in the application context
     * @return an exception reporting the bean cardinality conflict
     */
    public static PortBindingException multipleBeansFound(
            AdapterDescriptor descriptor, PortDefinition definition, String[] beanNames) {
        String message =
            "Property '%s' selects adapter '%s', but %d %s beans were found: [%s]"
                .formatted(
                    definition.property(), descriptor.name(), beanNames.length,
                    portTypeName(definition), String.join(", ", beanNames));
        String action =
            "Remove the extra %s bean(s) so exactly one adapter is wired".formatted(portTypeName(definition));
        return new PortBindingException(message, action);
    }

    private static String portTypeName(PortDefinition definition) {
        return definition.portType().getSimpleName();
    }

    private static String setPropertyAction(PortDefinition definition, String names) {
        return "Set '%s' to one of: %s".formatted(definition.property(), names);
    }

    private static String joinNames(List<AdapterDescriptor> descriptors) {
        return descriptors.stream().map(AdapterDescriptor::name).collect(Collectors.joining(", "));
    }
}
