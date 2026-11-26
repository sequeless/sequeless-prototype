package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.Cardinality;

/**
 * Unit tests for {@link ShapeCoercion} (spec 17, H7, decision b). The declared cardinality is the only input that decides
 * a wrap: each property below is declared in the fixture schema with the cardinality its test needs, and none is chosen
 * by name.
 */
class ShapeCoercionTest {

    private static final String TYPE = "https://sequeless.dev/ns/test#Thing";
    private static final String OTHER_TYPE = "https://sequeless.dev/ns/test#Other";
    private static final String NAME = "https://sequeless.dev/ns/test#name";
    private static final String TAGS = "https://sequeless.dev/ns/test#tags";
    private static final String LABELS = "https://sequeless.dev/ns/test#labels";
    private static final String UNDECLARED = "https://sequeless.dev/ns/test#undeclared";

    /** Unbounded (no maximum) and above-one maximum are both multi-valued; a maximum of one is single-valued. */
    private final ShapeCoercion shape = new ShapeCoercion(BindingFakes.schemaDeclaring(
            TYPE,
            Map.of(
                    NAME, Cardinality.atMost(1),
                    TAGS, new Cardinality(0, OptionalInt.empty()),
                    LABELS, Cardinality.range(0, 3))));

    @Test
    void aScalarOnAMultiValuedPropertyBecomesAOneElementList() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(TAGS, "one"));

        assertThat(shaped).isEqualTo(Map.of(TAGS, List.of("one")));
    }

    @Test
    void aListOnAMultiValuedPropertyStaysAsItIs() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(TAGS, List.of("a", "b")));

        assertThat(shaped).isEqualTo(Map.of(TAGS, List.of("a", "b")));
    }

    @Test
    void aMaximumAboveOneIsMultiValuedToo() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(LABELS, "x"));

        assertThat(shaped).isEqualTo(Map.of(LABELS, List.of("x")));
    }

    @Test
    void aSingleValuedPropertyIsUntouched() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(NAME, "Ada"));

        assertThat(shaped).isEqualTo(Map.of(NAME, "Ada"));
    }

    @Test
    void aPropertyTheSchemaDoesNotDeclareIsLeftAlone() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(UNDECLARED, "x"));

        assertThat(shaped).isEqualTo(Map.of(UNDECLARED, "x"));
    }

    @Test
    void aTypeTheSchemaDoesNotDeclareLeavesEveryValueAlone() {
        Map<String, Object> shaped = shape.coerce(BindingFakes.SCOPE, OTHER_TYPE, Map.of(TAGS, "one"));

        assertThat(shaped).isEqualTo(Map.of(TAGS, "one"));
    }

    @Test
    void aMixedWriteShapesOnlyTheScalarsOfMultiValuedProperties() {
        Map<String, Object> shaped =
                shape.coerce(BindingFakes.SCOPE, TYPE, Map.of(NAME, "Ada", TAGS, "one", LABELS, List.of("a", "b")));

        assertThat(shaped).isEqualTo(Map.of(NAME, "Ada", TAGS, List.of("one"), LABELS, List.of("a", "b")));
    }
}
