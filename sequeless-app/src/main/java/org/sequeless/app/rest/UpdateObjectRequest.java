package org.sequeless.app.rest;

import java.util.Map;

/**
 * JSON request body for {@code PUT /objects/{type}/{id}}.
 *
 * <p>{@code version} is nullable: its presence or absence, together with whether an {@code
 * If-Match} header is present, is what {@link ObjectsController#edit} uses to resolve the single
 * {@code expectedVersion} {@code long} that {@code
 * org.sequeless.core.api.BusinessObjectService#edit} requires — neither present is a 428, both
 * present but disagreeing is a 400. See {@link ObjectsController}'s javadoc for the full rule.
 *
 * @param version the version the caller expects the stored object to currently be at, or {@code
 *     null} if relying on the {@code If-Match} header instead
 * @param properties JSON-like raw property values that fully replace the object's current
 *     properties, keyed by property short name or full IRI
 */
public record UpdateObjectRequest(Long version, Map<String, Object> properties) {}
