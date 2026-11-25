package org.sequeless.spi.automation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A Store event (see GLOSSARY) as the automation side receives it: the record a Store writes when it
 * accepts a Store Command, either an object created, updated or deleted, or a transition fired. Each
 * implementation is the outbox row the event came from, read back as a typed value.
 *
 * <p>{@link #id()} is the outbox row's own id, and {@link #payload()} is the row's payload unchanged, so
 * a consumer can always match the event back to its row. The event carries the Ontology version it was
 * committed under in the payload's {@code ontologyVersion} key, as the row does.
 *
 * <p>{@code permits} is declared explicitly because each implementation lives in its own file, not
 * nested inside this one; implicit permits only works for subtypes nested in the sealed type's own file.
 */
public sealed interface StoreEvent permits ObjectChangedEvent, TransitionFiredEvent {

    /**
     * The outbox row's own identity.
     *
     * @return the id of the outbox row this event came from
     */
    UUID id();

    /**
     * The event's payload, keyed by field name, unchanged from the outbox row.
     *
     * @return an unmodifiable copy of the row's payload
     */
    Map<String, Object> payload();

    /**
     * When the event occurred.
     *
     * @return the instant the event occurred, as recorded on the outbox row
     */
    Instant occurredAt();
}
