/**
 * The mechanical contract test for {@link org.sequeless.spi.object.ObjectStorePort} and its
 * companion {@link org.sequeless.spi.object.OntologyDocumentStore}.
 *
 * <p>{@link org.sequeless.testkit.object.ObjectStoreContract} asserts every clause of the
 * behavioural contract documented on {@code ObjectStorePort} itself, against any implementation
 * supplied by a subclass via {@code freshStore()}. It is deliberately ontology-agnostic — every
 * fixture object it builds uses arbitrary {@code TypeRef}/{@code PropertyRef} IRIs, never the
 * reference-domain ontology from {@code Fixtures} — and asserts only null-safety, tenant isolation,
 * optimistic-locking and soft-delete semantics, paging behaviour, and outbox atomicity, never
 * anything specific to a particular storage technology. This module's own {@code
 * InMemoryObjectStorePortContractTest} (in {@code src/test/java}) is the standing proof that a
 * genuinely simple, non-relational implementation can pass it unmodified.
 */
package org.sequeless.testkit.object;
