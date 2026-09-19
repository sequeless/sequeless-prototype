/**
 * The business-object vocabulary: immutable value types for storing and mutating instances of
 * ontology types, independent of any RDF library, JSON library, or SQL dialect.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except {@link org.sequeless.spi.TenantId} from its parent package and
 * sibling types within this package. {@link org.sequeless.spi.object.BusinessObject} is the
 * aggregate root: an {@link org.sequeless.spi.object.ObjectId}, a {@link
 * org.sequeless.spi.object.TypeRef}, an optimistic-locking version, an optional state, a map of
 * {@link org.sequeless.spi.object.PropertyRef} to {@link org.sequeless.spi.object.Value}, and an
 * {@link org.sequeless.spi.object.Audit} trail. {@link org.sequeless.spi.object.Value} is sealed to
 * {@link org.sequeless.spi.object.TextValue}, {@link org.sequeless.spi.object.IntegerValue}, {@link
 * org.sequeless.spi.object.DecimalValue}, {@link org.sequeless.spi.object.BoolValue}, {@link
 * org.sequeless.spi.object.DateTimeValue}, {@link org.sequeless.spi.object.DateValue}, {@link
 * org.sequeless.spi.object.ReferenceValue}, and {@link org.sequeless.spi.object.ListValue}.
 *
 * <p>{@link org.sequeless.spi.object.Mutation}, sealed to {@link org.sequeless.spi.object.Create},
 * {@link org.sequeless.spi.object.Update}, and {@link org.sequeless.spi.object.Delete}, together
 * with {@link org.sequeless.spi.object.OutboxEntry} form a {@link
 * org.sequeless.spi.object.ChangeSet}: the unit of atomic change an {@link
 * org.sequeless.spi.object.ObjectStorePort#commit} call applies, producing a {@link
 * org.sequeless.spi.object.CommitResult}. {@link org.sequeless.spi.object.Page} and {@link
 * org.sequeless.spi.object.PageResult} are the paging vocabulary for {@link
 * org.sequeless.spi.object.ObjectStorePort#browse}. {@link
 * org.sequeless.spi.object.StaleObjectException} and {@link
 * org.sequeless.spi.object.ObjectNotFoundException} are the unchecked exceptions {@code commit}
 * throws when a mutation's optimistic-locking expectation is not met or its target does not exist.
 *
 * <p>The {@link org.sequeless.spi.object.ObjectStorePort} outbound port interface (together with
 * {@link org.sequeless.spi.object.OntologyDocumentStore} and {@link
 * org.sequeless.spi.object.StoredOntologyDocument}, its ontology-persistence companion) lives in
 * this same package, added alongside these value types. Its full behavioural contract is documented
 * on the port interface itself, and asserted mechanically by {@code sequeless-spi-testkit}'s {@code
 * ObjectStoreContract}.
 *
 * <p>{@link org.sequeless.spi.object.OutboxPort} is a sibling outbound port, not a method on
 * {@link org.sequeless.spi.object.ObjectStorePort}: it lets a relay claim, one row at a time, an
 * unprocessed {@link org.sequeless.spi.object.OutboxEntry#KIND_ACTION_REQUEST} row written by
 * {@code commit} above, without forcing every {@code ObjectStorePort} implementation — including
 * test doubles that have no use for outbox claiming — to implement it.
 */
package org.sequeless.spi.object;
