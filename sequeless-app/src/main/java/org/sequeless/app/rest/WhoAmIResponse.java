package org.sequeless.app.rest;

import java.util.Set;
import org.sequeless.core.api.WhoAmIResult;

/**
 * JSON response body for {@code GET /whoami}.
 *
 * <p>This is a transport-layer shape, deliberately distinct from {@link WhoAmIResult}: it exists so
 * a future change to the wire format (renaming a field, restructuring the decision) never forces a
 * change to sequeless-core's own result type, and vice versa. {@link #from(WhoAmIResult)} is the
 * single place that maps between the two.
 *
 * @param tenant the tenant identifier the request was scoped to
 * @param principal the caller's identifier
 * @param displayName the caller's human-readable name
 * @param roles the caller's roles
 * @param decision the authorization decision that authorized this read
 */
public record WhoAmIResponse(
        String tenant, String principal, String displayName, Set<String> roles, Decision decision) {

    /**
     * The authorization outcome, reported alongside the identity it was made for.
     *
     * @param allowed whether the operation was permitted
     * @param reason the port's explanation for the decision
     */
    public record Decision(boolean allowed, String reason) {}

    /**
     * Maps a core {@link WhoAmIResult} onto its wire representation.
     *
     * @param result the use case result to map; must not be {@code null}
     * @return the equivalent {@link WhoAmIResponse}
     */
    public static WhoAmIResponse from(WhoAmIResult result) {
        return new WhoAmIResponse(
                result.tenantId().value(),
                result.principal().id(),
                result.principal().displayName(),
                result.principal().roles(),
                new Decision(result.decision().allowed(), result.decision().reason()));
    }
}
