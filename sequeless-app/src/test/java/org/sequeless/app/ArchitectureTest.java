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
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.query.QueryPort;

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
    private static final String KERNEL_PACKAGE = "org.sequeless.kernel..";
    private static final String EXPRESSION_PACKAGE = "org.sequeless.expression..";
    private static final String SCHEMA_PACKAGE = "..spi.meta.schema..";
    private static final String BEHAVIOUR_PACKAGE = "..spi.meta.behaviour..";
    private static final String AUTOMATION_PACKAGE = "..spi.meta.automation..";
    private static final String QUERY_PACKAGE = "org.sequeless.spi.query..";
    private static final String ONTOLOGY_PACKAGE = "org.sequeless.spi.ontology..";
    private static final String HOST_CONFIG_PACKAGE = "org.sequeless.app.config..";
    private static final String TESTKIT_PACKAGE = "org.sequeless.testkit..";

    /**
     * ArchUnit places primitive types ({@code int}, {@code boolean}, {@code void}, ...) and array
     * types in the default (empty) package, since they have no real package of their own. A rule
     * built from {@code resideOutsideOfPackages("java..", ...)} would therefore misreport, e.g., an
     * {@code int} parameter as a dependency "outside java..". This predicate widens the allowed set
     * to include those synthetic default-package types instead of weakening the rule itself.
     */
    private static final DescribedPredicate<JavaClass> PRIMITIVE_OR_ARRAY = DescribedPredicate.describe(
            "is a primitive or array type (ArchUnit places these in the default package)",
            javaClass -> javaClass.isPrimitive() || javaClass.isArray());

    @ArchTest
    static final ArchRule spiDependsOnlyOnJdkAndItself = classes()
            .that()
            .resideInAPackage(SPI_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                    resideInAnyPackage("java..", SPI_PACKAGE, KERNEL_PACKAGE).or(PRIMITIVE_OR_ARRAY))
            .because("sequeless-spi is the outermost layer of the hexagon: it must be usable by any "
                    + "adapter or client without pulling in anything beyond the JDK, the released "
                    + "sequeless-kernel types it is built on, and its own port types.");

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
    static final ArchRule coreDependsOnlyOnJdkSpiAndItself = classes()
            .that()
            .resideInAPackage(CORE_PACKAGE)
            .should()
            .onlyDependOnClassesThat(resideInAnyPackage(
                            "java..", CORE_PACKAGE, SPI_PACKAGE, KERNEL_PACKAGE, EXPRESSION_PACKAGE, SLF4J_PACKAGE)
                    .or(PRIMITIVE_OR_ARRAY))
            .because("sequeless-core holds the use cases and orchestration logic; it may depend on the "
                    + "ports it calls through (sequeless-spi), the JDK, and the SLF4J logging facade "
                    + "(a dependency-free API, not an adapter-specific technology), but nothing "
                    + "else, or the core would stop being portable across adapter choices.");

    @ArchTest
    static final ArchRule spiAndCoreDoNotDependOnSpring = noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .because("sequeless-spi and sequeless-core form the framework-agnostic hexagon; Spring is a "
                    + "wiring concern that belongs to adapters' auto-configurations and to the app, "
                    + "never to the ports or use cases themselves, or the hexagon could not be run "
                    + "or tested outside a Spring context.");

    @ArchTest
    static final ArchRule onlyAppMayDependOnSpringMvc = noClasses()
            .that()
            .resideOutsideOfPackage(APP_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.web..")
            .because("Spring MVC is the inbound HTTP concern of the sequeless-app module alone; adapters "
                    + "remain free to use org.springframework.boot.autoconfigure.. and "
                    + "org.springframework.context.. to wire themselves, but must never depend on "
                    + "the web layer.");

    @ArchTest
    static final ArchRule onlyAppRestAndConfigMayDependOnCore = noClasses()
            .that()
            .resideInAPackage(APP_PACKAGE)
            .and()
            .resideOutsideOfPackages("org.sequeless.app.rest..", "org.sequeless.app.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_PACKAGE)
            .because("Within sequeless-app, only the REST inbound adapter and the wiring/configuration "
                    + "packages may reach into core use cases; other app packages, such as the port "
                    + "registry, must stay ignorant of the domain core.");

    @ArchTest
    static final ArchRule noAdapterDependsOnCore = noClasses()
            .that()
            .resideInAPackage(ADAPTER_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_PACKAGE)
            .because("Adapters are outbound implementations of SPI ports; depending on core use cases "
                    + "would invert the hexagon's dependency direction and create a cycle between "
                    + "the core and its own adapters.");

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther = slices().matching("org.sequeless.adapter.(*)..")
            .should()
            .notDependOnEachOther()
            .because("Each adapter implements its own port independently and is selected at runtime by "
                    + "a configuration property; adapters depending on one another would create "
                    + "hidden coupling and defeat the point of swappable, single-purpose adapters.");

    @ArchTest
    static final ArchRule noLombokInSpiOrCore = noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("lombok..")
            .because("sequeless-spi and sequeless-core are plain, Lombok-free Java by design (no Lombok "
                    + "compiler plugin is configured for either module); adapters may use Lombok to "
                    + "cut boilerplate, but the hexagon's core layers may not depend on it.");

    @ArchTest
    static final ArchRule coreDoesNotDependOnAdapterSpecificLibraries = noClasses()
            .that()
            .resideInAPackage(CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.apache.jena..", "java.sql..", "javax.sql..", "io.temporal..")
            .because("sequeless-core must stay ignorant of which concrete technology backs each port at "
                    + "runtime; referencing Jena, JDBC or Temporal types directly would leak adapter "
                    + "implementation details into the use cases the ports exist to hide.");

    private static final String TEMPORAL_ADAPTER_PACKAGE = "org.sequeless.adapter.automation.temporal..";

    @ArchTest
    static final ArchRule onlyTemporalAdapterMayDependOnTemporal = noClasses()
            .that()
            .resideOutsideOfPackage(TEMPORAL_ADAPTER_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.temporal..")
            .because("io.temporal is sequeless-adapter-automation-temporal's own implementation library; "
                    + "confining it there (stronger than coreDoesNotDependOnAdapterSpecificLibraries, "
                    + "which only guards sequeless-core) keeps every other module free to swap or omit "
                    + "the Temporal adapter without dragging its SDK onto an unrelated classpath.");

    @ArchTest
    static final ArchRule noJenaInSpiOrCore = noClasses()
            .that()
            .resideInAnyPackage(SPI_PACKAGE, CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.apache.jena..")
            .because("org.apache.jena is sequeless-adapter-ontology-jena's own implementation library, "
                    + "not part of the framework-agnostic hexagon. The meta-model the core consumes "
                    + "is deliberately a set of plain SPI records reached through the SPI read ports, so "
                    + "that replacing Jena with another RDF toolkit, or with a static test double, "
                    + "stays an adapter-level decision. This is stated separately from the broader "
                    + "coreDoesNotDependOnAdapterSpecificLibraries rule above because it also "
                    + "covers sequeless-spi, where a leaked Jena type would be worse still: it "
                    + "would force every adapter of every port to drag Jena onto its classpath.");

    @ArchTest
    static final ArchRule schemaDependsOnlyOnJdkKernelAndItself = classes()
            .that()
            .resideInAPackage(SCHEMA_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                    resideInAnyPackage("java..", SCHEMA_PACKAGE, KERNEL_PACKAGE).or(PRIMITIVE_OR_ARRAY))
            .because("the Schema half is the base of the meta-model: it may depend on the JDK, the kernel "
                    + "identity and value types, and its own package, and nothing else, so that the "
                    + "Behaviour and Automation halves can depend on it without a cycle.");

    @ArchTest
    static final ArchRule behaviourDependsOnlyOnSchema = classes()
            .that()
            .resideInAPackage(BEHAVIOUR_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                    resideInAnyPackage("java..", BEHAVIOUR_PACKAGE, SCHEMA_PACKAGE, KERNEL_PACKAGE, QUERY_PACKAGE)
                            .or(PRIMITIVE_OR_ARRAY))
            .because("the Behaviour half may depend on the Schema half, the kernel and org.sequeless.spi.query, "
                    + "and on nothing else. The query dependency is RollupRule's Criterion, which stays in "
                    + "spi.query until spec 14 replaces it (spec 10, section 11, question 1). Any other "
                    + "dependency would couple state machines and derivations to the adapters or ports that "
                    + "the Behaviour half is meant to describe, not to host.");

    @ArchTest
    static final ArchRule automationDependsOnlyOnBehaviourAndSchema = classes()
            .that()
            .resideInAPackage(AUTOMATION_PACKAGE)
            .should()
            .onlyDependOnClassesThat(
                    resideInAnyPackage("java..", AUTOMATION_PACKAGE, BEHAVIOUR_PACKAGE, SCHEMA_PACKAGE, KERNEL_PACKAGE)
                            .or(PRIMITIVE_OR_ARRAY))
            .because("the Automation half holds the transition actions and may depend on the Behaviour and "
                    + "Schema halves and the kernel, and on nothing else. A dependency on a port or an adapter "
                    + "would make the action list depend on how it is executed, which spec 15 must not inherit.");

    @ArchTest
    static final ArchRule querySeesOnlySchema = noClasses()
            .that()
            .resideInAPackage(QUERY_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(BEHAVIOUR_PACKAGE, AUTOMATION_PACKAGE, ONTOLOGY_PACKAGE)
            .because("the query port runs against one Schema and nothing else: it must not see the Behaviour or "
                    + "Automation halves, or the ontology administration port that activates them (#13 "
                    + "decision 3). The Schema is the only part of the type system a query needs.");

    /**
     * Index preparation is a host concern (#24): the host calls {@code QueryPort#ensureIndexes} after every
     * ontology activation, through {@code org.sequeless.app.config}. Use cases and adapters never call it,
     * so no caller can prepare indexes for a Schema that is not the active one.
     */
    @ArchTest
    static final ArchRule onlyHostPreparesIndexes = noClasses()
            .that()
            .resideOutsideOfPackages(HOST_CONFIG_PACKAGE, TESTKIT_PACKAGE)
            .should()
            .callMethod(QueryPort.class, "ensureIndexes", Scope.class, Schema.class)
            .because("index preparation after an ontology activation is a host responsibility (#24). Only the "
                    + "host's configuration package may call QueryPort#ensureIndexes in production code, so that "
                    + "the use cases never see QueryPort for that purpose and the host can swap it to the "
                    + "prepare(tenant, version) call of spec 13 in one place. The testkit's contract kit "
                    + "(org.sequeless.testkit) is excluded: it exercises the port's own contract, including the "
                    + "null-argument clause, and is not a production caller.");

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
        assertThat(classes.get("org.sequeless.kernel.identity.Scope").getFullName())
                .isEqualTo("org.sequeless.kernel.identity.Scope");
        assertThat(classes.get("org.sequeless.core.usecase.DefaultWhoAmI").getFullName())
                .isEqualTo("org.sequeless.core.usecase.DefaultWhoAmI");
        assertThat(classes.get("org.sequeless.adapter.authz.permitall.PermitAllAuthorizationPort")
                        .getFullName())
                .isEqualTo("org.sequeless.adapter.authz.permitall.PermitAllAuthorizationPort");
        assertThat(classes.get("org.sequeless.adapter.ontology.jena.JenaOntologyPort")
                        .getFullName())
                .isEqualTo("org.sequeless.adapter.ontology.jena.JenaOntologyPort");
    }
}
