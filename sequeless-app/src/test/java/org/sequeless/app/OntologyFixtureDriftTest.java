package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards against the two ontology-fixture duplications this reactor carries, both called out as
 * hazards in earlier phases (F17/F25/F57): {@code sequeless-app}'s bundled {@code reference.ttl}
 * must stay byte-identical to {@code sequeless-spi-testkit}'s copy (F57 — the app cannot depend on
 * the testkit outside test scope, so it carries its own), and {@code
 * sequeless-adapter-ontology-jena}'s bundled {@code sq-meta.ttl} must stay byte-identical to {@code
 * sequeless-spi-testkit}'s copy (F17 — same reason, for the {@code sq:} vocabulary import the
 * adapter's {@code ImportResolver} resolves locally). Nothing enforces either pairing at compile
 * time; only a test like this one catches a copy silently drifting out of sync with its source of
 * truth.
 *
 * <p><b>Why these are read from the filesystem, not the classpath.</b> Both pairs share the exact
 * same classpath-relative resource path in each other's module ({@code /ontology/reference.ttl},
 * {@code /ontology/sq-meta.ttl}), so at test run time {@code sequeless-app}'s test classpath
 * contains both copies of each file under the identical name — one from this module's own {@code
 * target/classes} (or, for {@code sq-meta.ttl}, transitively from the Jena adapter's jar), the other
 * from the testkit's jar. A classloader resource lookup by that shared path returns whichever entry
 * happens to come first on the classpath, silently hiding the other; it cannot be used to compare
 * the two. Reading each copy by its module-relative filesystem path instead — Maven Surefire's
 * working directory is the module's own {@code ${basedir}}, so {@code ../<module>/...} reaches a
 * sibling reactor module directly — sidesteps the collision entirely and compares the two actual
 * source files.
 */
class OntologyFixtureDriftTest {

    @Test
    void appReferenceTurtleIsByteIdenticalToTestkits() {
        assertSameBytes(
                Path.of("src/main/resources/ontology/reference.ttl"),
                Path.of("../sequeless-spi-testkit/src/main/resources/ontology/reference.ttl"));
    }

    @Test
    void adapterSqMetaTurtleIsByteIdenticalToTestkits() {
        assertSameBytes(
                Path.of("../sequeless-adapter-ontology-jena/src/main/resources/ontology/sq-meta.ttl"),
                Path.of("../sequeless-spi-testkit/src/main/resources/ontology/sq-meta.ttl"));
    }

    private static void assertSameBytes(Path first, Path second) {
        assertThat(first).exists();
        assertThat(second).exists();
        assertThat(readBytes(first))
                .as("%s must be byte-identical to %s", first, second)
                .isEqualTo(readBytes(second));
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read ontology fixture: " + path, e);
        }
    }
}
