package org.sequeless.app;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Encodes the Maven module boundary rules from {@code docs/architecture/01-overview.md} as
 * executable ArchUnit rules, so a dependency that violates the hexagon fails the build instead of
 * drifting in unnoticed.
 *
 * <p>{@code importOptions = ImportOption.DoNotIncludeTests.class} only. {@code DoNotIncludeJars}
 * must NOT be added: every module other than {@code sequeless-app} reaches this test's classpath
 * solely as a reactor jar (they are regular compile dependencies of this module, not sources), so
 * excluding jar-origin classes would leave this suite analyzing only {@code sequeless-app}'s own
 * sources and silently disable rules 1, 2, 3 (for spi/core), 6, 7, 8 and 9 — the suite would stay
 * green over a completely broken core. {@link #suiteActuallyAnalyzesEveryModule(JavaClasses)}
 * guards against exactly that regression.
 */
@AnalyzeClasses(packages = "org.sequeless", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String SPI_PACKAGE = "org.sequeless.spi..";
    private static final String CORE_PACKAGE = "org.sequeless.core..";
    private static final String APP_PACKAGE = "org.sequeless.app..";
    private static final String ADAPTER_PACKAGE = "org.sequeless.adapter..";

    /**
     * ArchUnit places primitive types ({@code int}, {@code boolean}, {@code void}, ...) and array
     * types in the default (empty) package, since they have no real package of their own. A rule
     * built from {@code resideOutsideOfPackages("java..", ...)} would therefore misreport, e.g., an
     * {@code int} parameter as a dependency "outside java..". This predicate widens the allowed set
     * to include those synthetic default-package types instead of weakening the rule itself.
     */
    private static final DescribedPredicate<JavaClass> PRIMITIVE_OR_ARRAY =
        DescribedPredicate.describe(
            "is a primitive or array type (ArchUnit places these in the default package)",
            javaClass -> javaClass.isPrimitive() || javaClass.isArray());

    @ArchTest
    static final ArchRule spiDependsOnlyOnJdkAndItself =
        classes()
            .that()
            .resideInAPackage(SPI_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                resideInAnyPackage("java..", SPI_PACKAGE).or(PRIMITIVE_OR_ARRAY))
            .because(
                "sequeless-spi is the outermost, dependency-free layer of the hexagon: it must be "
                    + "usable by any adapter or client without pulling in anything beyond the JDK "
                    + "and its own port and value types.");

    /**
     * {@code org.slf4j} (the facade, not any concrete provider) is allowed alongside the JDK: it is
     * a logging API with no implementation of its own and no adapter-specific technology behind it
     * — {@code org.sequeless.core.automation.DefaultActionExecutor#applyLog} is the first, but not
     * necessarily the last, direct SLF4J usage in {@code sequeless-core}. This is narrower than the
     * blanket {@code java..} exemption: only the {@code org.slf4j} package itself is allowed, so a
     * concrete logging backend (Logback, Log4j2, ...) pulled in transitively would still violate
     * this rule if referenced directly from core.
     */
    private static final String SLF4J_PACKAGE = "org.slf4j";

    @ArchTest
    static final ArchRule coreDependsOnlyOnJdkSpiAndItself =
        classes()
            .that()
            .resideInAPackage(CORE_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                resideInAnyPackage(
                        "java..", CORE_PACKAGE, SPI_PACKAGE, SLF4J_PACKAGE)
                    .or(PRIMITIVE_OR_ARRAY))
            .because(
                "sequeless-core holds the use cases and orchestration logic; it may depend on the "
                    + "ports it calls through (sequeless-spi), the JDK, and the SLF4J logging facade "
                    + "(a dependency-free API, not an adapter-specific technology), but nothing "
                    + "else, or the core would stop being portable across adapter choices.");

    @ArchTest
    static final ArchRule spiAndCoreDoNotDependOnSpring =
        noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .because(
                "sequeless-spi and sequeless-core form the framework-agnostic hexagon; Spring is a "
                    + "wiring concern that belongs to adapters' auto-configurations and to the app, "
                    + "never to the ports or use cases themselves, or the hexagon could not be run "
                    + "or tested outside a Spring context.");

    @ArchTest
    static final ArchRule onlyAppMayDependOnSpringMvc =
        noClasses()
            .that()
            .resideOutsideOfPackage(APP_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.web..")
            .because(
                "Spring MVC is the inbound HTTP concern of the sequeless-app module alone; adapters "
                    + "remain free to use org.springframework.boot.autoconfigure.. and "
                    + "org.springframework.context.. to wire themselves, but must never depend on "
                    + "the web layer.");

    @ArchTest
    static final ArchRule onlyAppRestAndConfigMayDependOnCore =
        noClasses()
            .that()
            .resideInAPackage(APP_PACKAGE)
            .and()
            .resideOutsideOfPackages("org.sequeless.app.rest..", "org.sequeless.app.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_PACKAGE)
            .because(
                "Within sequeless-app, only the REST inbound adapter and the wiring/configuration "
                    + "packages may reach into core use cases; other app packages, such as the port "
                    + "registry, must stay ignorant of the domain core.");

    @ArchTest
    static final ArchRule noAdapterDependsOnCore =
        noClasses()
            .that()
            .resideInAPackage(ADAPTER_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_PACKAGE)
            .because(
                "Adapters are outbound implementations of SPI ports; depending on core use cases "
                    + "would invert the hexagon's dependency direction and create a cycle between "
                    + "the core and its own adapters.");

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther =
        slices()
            .matching("org.sequeless.adapter.(*)..")
            .should()
            .notDependOnEachOther()
            .because(
                "Each adapter implements its own port independently and is selected at runtime by "
                    + "a configuration property; adapters depending on one another would create "
                    + "hidden coupling and defeat the point of swappable, single-purpose adapters.");

    @ArchTest
    static final ArchRule noLombokInSpiOrCore =
        noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("lombok..")
            .because(
                "sequeless-spi and sequeless-core are plain, Lombok-free Java by design (no Lombok "
                    + "compiler plugin is configured for either module); adapters may use Lombok to "
                    + "cut boilerplate, but the hexagon's core layers may not depend on it.");

    @ArchTest
    static final ArchRule coreDoesNotDependOnAdapterSpecificLibraries =
        noClasses()
            .that()
            .resideInAPackage(CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.apache.jena..", "java.sql..", "javax.sql..", "io.temporal..")
            .because(
                "sequeless-core must stay ignorant of which concrete technology backs each port at "
                    + "runtime; referencing Jena, JDBC or Temporal types directly would leak adapter "
                    + "implementation details into the use cases the ports exist to hide.");

    @ArchTest
    static final ArchRule noJenaInSpiOrCore =
        noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.apache.jena..")
            .because(
                "org.apache.jena is sequeless-adapter-ontology-jena's own implementation library, "
                    + "not part of the framework-agnostic hexagon. The meta-model the core consumes "
                    + "is deliberately a set of plain SPI records reached through OntologyPort, so "
                    + "that replacing Jena with another RDF toolkit, or with a static test double, "
                    + "stays an adapter-level decision. This is stated separately from the broader "
                    + "coreDoesNotDependOnAdapterSpecificLibraries rule above because it also "
                    + "covers sequeless-spi, where a leaked Jena type would be worse still: it "
                    + "would force every adapter of every port to drag Jena onto its classpath.");

    /**
     * Proves this suite is analyzing real classes from every module rather than passing
     * vacuously. If a future classpath or packaging change ever hollowed out the import (for
     * example, an accidental {@code DoNotIncludeJars}, or a dependency becoming {@code provided}
     * and dropping out of the test classpath), rules 1, 2, 3, 6, 7, 8 and 9 above would stop
     * seeing the classes they are meant to police and silently pass on an empty set. {@link
     * JavaClasses#get(String)} throws if the named class is not part of the imported set, so this
     * fails loudly instead.
     */
    @ArchTest
    static void suiteActuallyAnalyzesEveryModule(JavaClasses classes) {
        assertThat(classes.get("org.sequeless.spi.Scope").getFullName())
            .isEqualTo("org.sequeless.spi.Scope");
        assertThat(classes.get("org.sequeless.core.usecase.DefaultWhoAmI").getFullName())
            .isEqualTo("org.sequeless.core.usecase.DefaultWhoAmI");
        assertThat(
                classes
                    .get("org.sequeless.adapter.authz.permitall.PermitAllAuthorizationPort")
                    .getFullName())
            .isEqualTo("org.sequeless.adapter.authz.permitall.PermitAllAuthorizationPort");
        assertThat(
                classes.get("org.sequeless.adapter.ontology.jena.JenaOntologyPort").getFullName())
            .isEqualTo("org.sequeless.adapter.ontology.jena.JenaOntologyPort");
    }
}
