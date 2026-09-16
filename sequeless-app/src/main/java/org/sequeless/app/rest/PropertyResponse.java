package org.sequeless.app.rest;

/**
 * JSON response shape for a single property attributed to a type, rendered inside {@link
 * TypeDetailResponse#properties()}. Sealed to exactly {@link AttributePropertyResponse} and {@link
 * RelationshipPropertyResponse}, mirroring {@code org.sequeless.spi.meta.PropertyDefinition}'s own
 * sealing: {@link TypeResponseMapper} maps a {@code PropertyDefinition} onto one of these two
 * records with an exhaustive {@code switch} and no {@code default} branch, so a third kind of
 * property arriving in the domain forces a compile error here rather than silently falling through
 * unmapped.
 *
 * <p>This is a plain marker with no shared accessor methods: the two implementations' fields
 * overlap heavily but not completely (only one carries {@code datatype}, only the other {@code
 * targetTypeIri} / {@code inverseIri} / {@code transitive}), so declaring common accessors here
 * would buy nothing no caller needs. These are write-only DTOs — never deserialized back into the
 * sealed type — so there is no {@code @JsonTypeInfo} either: Jackson serializes each element by its
 * runtime record class with no extra configuration, and the {@code kind} field each record carries
 * (e.g. {@code "attribute"} / {@code "relationship"}) is ordinary data, letting clients discriminate
 * without reflection.
 */
public sealed interface PropertyResponse permits AttributePropertyResponse, RelationshipPropertyResponse {}
