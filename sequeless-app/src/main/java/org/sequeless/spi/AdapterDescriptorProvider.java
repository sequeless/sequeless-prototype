package org.sequeless.spi;

/**
 * The {@link java.util.ServiceLoader} service type adapters register under {@code
 * META-INF/services/org.sequeless.spi.AdapterDescriptorProvider}, so the application can discover
 * every adapter on the classpath before any Spring context exists.
 *
 * <p>This indirection exists only because {@link AdapterDescriptor} is a {@code record}, and
 * {@code ServiceLoader} requires a service type it can instantiate reflectively via a no-arg
 * constructor or a {@code provider()} static factory — a record's canonical constructor takes
 * arguments and so cannot serve that role directly. Adapters therefore register a small class that
 * implements this interface and returns their {@code AdapterDescriptor}, rather than trying to
 * register the descriptor record itself as the service.
 */
public interface AdapterDescriptorProvider {

    /**
     * @return this adapter's self-description; must never return {@code null}
     */
    AdapterDescriptor descriptor();
}
