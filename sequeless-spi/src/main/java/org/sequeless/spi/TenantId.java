package org.sequeless.spi;

/**
 * Identifies the tenant a request is scoped to. Sequeless is single-tenant in Phase 0 — every
 * request uses {@link #DEFAULT} — but the type exists from the start so ports, use cases, and
 * adapters never need to be retrofitted for multi-tenancy: they already carry a {@code TenantId}
 * through every call that needs one, via {@link Scope}.
 *
 * @param value the tenant identifier; must not be blank
 */
public record TenantId(String value) {

    /**
     * The single tenant Phase 0 operates under. {@code /whoami} and other single-tenant call sites
     * must read the default tenant from here rather than hardcoding the literal {@code "default"}
     * elsewhere, so a future multi-tenant change has exactly one place to touch.
     */
    public static final TenantId DEFAULT = new TenantId("default");

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TenantId value must not be blank");
        }
    }
}
