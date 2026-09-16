package org.sequeless.spi.object;

/**
 * A single change to apply as part of a {@link ChangeSet}: {@link Create}, {@link Update}, or
 * {@link Delete}. Every {@link ObjectStorePort#commit} call applies a list of these atomically —
 * all of them succeed, or none of them do.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because every implementation
 * lives in its own file, not nested inside this one; implicit permits only works for subtypes
 * nested in the sealed type's own file.
 */
public sealed interface Mutation permits Create, Update, Delete {
}
