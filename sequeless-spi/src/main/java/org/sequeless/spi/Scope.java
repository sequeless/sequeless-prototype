package org.sequeless.spi;

import java.util.Objects;

/**
 * The tenant and principal a single request is scoped to. This is the one piece of context every
 * outbound port needs in order to answer "on behalf of whom, and in which tenant" — bundling the
 * two together means a port signature never has to grow a third or fourth context parameter as new
 * ports are added; it just takes a {@code Scope}.
 *
 * @param tenantId the tenant the request belongs to; must not be {@code null}
 * @param principal the caller the request is made on behalf of; must not be {@code null}
 */
public record Scope(TenantId tenantId, Principal principal) {

    public Scope {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
    }
}
