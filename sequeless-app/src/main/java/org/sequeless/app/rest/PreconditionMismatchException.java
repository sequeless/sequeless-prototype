package org.sequeless.app.rest;

/**
 * Thrown by {@link ObjectsController#edit} when a {@code PUT /objects/{type}/{id}} request
 * supplies both a body {@code version} and an {@code If-Match} header, and the two disagree. There
 * is no existing generic "bad request" exception type in {@code org.sequeless.core} to reuse for
 * this — this is a REST-level precondition check, not a core use-case concern. Mapped by {@link
 * ApiExceptionAdvice} to HTTP 400 (Bad Request).
 *
 * <p>Package-private: nothing outside {@code org.sequeless.app.rest} has a reason to throw or
 * catch it.
 */
final class PreconditionMismatchException extends RuntimeException {

    PreconditionMismatchException(long bodyVersion, long ifMatchVersion) {
        super(
                "Body version " + bodyVersion + " does not match If-Match version " + ifMatchVersion);
    }
}
