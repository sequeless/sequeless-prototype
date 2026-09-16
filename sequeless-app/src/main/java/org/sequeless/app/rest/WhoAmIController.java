package org.sequeless.app.rest;

import java.util.Objects;
import org.sequeless.core.api.WhoAmI;
import org.sequeless.core.api.WhoAmIResult;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@link WhoAmI} over HTTP as {@code GET /whoami}.
 *
 * <p>Phase 0 has no inbound identity provider wired up yet, so every request is reported against
 * {@link TenantId#DEFAULT} and {@link Principal#ANONYMOUS} — never against the literal strings
 * {@code "default"} or {@code "anonymous"}, so the single-tenant, unauthenticated assumption has
 * exactly one place (the SPI constants themselves) to change later.
 */
@RestController
public class WhoAmIController {

    private final WhoAmI whoAmI;

    /**
     * @param whoAmI the use case this controller delegates to; must not be {@code null}
     * @throws NullPointerException if {@code whoAmI} is {@code null}
     */
    public WhoAmIController(WhoAmI whoAmI) {
        this.whoAmI = Objects.requireNonNull(whoAmI, "whoAmI must not be null");
    }

    /**
     * @return the caller's tenant, principal, and authorization decision, as reported by {@link
     *     WhoAmI}
     */
    @GetMapping("/whoami")
    public WhoAmIResponse whoAmi() {
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
        WhoAmIResult result = whoAmI.whoAmI(scope);
        return WhoAmIResponse.from(result);
    }
}
