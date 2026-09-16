package org.sequeless.spi.object;

/**
 * A page request for {@link ObjectStorePort#browse}: a 0-based page number and a page size.
 *
 * @param number the 0-based page number; must not be negative
 * @param size the page size; must be between 1 and 500 inclusive
 */
public record Page(int number, int size) {

    public Page {
        if (number < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1 || size > 500) {
            throw new IllegalArgumentException("Page size must be between 1 and 500");
        }
    }
}
