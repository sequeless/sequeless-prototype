package org.sequeless.app.rest;

import java.util.Map;

/**
 * JSON request body for {@code POST /objects/{type}}.
 *
 * <p>{@code properties} is passed straight through to {@code
 * org.sequeless.core.api.BusinessObjectService#add}, keyed by either property short name or full
 * IRI — {@code ValueCoercer} accepts either form, so this controller does no key translation of
 * its own on the way in (only the response mapper translates full IRIs back to short names).
 *
 * @param properties JSON-like raw property values, keyed by property short name or full IRI
 */
public record CreateObjectRequest(Map<String, Object> properties) {}
