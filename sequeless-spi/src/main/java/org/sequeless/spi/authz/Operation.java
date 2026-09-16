package org.sequeless.spi.authz;

/**
 * The kinds of action an {@link AuthorizationPort} is asked to authorize against a resource.
 *
 * <p>This is a closed, ontology-independent vocabulary: it describes what a caller is trying to
 * do, not what kind of object they are trying to do it to (that distinction, and the object
 * types themselves, are defined at runtime by the OWL ontology in later phases, not by this
 * enum). Every port implementation and every use case shares this one set of verbs so that
 * authorization policy can be expressed once, independently of which business object type is
 * involved.
 *
 * <p>Declaration order is part of the contract: {@link #values()} is asserted by a unit test to
 * guard {@link #ordinal()} stability, since ordinal values may end up persisted or compared by
 * adapters even though {@code Operation} itself never exposes an ordinal-based API.
 */
public enum Operation {

    /** List or search for resources without reading any single one's full content. */
    BROWSE,

    /** Read a single resource's content. */
    READ,

    /** Modify an existing resource's content. */
    EDIT,

    /** Create a new resource. */
    ADD,

    /** Remove an existing resource. */
    DELETE,

    /** Move a resource through a lifecycle or workflow state. */
    TRANSITION,

    /** Perform an administrative action outside the ordinary content lifecycle. */
    ADMIN
}
