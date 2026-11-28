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
 * deliberately minimal (mirroring the permit-all wiring's {@code
 * PermitAllAuthorizationAutoConfigurationTest}); that end-to-end proof belongs to the application
 * module that assembles adapters together.
 *
 * <p>Since T8, the Jena wiring names two auto-configurations ({@link JenaOntologyAutoConfiguration} and
 * {@link ShaclValidationAutoConfiguration}). Since spec 18 U12b the imports file is shared with the
 * Postgres wiring, so this test filters to the {@code org.sequeless.adapter.ontology.jena} package and
 * asserts exactly those two entries, checked by name (order-independent) rather than {@code get(0)}.
 * A future third Jena entry still fails loudly here.
 */
class JenaOntologyAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String JENA_PACKAGE_PREFIX = "org.sequeless.adapter.ontology.jena.";

    @Test
    void importsFileNamesACorrectlyAnnotatedAutoConfiguration() throws IOException {
        List<String> classNames = readNonBlankLines(IMPORTS_RESOURCE).stream()
                .filter(name -> name.startsWith(JENA_PACKAGE_PREFIX))
                .toList();
        assertThat(classNames)
                .containsExactlyInAnyOrder(
                        JenaOntologyAutoConfiguration.class.getName(),
                        ShaclValidationAutoConfiguration.class.getName());

        Class<?> autoConfigurationClass = JenaOntologyAutoConfiguration.class;

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
