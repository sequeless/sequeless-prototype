package org.sequeless.core.api;

import java.util.Objects;
import org.sequeless.spi.Principal;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;

/**
 * The result of a successful {@link WhoAmI#whoAmI(org.sequeless.spi.Scope)} call.
 *
 * <p>This carries the SPI's own {@link AccessDecision} directly rather than duplicating its {@code
 * allowed}/{@code reason} fields as separate components: the decision that authorized this very
 * read is itself part of what {@code /whoami} reports, and re-shaping it here would just be a
 * lossy copy of a value the caller already understands.
 *
 * @param tenantId the tenant the reported scope belongs to; must not be {@code null}
 * @param principal the caller the reported scope was made on behalf of; must not be {@code null}
 * @param decision the {@link AuthorizationPort} decision that authorized this read; must not be
 *     {@code null}
 */
public record WhoAmIResult(TenantId tenantId, Principal principal, AccessDecision decision) {

    public WhoAmIResult {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
