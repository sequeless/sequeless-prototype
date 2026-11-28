package org.sequeless.adapter.authz.permitall;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Proves {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * names a real, correctly annotated auto-configuration class — via reflection on the annotation
 * only, without starting a Spring context.
 *
 * <p>Since spec 18 U12c the imports file is shared with the Postgres and Jena wiring, so this test filters to the
 * {@code org.sequeless.adapter.authz.permitall} package and asserts exactly the one permit-all entry, checked by name.
 * A missing or duplicated permit-all entry still fails. The end-to-end proof is {@code PortRegistryContextRunnerTest}.
 */
class PermitAllAuthorizationAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String PERMIT_ALL_PACKAGE_PREFIX = "org.sequeless.adapter.authz.permitall.";

    @Test
    void importsFileNamesACorrectlyAnnotatedAutoConfiguration() throws IOException {
        List<String> classNames = readNonBlankLines(IMPORTS_RESOURCE).stream()
                .filter(name -> name.startsWith(PERMIT_ALL_PACKAGE_PREFIX))
                .toList();
        assertThat(classNames).containsExactly(PermitAllAuthorizationAutoConfiguration.class.getName());

        Class<?> autoConfigurationClass = PermitAllAuthorizationAutoConfiguration.class;

        ConditionalOnProperty condition = autoConfigurationClass.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("sequeless.authz.adapter");
        assertThat(condition.havingValue()).isEqualTo("permit-all");
    }

    private static List<String> readNonBlankLines(String resource) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = PermitAllAuthorizationAutoConfigurationTest.class
                        .getClassLoader()
                        .getResourceAsStream(resource);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }
}
