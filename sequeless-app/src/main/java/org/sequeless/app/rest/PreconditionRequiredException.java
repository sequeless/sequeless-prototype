package org.sequeless.app.rest;

/**
 * Thrown by {@link ObjectsController#edit} when a {@code PUT /objects/{type}/{id}} request
 * supplies neither a {@code version} in its body nor an {@code If-Match} header. {@code
 * org.sequeless.core.api.BusinessObjectService#edit}'s {@code expectedVersion} parameter is a
 * non-optional {@code long}, so the REST boundary must resolve one before it can call in — with
 * neither source present, there is nothing to resolve. Mapped by {@link ApiExceptionAdvice} to
 * HTTP 428 (Precondition Required).
 *
 * <p>Package-private: this is a REST-level precondition check, not a core use-case concern, so
 * nothing outside {@code org.sequeless.app.rest} has a reason to throw or catch it.
 */
final class PreconditionRequiredException extends RuntimeException {

    PreconditionRequiredException() {
        super("A version is required: supply either a body 'version' or an 'If-Match' header");
    }
}
