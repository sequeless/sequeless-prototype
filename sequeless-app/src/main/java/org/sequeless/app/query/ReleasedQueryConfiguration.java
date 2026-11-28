package org.sequeless.app.query;

import org.sequeless.query.api.QueryService;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.spi.query.ReadPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Binds the prototype's Query-side ports to the released query service. The bean names are distinct from the
 * released autoconfiguration's {@code browsePort}, {@code readPort} and {@code indexPreparationPort}, which are of the
 * released port types and would otherwise collide with these by name.
 *
 * <p>Each prototype port has exactly one bean, the adapter here. The released autoconfiguration's ports are of
 * other types, so they coexist with these and nothing injects them.
 */
@Configuration(proxyBeanMethods = false)
public class ReleasedQueryConfiguration {

    @Bean
    public BrowsePort releasedBrowsePortAdapter(@Lazy QueryService queryService, @Lazy FieldSource fieldSource) {
        return new ReleasedBrowsePortAdapter(queryService, fieldSource);
    }

    @Bean
    public ReadPort releasedReadPortAdapter(@Lazy QueryService queryService) {
        return new ReleasedReadPortAdapter(queryService);
    }

    @Bean
    public IndexPreparationPort releasedIndexPreparationPortAdapter(@Lazy QueryService queryService) {
        return new ReleasedIndexPreparationPortAdapter(queryService);
    }
}
