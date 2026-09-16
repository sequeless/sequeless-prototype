package org.sequeless.app.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * JSON response body for one element of {@code GET /types}: a type's identity and its supertypes,
 * without the full property list {@link TypeDetailResponse} carries — cheap enough to list every
 * type in the ontology in one response.
 *
 * <p>{@code superTypes} render as short names rather than full IRIs — resolved through the
 * {@code Map<iri, name>} {@link TypeResponseMapper} builds once per snapshot — because a short name
 * is far friendlier for a client piping this through {@code jq} than a raw IRI would be.
 *
 * <p>The record component is named {@code isAbstract} rather than {@code abstract} because {@code
 * abstract} is a reserved Java keyword; {@link JsonProperty} restores the wire field name {@code
 * "abstract"} that the JSON shape calls for.
 *
 * @param iri the type's IRI
 * @param name the type's short name
 * @param label the type's display label
 * @param isAbstract whether this type exists purely for other types to specialise; rendered on the
 *     wire as {@code "abstract"}
 * @param superTypes the short names of this type's named supertypes
 */
public record TypeSummaryResponse(
        String iri,
        String name,
        String label,
        @JsonProperty("abstract") boolean isAbstract,
        List<String> superTypes) {}
