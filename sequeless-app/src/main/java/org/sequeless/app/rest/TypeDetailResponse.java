package org.sequeless.app.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * JSON response body for {@code GET /types/{name}}: a type's identity, supertypes, display hints,
 * and its full, already-ordered property list.
 *
 * <p>{@code properties} arrives from {@code org.sequeless.spi.meta.TypeDefinition#properties()}
 * already sorted by {@code (order, iri)} — {@link TypeResponseMapper} must not re-sort it, or the
 * carefully-established display order (inherited properties included) would be lost.
 *
 * <p>{@code group} is a plain nullable field rather than {@code Optional} — a deliberate
 * wire-vs-domain divergence, the same rationale {@link WhoAmIResponse}'s javadoc already states.
 * The record component is named {@code isAbstract} rather than {@code abstract} because {@code
 * abstract} is a reserved Java keyword; {@link JsonProperty} restores the wire field name {@code
 * "abstract"} that the JSON shape calls for. {@code stateMachine} is likewise a plain nullable
 * field, for the same reason as {@code group}: {@code null} when this type declares no {@code
 * sq:StateMachine} (every type except {@code Project} today), present otherwise.
 *
 * @param iri the type's IRI
 * @param name the type's short name
 * @param label the type's display label
 * @param isAbstract whether this type exists purely for other types to specialise; rendered on the
 *     wire as {@code "abstract"}
 * @param superTypes the short names of this type's named supertypes
 * @param group the type's named display group, or {@code null} if it has none
 * @param hidden whether this type should be hidden from ordinary presentation
 * @param properties this type's properties, in display order, inherited ones included
 * @param stateMachine this type's {@code sq:StateMachine}, or {@code null} if it declares none
 */
public record TypeDetailResponse(
        String iri,
        String name,
        String label,
        @JsonProperty("abstract") boolean isAbstract,
        List<String> superTypes,
        String group,
        boolean hidden,
        List<PropertyResponse> properties,
        StateMachineResponse stateMachine) {}
