package org.sequeless.adapter.ontology.jena;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a configured ontology source location into a readable stream. Supports the {@code
 * classpath:} and {@code file:} prefixes only; anything else, or a location that does not resolve
 * to an existing resource, fails with a message naming both the offending configuration property
 * and the unresolved location, so a misconfigured {@code sequeless.ontology.source} is diagnosable
 * from the exception message alone.
 *
 * <p>Package-private: only {@link JenaOntologyPort} (via its {@code fromSource} factory) needs
 * this.
 */
final class OntologySource {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";

    private OntologySource() {}

    /**
     * Opens {@code location} for reading.
     *
     * @param propertyName the configuration property {@code location} came from, used only to
     *     produce a clear error message; must not be {@code null}
     * @param location the location to resolve, prefixed with {@code classpath:} or {@code file:};
     *     must not be {@code null}
     * @return a stream positioned at the start of the resolved resource; the caller owns it and
     *     must close it
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code location} uses neither supported prefix, or the
     *     resource it names cannot be opened
     */
    static InputStream open(String propertyName, String location) {
        Objects.requireNonNull(propertyName, "propertyName must not be null");
        Objects.requireNonNull(location, "location must not be null");

        if (location.startsWith(CLASSPATH_PREFIX)) {
            String resourcePath = location.substring(CLASSPATH_PREFIX.length());
            InputStream stream = OntologySource.class.getClassLoader().getResourceAsStream(resourcePath);
            if (stream == null) {
                throw unresolved(propertyName, location, "no such classpath resource");
            }
            return stream;
        }

        if (location.startsWith(FILE_PREFIX)) {
            Path path = Path.of(location.substring(FILE_PREFIX.length()));
            try {
                return Files.newInputStream(path);
            } catch (NoSuchFileException e) {
                throw unresolved(propertyName, location, "no such file");
            } catch (IOException e) {
                throw unresolved(propertyName, location, e.getMessage());
            }
        }

        throw unresolved(propertyName, location, "expected a 'classpath:' or 'file:' prefix");
    }

    private static IllegalArgumentException unresolved(String propertyName, String location, String reason) {
        return new IllegalArgumentException(
            "%s: could not resolve ontology source '%s' (%s)".formatted(propertyName, location, reason));
    }
}
