package org.sequeless.testkit;

import java.util.Set;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;

/**
 * Shared, port-agnostic test data for every contract in this testkit. This class lives at the top
 * level of {@code org.sequeless.testkit} — not under a per-port subpackage such as {@code
 * org.sequeless.testkit.authz} — precisely because later contracts (an {@code ObjectStoreContract},
 * a {@code QueryContract}) need the same tenant, principal, and scope building blocks that {@link
 * org.sequeless.testkit.authz.AuthorizationContract} needs, and none of that data is specific to
 * authorization.
 */
public final class Fixtures {

    /**
     * An opaque resource identifier with no special meaning to any port — distinct from {@link
     * org.sequeless.spi.authz.AuthorizationPort#EVERYTHING}, so a contract can assert that
     * implementations accept arbitrary resource strings and not just the sentinel.
     */
    public static final String OPAQUE_RESOURCE = "sq:SampleType/1";

    private Fixtures() {}

    /**
     * @return a {@link Scope} for the default tenant and the anonymous principal, suitable
     *     wherever a test needs a valid scope but does not care about tenant or identity
     */
    public static Scope defaultScope() {
        return new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    }

    /**
     * @param principalId the id (and display name) to give the principal; must not be blank
     * @param roles the roles to assign the principal
     * @return a {@link Scope} for the default tenant and a principal identified by {@code
     *     principalId} carrying {@code roles}
     */
    public static Scope scope(String principalId, String... roles) {
        return new Scope(TenantId.DEFAULT, new Principal(principalId, principalId, Set.of(roles)));
    }
}
