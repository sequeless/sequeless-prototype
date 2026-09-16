package org.sequeless.spi;

import java.util.Objects;
import java.util.Set;

/**
 * The authenticated (or anonymous) caller a request is made on behalf of. Ports and use cases see
 * only this shape — never a framework-specific principal type — so adapters for different identity
 * providers can all populate the same SPI value.
 *
 * <p>{@code roles} is defensively copied into an unmodifiable set in the compact constructor: Java
 * records do <em>not</em> do this automatically, and without it a caller could hand in a mutable
 * {@code HashSet} and continue mutating it after construction, silently changing a {@code
 * Principal} that downstream code (an authorization decision, an audit log entry) had already
 * treated as immutable. {@link Set#copyOf(java.util.Collection)} also rejects {@code null} roles
 * for free.
 *
 * @param id unique identifier for the principal; must not be blank
 * @param displayName human-readable name for the principal; must not be blank
 * @param roles the principal's roles; must not be {@code null} (individual role strings are not
 *     further validated — role vocabulary is a concern for the authorization adapter, not this SPI)
 */
public record Principal(String id, String displayName, Set<String> roles) {

    /** The unauthenticated caller, used wherever no identity provider has run. */
    public static final Principal ANONYMOUS = new Principal("anonymous", "Anonymous", Set.of());

    public Principal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Principal id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Principal displayName must not be blank");
        }
        Objects.requireNonNull(roles, "roles must not be null");
        roles = Set.copyOf(roles);
    }
}
