package org.sequeless.app.rest;

/**
 * JSON response body {@link ApiExceptionAdvice} renders for a {@code TypeNotFoundException} (HTTP
 * 404) or an {@code AuthorizationException} (HTTP 403) — the two failure shapes simple enough that
 * a single message string is the whole body.
 *
 * @param message a human-readable description of what went wrong
 */
public record ErrorResponse(String message) {}
