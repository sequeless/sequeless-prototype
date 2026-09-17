package org.sequeless.app.rest;

import java.util.List;

/**
 * JSON response body for {@code GET /objects/{type}}.
 *
 * <p>{@code page} — not {@code number} — is the field name, a deliberate wire-vs-SPI divergence:
 * {@code org.sequeless.spi.object.Page}/{@code PageResult} use {@code number}, but plan.md §8's
 * wire shape explicitly spells this field {@code page}.
 *
 * @param items the objects on this page
 * @param page the 0-based page number this result answers
 * @param size the page size this result was computed with (after the 200 cap)
 * @param totalItems the total number of items across every page
 * @param totalPages the total number of pages needed to hold {@code totalItems} at {@code size}
 *     each, rounded up
 */
public record PageResultResponse(
        List<BusinessObjectResponse> items, int page, int size, long totalItems, int totalPages) {}
