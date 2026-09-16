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
 * only, without starting a Spring context. Pulling in {@code spring-boot-test} to run an actual
 * {@code ApplicationContextRunner} check here would be disproportionate for a module this
 * deliberately minimal; that end-to-end proof belongs to the application module that assembles
 * adapters together (see the step plan's T9/T11).
 */
class PermitAllAuthorizationAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void importsFileNamesACorrectlyAnnotatedAutoConfiguration() throws IOException, ClassNotFoundException {
        List<String> classNames = readNonBlankLines(IMPORTS_RESOURCE);
        assertThat(classNames).hasSize(1);

        Class<?> autoConfigurationClass = Class.forName(classNames.get(0));
        assertThat(autoConfigurationClass).isEqualTo(PermitAllAuthorizationAutoConfiguration.class);

        ConditionalOnProperty condition =
            autoConfigurationClass.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("sequeless.authz.adapter");
        assertThat(condition.havingValue()).isEqualTo("permit-all");
    }

    private static List<String> readNonBlankLines(String resource) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in =
                PermitAllAuthorizationAutoConfigurationTest.class
                    .getClassLoader()
                    .getResourceAsStream(resource);
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
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
