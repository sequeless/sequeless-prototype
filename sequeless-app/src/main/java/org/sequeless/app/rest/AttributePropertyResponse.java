package org.sequeless.app.rest;

/**
 * JSON response shape for a scalar-valued property, mapped from {@code
 * org.sequeless.spi.meta.AttributeDefinition} by {@link TypeResponseMapper}.
 *
 * <p>{@code group} and {@code cardinalityMax} are plain nullable fields rather than {@code
 * Optional} — a deliberate wire-vs-domain divergence, the same rationale {@link
 * WhoAmIResponse}'s javadoc already states for its own fields: {@code Optional} is a fine
 * in-process signal but an awkward thing to ask Jackson to serialize.
 *
 * @param kind always {@code "attribute"}, so clients can discriminate {@link PropertyResponse}
 *     elements without reflection
 * @param iri the property's IRI
 * @param name the property's short name
 * @param label the property's display label
 * @param order the property's display order among its siblings
 * @param group the property's named display group, or {@code null} if it has none
 * @param hidden whether this property should be hidden from ordinary presentation
 * @param facet whether this property is usable as a facet in search/filtering
 * @param indexed whether this property's values are indexed
 * @param searchable whether this property's values are full-text searchable
 * @param readOnly whether this property is computed and not writable through the generic API
 * @param cardinalityMin the minimum number of values this property may hold
 * @param cardinalityMax the maximum number of values this property may hold, or {@code null} if
 *     unbounded
 * @param datatype the scalar type this property's values hold, e.g. {@code "STRING"}
 * @param derivation how this property's value is computed, or {@code null} if it is an ordinary
 *     stored property
 */
public record AttributePropertyResponse(
        String kind,
        String iri,
        String name,
        String label,
        int order,
        String group,
        boolean hidden,
        boolean facet,
        boolean indexed,
        boolean searchable,
        boolean readOnly,
        int cardinalityMin,
        Integer cardinalityMax,
        String datatype,
        DerivationResponse derivation)
        implements PropertyResponse {}
