package org.sequeless.app.rest;

/**
 * JSON request body for {@code POST /objects/{type}/{id}/transitions/{name}}.
 *
 * <p>The field is named {@code expectedVersion} — not {@code version}, unlike {@link
 * UpdateObjectRequest} — per plan.md §8's wire example verbatim. Either it or the {@code If-Match}
 * header (or both, agreeing) must be present; {@link ObjectsController#fireTransition} resolves the
 * two exactly the way {@link ObjectsController#edit} already does, via the same private {@code
 * resolveExpectedVersion} helper.
 *
 * @param expectedVersion the version the caller expects the object to currently be at, or {@code
 *     null} to rely on {@code If-Match} instead
 */
public record TransitionRequest(Long expectedVersion) {}
