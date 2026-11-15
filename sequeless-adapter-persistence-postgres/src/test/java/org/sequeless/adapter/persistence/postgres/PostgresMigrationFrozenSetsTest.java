package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Proves the frozen post-P7 migration sets in test resources still match the live sets. The frozen
 * command set is a subset of the live command set: later migrations may be added to the live set
 * after the freeze, as V7 was. The frozen query set has the same file names as its live set. Every
 * frozen file is byte-identical to its live file. P2's PR copies its own migration pair from this set,
 * so a silent drift between the two would corrupt that copy.
 */
class PostgresMigrationFrozenSetsTest {

    private static final String LIVE_COMMAND = "db/migration/sequeless/postgres";
    private static final String LIVE_QUERY = "db/migration/sequeless/postgres-query";
    private static final String FROZEN_COMMAND = "frozen-migrations/post-p7/command";
    private static final String FROZEN_QUERY = "frozen-migrations/post-p7/query";

    @Test
    void frozenCommandSetIsASubsetOfTheLiveCommandSet() {
        assertThat(sqlFileNames(LIVE_COMMAND)).containsAll(sqlFileNames(FROZEN_COMMAND));
    }

    @Test
    void frozenQuerySetHasTheSameFileNamesAsTheLiveQuerySet() {
        assertThat(sqlFileNames(FROZEN_QUERY)).isEqualTo(sqlFileNames(LIVE_QUERY));
    }

    @Test
    void everyFrozenPostP7FileIsByteIdenticalToItsLiveFile() {
        assertIdentical(FROZEN_COMMAND, LIVE_COMMAND);
        assertIdentical(FROZEN_QUERY, LIVE_QUERY);
    }

    private static void assertIdentical(String frozenDirectory, String liveDirectory) {
        for (String name : sqlFileNames(frozenDirectory)) {
            assertThat(readResource(frozenDirectory + "/" + name))
                    .as("%s/%s must match the live file byte for byte", frozenDirectory, name)
                    .isEqualTo(readResource(liveDirectory + "/" + name));
        }
    }

    private static List<String> sqlFileNames(String directory) {
        URL url = PostgresMigrationFrozenSetsTest.class.getClassLoader().getResource(directory);
        assertThat(url).as("classpath directory %s", directory).isNotNull();
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("cannot list " + directory, e);
        }
    }

    private static byte[] readResource(String resource) {
        try (InputStream in =
                PostgresMigrationFrozenSetsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("classpath resource %s", resource).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }
}
