package org.sequeless.spi.derivation;

import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.query.QueryPort;

/**
 * The context a {@link DerivationPlugin} is invoked with: the tenant/principal the request is made
 * on behalf of, the type system in effect, and port access for a plug-in that needs to look up
 * related objects itself. {@code ServiceLoader} instantiates a {@link DerivationPlugin} via a
 * no-arg constructor, so this record is the only way a plug-in receives request-scoped state —
 * there is no mutable init step it could otherwise be handed through.
 *
 * @param scope the tenant and principal the request is made on behalf of; must not be {@code null}
 * @param snapshot the type system the request is interpreted against; must not be {@code null}
 * @param queryPort the port a plug-in may use to issue its own aggregate or query calls; must not
 *     be {@code null}
 */
public record DerivationContext(Scope scope, MetaModelSnapshot snapshot, QueryPort queryPort) {

    public DerivationContext {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(queryPort, "queryPort must not be null");
    }
}
