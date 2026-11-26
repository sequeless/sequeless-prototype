package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.testfixtures.Fixtures;

/**
 * Guards against the one ontology-fixture duplication that remains after the spec 12 cut-over:
 * {@code sequeless-app}'s bundled {@code reference.ttl} must stay byte-identical to the copy in the
 * released {@code ontology-testfixtures} jar (spec 12 section 7), except for the one H7 edit that {@code H7_EDITS}
 * names (spec 17 section 9). The app depends on that jar only
 * at test scope, so main code carries its own copy. Nothing enforces the pairing at compile
 * time; only this test catches the copy drifting out of sync with its source of truth.
 *
 * <p>The test checks the app's {@code reference.ttl} against the released {@code ontology-testfixtures}
 * copy. The vocabulary-copy comparison that earlier phases ran was removed with the ontology code.
 *
 * <p><b>Why the jar's copy is picked by origin.</b> Both copies sit at the classpath path {@code
 * ontology/reference.ttl}, and in app tests the app's own copy comes first. A plain classloader
 * lookup would therefore return the app's copy and compare it with itself. The test enumerates every
 * entry for the path and keeps the one that comes from the jar holding the released {@link Fixtures}
 * class.
 */
class OntologyFixtureDriftTest {

    private static final String REFERENCE = "ontology/reference.ttl";

    /**
     * The one divergence the app's copy may carry (spec 17 section 9, H7). {@code Task.title} gets an
     * {@code owl:maxCardinality 1} restriction, and the header paragraph that said title was unrestricted is rewritten
     * to match. The released 0.1.0 fixture carries neither change yet. Each released text must occur exactly once in
     * the released bytes, so the allowance cannot quietly become a no-op. Remove both pairs once the
     * ontology-testfixtures jar that the BOM pins carries the app's text.
     */
    private static final String TITLE_RELEASED = "        [ a owl:Restriction ;\n"
            + "          owl:onProperty ex:title ;\n"
            + "          owl:minCardinality \"1\"^^xsd:nonNegativeInteger ] ,\n";

    private static final String TITLE_APP = TITLE_RELEASED
            + "        [ a owl:Restriction ;\n"
            + "          owl:onProperty ex:title ;\n"
            + "          owl:maxCardinality \"1\"^^xsd:nonNegativeInteger ] ,\n";
    private static final String HEADER_RELEASED =
            "# exactly the reason filter/sort/facet resolution does above. title/description remain\n"
                    + "# deliberately unrestricted (max unbounded) — nothing filters/sorts/facets/aggregates on them, and\n"
                    + "# existing tests (F39) rely on posting them as JSON lists.\n";
    private static final String HEADER_APP =
            "# exactly the reason filter/sort/facet resolution does above. Task.title carries an owl:maxCardinality\n"
                    + "# 1 restriction beside its owl:minCardinality 1 (spec 17 section 9, H7): a single-valued title is\n"
                    + "# sortable and filterable on Task, and a Task whose title is a JSON list is rejected by the Store.\n"
                    + "# ex:title on Project and ex:description stay deliberately unrestricted (max unbounded): nothing\n"
                    + "# filters/sorts/facets/aggregates on them, and existing tests (F39) post them as JSON lists.\n";
    private static final List<String[]> H7_EDITS =
            List.of(new String[] {TITLE_RELEASED, TITLE_APP}, new String[] {HEADER_RELEASED, HEADER_APP});

    @Test
    void appReferenceTurtleIsTheReleasedFixturePlusOnlyTheH7Edit() {
        Path app = Path.of("src/main/resources/ontology/reference.ttl");

        assertThat(app).exists();
        String expected = new String(releasedReferenceTurtle(), StandardCharsets.UTF_8);
        for (String[] edit : H7_EDITS) {
            assertThat(occurrences(expected, edit[0]))
                    .as(
                            "the released text to edit must occur exactly once: %s",
                            edit[0].lines().findFirst())
                    .isEqualTo(1);
            expected = expected.replace(edit[0], edit[1]);
        }
        assertThat(new String(readBytes(app), StandardCharsets.UTF_8))
                .as("%s must be the ontology-testfixtures jar's %s plus only the spec 17 H7 edit", app, REFERENCE)
                .isEqualTo(expected);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    /**
     * The bytes of {@value #REFERENCE} as the released jar holds them. The jar is the code source of
     * the released {@link Fixtures} class, so its entry has the URL {@code jar:<code source>!/<path>}.
     */
    private static byte[] releasedReferenceTurtle() {
        String codeSource = Fixtures.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString();
        String releasedEntry = "jar:" + codeSource + "!/" + REFERENCE;

        List<URL> matches = enumerate(REFERENCE).stream()
                .filter(url -> url.toString().equals(releasedEntry))
                .toList();
        assertThat(matches)
                .as("the ontology-testfixtures jar must carry %s", releasedEntry)
                .hasSize(1);

        try (InputStream in = matches.get(0).openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read released ontology fixture: " + releasedEntry, e);
        }
    }

    private static List<URL> enumerate(String path) {
        try {
            return Collections.list(Fixtures.class.getClassLoader().getResources(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to enumerate classpath resource: " + path, e);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read ontology fixture: " + path, e);
        }
    }
}
