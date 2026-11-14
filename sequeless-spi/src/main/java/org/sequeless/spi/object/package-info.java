/**
 * The business-object vocabulary: immutable value types for storing and mutating instances of
 * ontology types, independent of any RDF library, JSON library, or SQL dialect.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except {@link org.sequeless.kernel.identity.TenantId} from its parent package and
 * sibling types within this package. {@link org.sequeless.spi.object.BusinessObject} is the
 * aggregate root: an {@link org.sequeless.kernel.values.ObjectId}, a {@link
 * org.sequeless.spi.object.TypeRef}, an optimistic-locking version, an optional state, a map of
 * {@link org.sequeless.spi.object.PropertyRef} to {@link org.sequeless.kernel.values.Value}, and an
 * {@link org.sequeless.spi.object.Audit} trail. {@link org.sequeless.kernel.values.Value} is sealed to
 * {@link org.sequeless.kernel.values.TextValue}, {@link org.sequeless.kernel.values.IntegerValue}, {@link
 * org.sequeless.kernel.values.DecimalValue}, {@link org.sequeless.kernel.values.BoolValue}, {@link
 * org.sequeless.kernel.values.DateTimeValue}, {@link org.sequeless.kernel.values.DateValue}, {@link
 * org.sequeless.kernel.values.ReferenceValue}, and {@link org.sequeless.kernel.values.ListValue}.
 *
 * <p>{@link org.sequeless.spi.object.Mutation}, sealed to {@link org.sequeless.spi.object.Create},
 * {@link org.sequeless.spi.object.Update}, and {@link org.sequeless.spi.object.Delete}, together
 * with {@link org.sequeless.spi.outbox.OutboxEntry} form a {@link
 * org.sequeless.spi.object.ChangeSet}: the unit of atomic change an {@link
 * org.sequeless.spi.object.ObjectStorePort#commit} call applies, producing a {@link
 * org.sequeless.spi.object.CommitResult}. {@link org.sequeless.spi.object.Page} and {@link
 * org.sequeless.spi.object.PageResult} are the paging vocabulary: a {@link
 * org.sequeless.spi.object.Page} is the request for one page, and a {@link
 * org.sequeless.spi.object.PageResult} is the page that answers it. {@link
 * org.sequeless.spi.object.StaleObjectException} and {@link
 * org.sequeless.kernel.values.ObjectNotFoundException} are the unchecked exceptions {@code commit}
 * throws when a mutation's optimistic-locking expectation is not met or its target does not exist.
 *
 * <p>The {@link org.sequeless.spi.object.ObjectStorePort} outbound port interface (together with
 * {@link org.sequeless.ontology.spi.OntologyDocumentStore} and {@link
 * org.sequeless.ontology.spi.StoredOntologyDocument}, its ontology-persistence companion) lives in
 * this same package, added alongside these value types. Its full behavioural contract is documented
 * on the port interface itself, and asserted mechanically by {@code sequeless-spi-testkit}'s {@code
 * ObjectStoreContract}.
 *
 * <p>{@link org.sequeless.spi.outbox.OutboxPort} is a sibling outbound port, not a method on
 * {@link org.sequeless.spi.object.ObjectStorePort}: it lets a relay claim, one row at a time, an
 * unprocessed {@link org.sequeless.spi.outbox.OutboxEntry#KIND_ACTION_REQUEST} row written by
 * {@code commit} above, without forcing every {@code ObjectStorePort} implementation — including
 * test doubles that have no use for outbox claiming — to implement it.
 *
 * <p>{@link org.sequeless.spi.object.SearchVectorBackfillPort} is a sibling outbound port, not a
 * method on {@link org.sequeless.spi.object.ObjectStorePort}: it recomputes the full-text search data
 * of a tenant's stored objects after index preparation registers a new searchable property. Keeping
 * it separate leaves every {@code ObjectStorePort} implementation as it was.
 */
package org.sequeless.spi.object;
