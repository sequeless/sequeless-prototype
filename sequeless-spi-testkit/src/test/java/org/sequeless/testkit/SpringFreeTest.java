package org.sequeless.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the testkit's Spring-free rule (spec 18, Q16): no source or resource file under {@code src}
 * and no {@code pom.xml} of this module may name Spring. The needle is assembled at run time so this
 * file does not match itself.
 */
class SpringFreeTest {

    private static final String NEEDLE = "org." + "springframework";

    @Test
    void noSourceResourceOrPomNamesSpring() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        List<Path> files;
        try (var stream = Files.walk(moduleRoot.resolve("src"))) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        List<Path> checked = new ArrayList<>(files);
        checked.add(moduleRoot.resolve("pom.xml"));

        List<Path> named =
                checked.stream().filter(p -> readText(p).contains(NEEDLE)).toList();

        assertThat(named).isEmpty();
        assertThat(checked).isNotEmpty();
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
