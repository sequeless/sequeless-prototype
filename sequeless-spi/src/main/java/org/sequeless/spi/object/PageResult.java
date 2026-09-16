package org.sequeless.spi.object;

import java.util.List;
import java.util.Objects;

/**
 * A page of results from {@link ObjectStorePort#browse}: the items on this page, the page request
 * it answers, and the total number of items across every page.
 *
 * @param items the items on this page; must not be {@code null}; may be empty (for a page number
 *     past the end); returned as an unmodifiable copy so callers cannot mutate this result after
 *     construction
 * @param number the 0-based page number this result answers; must not be negative
 * @param size the page size this result was computed with; must be at least 1
 * @param totalItems the total number of items across every page; must not be negative
 * @param <T> the type of item on this page
 */
public record PageResult<T>(List<T> items, int number, int size, long totalItems) {

    public PageResult {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
        if (number < 0) {
            throw new IllegalArgumentException("PageResult number must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("PageResult size must be at least 1");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("PageResult totalItems must not be negative");
        }
    }

    /**
     * @return the total number of pages needed to hold {@link #totalItems()} items at {@link
     *     #size()} each, rounded up; 0 when {@link #totalItems()} is 0
     */
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil(totalItems / (double) size);
    }
}
