package org.sequeless.spi.ontology;

import java.util.Objects;

/**
 * Thrown by {@link OntologyPort#snapshot} and {@link OntologyPort#reload} when the ontology they
 * would otherwise return is inconsistent, carrying the {@link OntologyReport} that explains why.
 *
 * <p>This is deliberately unchecked, for the same reason {@code
 * org.sequeless.core.AuthorizationException} is: inconsistency is a condition every intermediate
 * signature up the call chain should not have to declare, and the REST boundary (or any other
 * inbound adapter) is expected to catch it once, at the edge — here, to produce an HTTP 422 body
 * from {@link OntologyReport#describe()}.
 *
 * <p>Unlike {@code AuthorizationException}, which lives in {@code sequeless-core}, this exception
 * lives in the SPI. That is deliberate: {@link OntologyPort}'s own contract javadoc specifies that
 * {@code snapshot()} and {@code reload()} throw {@code OntologyException} on inconsistency, so the
 * SPI must define the exception its own interface promises to throw, the same way {@link
 * org.sequeless.spi.authz.AccessDecision} is SPI-level because it is a port return type. {@code
 * AuthorizationPort} by contrast never throws — denial is a return value a core use case escalates
 * into {@code AuthorizationException}, which is why that exception belongs to core instead.
 */
public final class OntologyException extends RuntimeException {

    private final OntologyReport report;

    /**
     * @param report the report explaining the inconsistency this exception carries; must not be
     *     {@code null} and must not be {@link OntologyReport#consistent() consistent}
     * @throws NullPointerException if {@code report} is {@code null}
     * @throws IllegalArgumentException if {@code report} is {@link OntologyReport#consistent()
     *     consistent} — this exception exists to carry an inconsistency, not a clean report
     */
    public OntologyException(OntologyReport report) {
        super(Objects.requireNonNull(report, "report must not be null").describe());
        if (report.consistent()) {
            throw new IllegalArgumentException("report must not be consistent");
        }
        this.report = report;
    }

    /**
     * @return the report explaining the inconsistency that caused this exception
     */
    public OntologyReport report() {
        return report;
    }
}
