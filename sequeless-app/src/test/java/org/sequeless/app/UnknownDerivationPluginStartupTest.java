package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.spi.ontology.OntologyException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Proves Phase 4's other end-to-end acceptance criterion: an ontology declaring a {@code
 * sq:pluginName} with no registered {@code DerivationPlugin} on the classpath fails application
 * startup, naming the unresolved plug-in, rather than booting into a state where the failure would
 * only surface the first time a derived value needed computing.
 *
 * <p>{@code unknown-plugin.ttl} (from {@code sequeless-spi-testkit}, a test-scope dependency of
 * this module) is {@code reference.ttl} plus one extra derived property, {@code ex:workload} on
 * {@code ex:Person}, whose {@code sq:pluginName} is {@code "does-not-exist"} — a name no {@code
 * META-INF/services/org.sequeless.spi.derivation.DerivationPlugin} entry on this classpath
 * provides. {@code JenaOntologyPort.buildStateFrom}'s {@code ServiceLoader}-backed plug-in name
 * check (T4) is what makes this observable at all: it runs on every activation path, including
 * this one, the application's own eager startup load.
 *
 * <p>Follows {@link InconsistentOntologyStartupTest}'s exact pattern, for the reasons that class's
 * own javadoc documents in detail:
 *
 * <ul>
 *   <li>No {@code @SpringBootTest} — that annotation has no clean way to hand a startup failure
 *       back for inspection, it just fails the test itself.
 *   <li>{@link SpringApplicationBuilder} driven directly with {@link WebApplicationType#NONE}, so
 *       the failure surfaces as a plain exception this test can catch and walk.
 *   <li>The {@code sequeless.ontology.source} override is passed as a command-line argument, not
 *       via {@code SpringApplicationBuilder.properties(...)}: the latter is the <em>lowest</em>-
 *       precedence property source, so it would be silently overridden by {@code
 *       application.yaml}'s own {@code sequeless.ontology.source=classpath:ontology/reference.ttl}
 *       default and the application would start successfully against the consistent reference
 *       ontology instead of failing.
 * </ul>
 *
 * <p>Extends {@link PostgresTestcontainersSupport} and passes its container's connection details
 * as command-line arguments, for the same reason {@link InconsistentOntologyStartupTest} does:
 * {@code sequeless.persistence.adapter=postgres} is baked into {@code application.yaml}, so this
 * manually built {@link SpringApplicationBuilder} also needs a reachable Postgres before it can
 * even get to the ontology load this test cares about.
 */
class UnknownDerivationPluginStartupTest extends PostgresTestcontainersSupport {

    @Test
    void unknownPluginNameFailsStartupNamingIt() {
        assertThatThrownBy(
                        () ->
                                new SpringApplicationBuilder(SequelessApplication.class)
                                        .web(WebApplicationType.NONE)
                                        .run(
                                                "--sequeless.ontology.source=classpath:ontology/unknown-plugin.ttl",
                                                "--sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                                                "--sequeless.persistence.username=" + POSTGRES.getUsername(),
                                                "--sequeless.persistence.password=" + POSTGRES.getPassword()))
                .satisfies(
                        thrown -> {
                            OntologyException cause = findCause(thrown, OntologyException.class);
                            assertThat(cause).isNotNull();
                            assertThat(cause.getMessage())
                                    .contains("does-not-exist")
                                    .contains("has no registered DerivationPlugin on the classpath");
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
