package org.sequeless.adapter.ontology.jena;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Proves {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * names a real, correctly annotated auto-configuration class — via reflection on the annotations
 * only, without starting a Spring context. Pulling in {@code spring-boot-test} to run an actual
 * {@code ApplicationContextRunner} check here would be disproportionate for a module this
 * deliberately minimal (mirroring {@code sequeless-adapter-authz-permitall}'s {@code
 * PermitAllAuthorizationAutoConfigurationTest}); that end-to-end proof belongs to the application
 * module that assembles adapters together.
 */
class JenaOntologyAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void importsFileNamesACorrectlyAnnotatedAutoConfiguration() throws IOException, ClassNotFoundException {
        List<String> classNames = readNonBlankLines(IMPORTS_RESOURCE);
        assertThat(classNames).hasSize(1);

        Class<?> autoConfigurationClass = Class.forName(classNames.get(0));
        assertThat(autoConfigurationClass).isEqualTo(JenaOntologyAutoConfiguration.class);

        ConditionalOnProperty condition = autoConfigurationClass.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("sequeless.ontology.adapter");
        assertThat(condition.havingValue()).isEqualTo("jena");

        EnableConfigurationProperties propertiesBinding =
            autoConfigurationClass.getAnnotation(EnableConfigurationProperties.class);
        assertThat(propertiesBinding).isNotNull();
        assertThat(propertiesBinding.value()).containsExactly(JenaOntologyProperties.class);
    }

    private static List<String> readNonBlankLines(String resource) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in =
                JenaOntologyAutoConfigurationTest.class.getClassLoader().getResourceAsStream(resource);
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
