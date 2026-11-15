/**
 * The transactional-outbox vocabulary: {@link org.sequeless.spi.outbox.OutboxEntry}, the row that a
 * commit on {@link org.sequeless.spi.object.ObjectStorePort} persists alongside the change it
 * records, and {@link org.sequeless.spi.outbox.OutboxPort}, the outbound port a relay uses to claim
 * one unprocessed row at a time.
 */
package org.sequeless.spi.outbox;
