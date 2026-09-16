package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;

/**
 * Maps sequeless-core's {@code org.sequeless.spi.meta} types onto their {@link TypesController}
 * wire representations. Factored out of {@link TypesController} itself, mirroring {@link
 * WhoAmIResponse#from(org.sequeless.core.api.WhoAmIResult)}, because both {@link
 * TypeSummaryResponse} and {@link TypeDetailResponse} need the same superTypes-to-short-names
 * rendering.
 *
 * <p>Package-private: nothing outside {@code org.sequeless.app.rest} has a reason to map these
 * types directly.
 */
final class TypeResponseMapper {

    private TypeResponseMapper() {}

    /**
     * @param snapshot the snapshot to render; must not be {@code null}
     * @return one {@link TypeSummaryResponse} per {@link MetaModelSnapshot#types()}, in the same
     *     order, with {@code superTypes} resolved to short names via a {@code Map<iri, name>}
     *     built once for this call
     */
    static List<TypeSummaryResponse> toSummaries(MetaModelSnapshot snapshot) {
        Map<String, String> namesByIri = namesByIri(snapshot);
        return snapshot.types().stream().map(type -> toSummary(type, namesByIri)).toList();
    }

    /**
     * @param type the resolved, authorized type to render; must not be {@code null}
     * @return the {@link TypeDetailResponse} rendering of {@code type}, with {@code properties}
     *     left in the order {@link TypeDefinition#properties()} already provides
     */
    static TypeDetailResponse toDetail(TypeDefinition type) {
        List<String> superTypeNames =
                type.superTypes().stream().map(TypeResponseMapper::shortName).toList();
        List<PropertyResponse> properties =
                type.properties().stream().map(TypeResponseMapper::toProperty).toList();
        return new TypeDetailResponse(
                type.iri(),
                shortName(type.iri()),
                type.label(),
                type.isAbstract(),
                superTypeNames,
                type.displayHints().group().orElse(null),
                type.displayHints().hidden(),
                properties);
    }

    private static TypeSummaryResponse toSummary(TypeDefinition type, Map<String, String> namesByIri) {
        List<String> superTypeNames =
                type.superTypes().stream()
                        .map(iri -> namesByIri.getOrDefault(iri, shortName(iri)))
                        .toList();
        return new TypeSummaryResponse(
                type.iri(), namesByIri.get(type.iri()), type.label(), type.isAbstract(), superTypeNames);
    }

    private static Map<String, String> namesByIri(MetaModelSnapshot snapshot) {
        List<TypeDefinition> types = snapshot.types();
        List<String> names = snapshot.names();
        Map<String, String> namesByIri = new HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            namesByIri.put(types.get(i).iri(), names.get(i));
        }
        return namesByIri;
    }

    /**
     * Maps one {@link PropertyDefinition} onto its {@link PropertyResponse}. The {@code switch} is
     * exhaustive over the sealed {@link PropertyDefinition} hierarchy with no {@code default}
     * branch, so a third permitted implementation arriving later would fail this compilation unit
     * rather than silently falling through unmapped.
     */
    private static PropertyResponse toProperty(PropertyDefinition property) {
        return switch (property) {
            case AttributeDefinition attribute -> new AttributePropertyResponse(
                    "attribute",
                    attribute.iri(),
                    shortName(attribute.iri()),
                    attribute.label(),
                    attribute.order(),
                    attribute.displayHints().group().orElse(null),
                    attribute.displayHints().hidden(),
                    attribute.facet(),
                    attribute.indexed(),
                    attribute.searchable(),
                    attribute.readOnly(),
                    attribute.cardinality().min(),
                    attribute.cardinality().max().isPresent()
                            ? attribute.cardinality().max().getAsInt()
                            : null,
                    attribute.datatype().name());
            case RelationshipDefinition relationship -> new RelationshipPropertyResponse(
                    "relationship",
                    relationship.iri(),
                    shortName(relationship.iri()),
                    relationship.label(),
                    relationship.order(),
                    relationship.displayHints().group().orElse(null),
                    relationship.displayHints().hidden(),
                    relationship.facet(),
                    relationship.indexed(),
                    relationship.searchable(),
                    relationship.readOnly(),
                    relationship.cardinality().min(),
                    relationship.cardinality().max().isPresent()
                            ? relationship.cardinality().max().getAsInt()
                            : null,
                    relationship.targetTypeIri(),
                    relationship.inverseIri().orElse(null),
                    relationship.transitive());
        };
    }

    /**
     * Duplicates {@code MetaModelSnapshot}'s own short-name rule (local name after an IRI's last
     * {@code #}, or after its last {@code /} if there is no {@code #}) rather than calling back
     * into a snapshot, because {@link #toDetail(TypeDefinition)} renders a single, already
     * resolved {@link TypeDefinition} — fetching a whole snapshot just to look up one supertype's
     * short name would mean an extra, redundantly-authorized {@code OntologyPort} round trip for
     * no benefit: the rule is a pure function of the IRI string, not of what else happens to be in
     * the snapshot.
     *
     * @param iri the IRI to derive a short name from; must not be {@code null}
     * @return the local name after the IRI's last {@code #}, or after its last {@code /} if there
     *     is no {@code #}, or the whole IRI if neither is present
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
