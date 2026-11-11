package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.testfixtures.Fixtures;

/**
 * Guards against the one ontology-fixture duplication that remains after the spec 12 cut-over:
 * {@code sequeless-app}'s bundled {@code reference.ttl} must stay byte-identical to the copy in the
 * released {@code ontology-testfixtures} jar (spec 12 section 7). The app cannot depend on the
 * testkit outside test scope, so it carries its own copy. Nothing enforces the pairing at compile
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

    @Test
    void appReferenceTurtleIsByteIdenticalToTheReleasedFixture() {
        Path app = Path.of("src/main/resources/ontology/reference.ttl");

        assertThat(app).exists();
        assertThat(readBytes(app))
                .as("%s must be byte-identical to the ontology-testfixtures jar's %s", app, REFERENCE)
                .isEqualTo(releasedReferenceTurtle());
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
