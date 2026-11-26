/**
 * The Store's inbound Command vocabulary: the authorization request and response types the
 * Store's authorization port is called with ({@link org.sequeless.store.api.Operation} and {@link
 * org.sequeless.store.api.AccessDecision}), and the derivation recompute port that the Store core
 * implements and the host's outbox relay calls ({@link org.sequeless.store.api.DerivationRecomputer}).
 *
 * <p>Spec 18 moves these types here from {@code org.sequeless.spi.authz} and {@code
 * org.sequeless.spi.automation} (decision Q7 and Q12, DR-30). The outbound ports the Store core
 * calls and its adapters implement stay in {@link org.sequeless.store.spi}, and {@link
 * org.sequeless.store.spi.AuthorizationPort} is the port that takes {@link
 * org.sequeless.store.api.Operation} and returns {@link org.sequeless.store.api.AccessDecision}.
 *
 * <p>Like every package beneath {@code org.sequeless.store} and {@code org.sequeless.spi}, this package
 * imports nothing outside the JDK, the released sequeless-kernel and ontology contracts, and the
 * {@code org.sequeless.spi} and {@code org.sequeless.store.spi} packages. The ArchUnit rules in
 * {@code sequeless-app} cover {@code org.sequeless.store.api..}.
 */
package org.sequeless.store.api;
