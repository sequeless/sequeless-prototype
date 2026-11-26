/**
 * The outbound ports of the Store: the interfaces a Store adapter implements and the Store core
 * calls through. Spec 18 renames these types from {@code org.sequeless.spi.*} into this package
 * (decision Q7, DR-30) before the Store moves to its own repository, so the released Store
 * contract is published under the {@code org.sequeless.store} family root.
 *
 * <p>The ports are {@link org.sequeless.store.spi.ObjectStorePort}, {@link
 * org.sequeless.store.spi.SearchVectorBackfillPort}, {@link org.sequeless.store.spi.OutboxPort},
 * {@link org.sequeless.store.spi.AuthorizationPort}, {@link org.sequeless.store.spi.ValidationPort},
 * {@link org.sequeless.store.spi.AutomationFailureRecorder}, {@link
 * org.sequeless.store.spi.DerivationPlugin} with its {@link org.sequeless.store.spi.DerivationContext},
 * and {@link org.sequeless.store.spi.QueryPort}. Their value vocabulary stays with its family
 * ({@code org.sequeless.spi.object}, {@code org.sequeless.spi.outbox}, {@code org.sequeless.spi.query}
 * and so on) until those types move too. {@link org.sequeless.store.spi.Violation} and {@link
 * org.sequeless.store.spi.AutomationFailure} are the result vocabulary of two of these ports, and
 * {@link org.sequeless.store.spi.Criterion}, {@link org.sequeless.store.spi.Operator}, {@link
 * org.sequeless.store.spi.AggregateRequest} and {@link org.sequeless.store.spi.AggregateResult} are
 * the aggregate query types that {@link org.sequeless.store.spi.QueryPort} takes and returns.
 *
 * <p>The package keeps the rule the rest of {@code org.sequeless.spi} keeps: it imports nothing
 * outside the JDK, the released sequeless-kernel and ontology contracts, and the remaining
 * {@code org.sequeless.spi.*} value packages. The ArchUnit rules in {@code sequeless-app} cover
 * {@code org.sequeless.store.spi..} alongside {@code org.sequeless.spi..}.
 */
package org.sequeless.store.spi;
