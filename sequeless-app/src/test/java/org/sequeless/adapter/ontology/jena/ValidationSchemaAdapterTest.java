package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * Pins the three methods of {@link ValidationSchemaAdapter} to the rules of the released
 * {@code SchemaValidationSchema} (sequeless-ontology {@code c9726a7}, {@code ontology-jena-adapter}):
 * the type lookup, the breadth-first supertype closure that keeps an unresolved supertype without
 * expanding it, the last-declared-wins datatype lookup, and the {@code xsd:string} fallback. That
 * class is package-private in its own module, so these tests check the adapter's copy directly.
 */
class ValidationSchemaAdapterTest {

    private static final String ONT_IRI = "https://example.org/ont";
    private static final String THING = ONT_IRI + "#Thing";
    private static final String ITEM = ONT_IRI + "#Item";
    private static final String TASK = ONT_IRI + "#Task";
    private static final String GHOST = ONT_IRI + "#Ghost";
    private static final String NAME = ONT_IRI + "#name";
    private static final String PRIORITY = ONT_IRI + "#priority";
    private static final String OWNER = ONT_IRI + "#owner";
    private static final String UNDECLARED = ONT_IRI + "#undeclared";

    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    private static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";
    private static final String XSD_DECIMAL = "http://www.w3.org/2001/XMLSchema#decimal";

    @Test
    void hasTypeIsTrueOnlyForDeclaredTypes() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of()));

        assertThat(schema.hasType(TASK)).isTrue();
        assertThat(schema.hasType(GHOST)).isFalse();
    }

    @Test
    void closureStartsWithTheTypeItselfAndWalksEverySupertype() {
        ValidationSchemaAdapter schema =
                adapter(type(TASK, List.of(ITEM)), type(ITEM, List.of(THING)), type(THING, List.of()));

        assertThat(schema.typeAndSupertypes(TASK)).containsExactly(TASK, ITEM, THING);
    }

    @Test
    void unresolvedSupertypeStaysInTheClosureButIsNotExpanded() {
        // GHOST is named as a supertype but never declared. The walk keeps it and does not expand it.
        ValidationSchemaAdapter schema =
                adapter(type(TASK, List.of(ITEM, GHOST)), type(ITEM, List.of(THING)), type(THING, List.of()));

        Set<String> closure = schema.typeAndSupertypes(TASK);

        assertThat(schema.hasType(GHOST)).isFalse();
        assertThat(closure).containsExactly(TASK, ITEM, GHOST, THING);
    }

    @Test
    void closureOfAnUndeclaredTypeIsTheTypeAlone() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of(ITEM)));

        assertThat(schema.typeAndSupertypes(GHOST)).containsExactly(GHOST);
    }

    @Test
    void closureIsUnmodifiable() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of()));

        Set<String> closure = schema.typeAndSupertypes(TASK);

        assertThatThrownBy(() -> closure.add(GHOST)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lastDeclaredAttributeWinsWhenAPropertyIsDeclaredTwice() {
        ValidationSchemaAdapter integerLast = adapter(
                type(TASK, List.of(), attribute(PRIORITY, Datatype.DECIMAL), attribute(PRIORITY, Datatype.INTEGER)));
        ValidationSchemaAdapter decimalLast = adapter(
                type(TASK, List.of(), attribute(PRIORITY, Datatype.INTEGER), attribute(PRIORITY, Datatype.DECIMAL)));

        assertThat(integerLast.xsdDatatypeOf(TASK, PRIORITY)).isEqualTo(XSD_INTEGER);
        assertThat(decimalLast.xsdDatatypeOf(TASK, PRIORITY)).isEqualTo(XSD_DECIMAL);
    }

    @Test
    void lastDeclaredRelationshipWinsOverAnEarlierAttributeAndGivesXsdString() {
        ValidationSchemaAdapter schema =
                adapter(type(TASK, List.of(), attribute(OWNER, Datatype.INTEGER), relationship(OWNER, TASK)));

        assertThat(schema.xsdDatatypeOf(TASK, OWNER)).isEqualTo(XSD_STRING);
    }

    @Test
    void attributeGivesItsDatatypeXsdIri() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of(), attribute(PRIORITY, Datatype.INTEGER)));

        assertThat(schema.xsdDatatypeOf(TASK, PRIORITY)).isEqualTo(XSD_INTEGER);
    }

    @Test
    void relationshipPropertyFallsBackToXsdString() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of(), relationship(OWNER, TASK)));

        assertThat(schema.xsdDatatypeOf(TASK, OWNER)).isEqualTo(XSD_STRING);
    }

    @Test
    void undeclaredPropertyOnADeclaredTypeFallsBackToXsdString() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of(), attribute(PRIORITY, Datatype.INTEGER)));

        assertThat(schema.xsdDatatypeOf(TASK, UNDECLARED)).isEqualTo(XSD_STRING);
    }

    @Test
    void unknownTypeFallsBackToXsdStringEvenForAPropertyDeclaredElsewhere() {
        ValidationSchemaAdapter schema = adapter(type(TASK, List.of(), attribute(PRIORITY, Datatype.INTEGER)));

        assertThat(schema.xsdDatatypeOf(GHOST, PRIORITY)).isEqualTo(XSD_STRING);
    }

    @Test
    void datatypeIsReadFromTheTypeItselfNotFromItsSupertypes() {
        ValidationSchemaAdapter schema =
                adapter(type(TASK, List.of(ITEM)), type(ITEM, List.of(), attribute(NAME, Datatype.INTEGER)));

        assertThat(schema.xsdDatatypeOf(TASK, NAME)).isEqualTo(XSD_STRING);
        assertThat(schema.xsdDatatypeOf(ITEM, NAME)).isEqualTo(XSD_INTEGER);
    }

    private static ValidationSchemaAdapter adapter(TypeDefinition... types) {
        return new ValidationSchemaAdapter(
                new Schema(new OntologyVersion(1), ONT_IRI, Optional.empty(), Map.of(), List.of(types)));
    }

    private static TypeDefinition type(String iri, List<String> superTypes, PropertyDefinition... properties) {
        return new TypeDefinition(iri, iri, superTypes, List.of(properties), DisplayHints.none(), false);
    }

    private static AttributeDefinition attribute(String iri, Datatype datatype) {
        return new AttributeDefinition(
                iri, iri, Cardinality.optional(), false, false, false, false, false, DisplayHints.none(), datatype);
    }

    private static RelationshipDefinition relationship(String iri, String targetTypeIri) {
        return new RelationshipDefinition(
                iri,
                iri,
                Cardinality.optional(),
                false,
                false,
                false,
                false,
                false,
                DisplayHints.none(),
                targetTypeIri,
                Optional.empty(),
                false);
    }
}
