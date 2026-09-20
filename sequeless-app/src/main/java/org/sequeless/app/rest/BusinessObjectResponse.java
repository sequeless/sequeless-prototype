package org.sequeless.app.rest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JSON response body for a single business object, rendered by {@code GET /objects/{type}/{id}},
 * {@code POST /objects/{type}}, {@code PUT /objects/{type}/{id}}, and as each element of {@link
 * PageResultResponse#items()}.
 *
 * <p>{@code properties} is keyed by property short name, not full IRI — {@link
 * ObjectPropertyMapper} does that translation, mirroring {@link TypeResponseMapper}'s
 * IRI-to-short-name rendering for types.
 *
 * @param id the object's id
 * @param type the short name of the object's own ontology type (which may be a subtype of the
 *     {@code type} path variable the request named, e.g. when browsing an abstract supertype)
 * @param typeIri the full IRI of the object's own ontology type
 * @param version the optimistic-locking version, also rendered as the response's {@code ETag}
 * @param state the short name of the object's current state-machine state, or {@code null} if the
 *     type has none
 * @param properties the object's property values, keyed by short name
 * @param audit who created and last modified this object, and when
 */
public record BusinessObjectResponse(
        UUID id,
        String type,
        String typeIri,
        long version,
        String state,
        Map<String, Object> properties,
        AuditResponse audit) {

    /**
     * @param createdAt when the object was created
     * @param createdBy the principal that created the object
     * @param updatedAt when the object was last modified
     * @param updatedBy the principal that last modified the object
     */
    public record AuditResponse(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {}
}
