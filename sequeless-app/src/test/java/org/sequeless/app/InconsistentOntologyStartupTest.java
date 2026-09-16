package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.ontology.OntologyException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Proves the Ontology phase's other end-to-end acceptance criterion (criterion 2): an inconsistent
 * ontology fails application startup, naming the offending class, rather than booting into a state
 * where the inconsistency would only surface on the first {@code GET /types} request.
 *
 * <p>{@link org.sequeless.app.config.OntologyStartupValidator} is what makes this observable at
 * all: {@code CoreConfiguration#metaModelService} injects {@code OntologyPort} lazily (see its own
 * javadoc), so without that validator nothing would call {@code OntologyPort#snapshot} until a real
 * request arrived. See its javadoc for why an {@code ApplicationRunner} is the right place for that
 * first eager call.
 *
 * <p>Follows {@link PortBindingStartupFailureTest}'s pattern exactly, for the same reasons that
 * class's javadoc documents in detail:
 *
 * <ul>
 *   <li>No {@code @SpringBootTest} — that annotation has no clean way to hand a startup failure
 *       back for inspection, it just fails the test itself.
 *   <li>{@link SpringApplicationBuilder} driven directly with {@link WebApplicationType#NONE}, so
 *       the failure surfaces as a plain exception this test can catch and walk.
 *   <li>The override is passed as a command-line argument, not via {@code
 *       SpringApplicationBuilder.properties(...)}: the latter is the <em>lowest</em>-precedence
 *       property source, so it would be silently overridden by {@code application.yaml}'s own
 *       {@code sequeless.ontology.source=classpath:ontology/reference.ttl} default and the
 *       application would start successfully against the consistent reference ontology instead of
 *       failing against the inconsistent fixture.
 * </ul>
 *
 * <p>{@code sequeless.ontology.reasoner} is left at {@code application.yaml}'s shipped {@code owl}
 * default deliberately: {@code owl} is the only reasoner setting that detects this fixture's
 * inconsistency at all (see {@code JenaOntologyProperties}'s javadoc), so overriding it here would
 * defeat the point of the test.
 */
class InconsistentOntologyStartupTest {

    @Test
    void inconsistentOntologySourceFailsStartupNamingTheCulprit() {
        assertThatThrownBy(
                        () ->
                                new SpringApplicationBuilder(SequelessApplication.class)
                                        .web(WebApplicationType.NONE)
                                        .run(
                                                "--sequeless.ontology.source=classpath:ontology/inconsistent.ttl"))
                .satisfies(
                        thrown -> {
                            OntologyException cause = findCause(thrown, OntologyException.class);
                            assertThat(cause).isNotNull();
                            assertThat(cause.getMessage()).contains("Cyborg");
                            assertThat(cause.report().consistent()).isFalse();
                        });
    }

    private static <T extends Throwable> T findCause(Throwable root, Class<T> type) {
        Throwable current = root;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
