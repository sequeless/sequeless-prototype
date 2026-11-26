package org.sequeless.app;

import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
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
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.spi.query.IndexPreparationPort;

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
    private static final String BEHAVIOUR_PACKAGE = "org.sequeless.ontology.behaviour..";
    private static final String AUTOMATION_PACKAGE = "org.sequeless.ontology.automation..";
    private static final String QUERY_PACKAGE = "org.sequeless.spi.query..";
    private static final String ONTOLOGY_PACKAGE = "org.sequeless.ontology.api..";
    private static final String ONTOLOGY_SPI_PACKAGE = "org.sequeless.ontology.spi..";
    private static final String ONTOLOGY_SCHEMA_PACKAGE = "org.sequeless.ontology.schema..";
    private static final String HOST_CONFIG_PACKAGE = "org.sequeless.app.config..";
    private static final String TESTKIT_QUERY_PACKAGE = "org.sequeless.testkit.query..";
    private static final String PREPARE = "prepare";
    private static final String INGEST = "ingest";
    private static final String RELAY_PACKAGE = "org.sequeless.app.automation..";
    private static final String RELAY_CLASS = "org.sequeless.app.automation.OutboxRelay";
    private static final String TESTKIT_AUTOMATION_PACKAGE = "org.sequeless.testkit.automation..";

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
            .onlyDependOnClassesThat(resideInAnyPackage(
                            "java..",
                            SPI_PACKAGE,
                            KERNEL_PACKAGE,
                            ONTOLOGY_PACKAGE,
                            ONTOLOGY_SPI_PACKAGE,
                            ONTOLOGY_SCHEMA_PACKAGE,
                            BEHAVIOUR_PACKAGE,
                            AUTOMATION_PACKAGE)
                    .or(PRIMITIVE_OR_ARRAY))
            .because("sequeless-spi is the outermost layer of the hexagon: it must be usable by any "
                    + "adapter or client without pulling in anything beyond the JDK, the released "
                    + "sequeless-kernel types it is built on, the released ontology contracts it names "
                    + "(ontology-core, schema-contract, behaviour-contract and automation-contract, in their "
                    + "api, spi, schema, behaviour and automation packages), and its own port types.");

    /**
     * {@code org.slf4j} (the facade, not any concrete provider) is allowed alongside the JDK: it is
     * a logging API with no implementation of its own and no adapter-specific technology behind it
     * — the first direct SLF4J usage in {@code sequeless-core} was DefaultActionExecutor#applyAction,
     * which moved to sequeless-automation in spec 17 P4; the rule stays for any later one. This is
     * narrower than the blanket {@code java..} exemption: only the {@code org.slf4j} package itself is allowed, so a
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
                            "java..",
                            CORE_PACKAGE,
                            SPI_PACKAGE,
                            KERNEL_PACKAGE,
                            EXPRESSION_PACKAGE,
                            SLF4J_PACKAGE,
                            "org.sequeless.filter.api..",
                            "com.fasterxml.jackson.databind..",
                            ONTOLOGY_PACKAGE,
                            ONTOLOGY_SPI_PACKAGE,
                            ONTOLOGY_SCHEMA_PACKAGE,
                            BEHAVIOUR_PACKAGE,
                            AUTOMATION_PACKAGE)
                    .or(PRIMITIVE_OR_ARRAY))
            .because("sequeless-core holds the use cases and orchestration logic; it may depend on the JDK, "
                    + "the ports it calls through (sequeless-spi), the released sequeless-kernel identity "
                    + "and values types, "
                    + "the released ontology contracts it uses (ontology-core, schema-contract, "
                    + "behaviour-contract and automation-contract, in their api, spi, schema, behaviour and "
                    + "automation packages), "
                    + "the expression api and the SLF4J logging facade (a dependency-free API, not an "
                    + "adapter-specific technology), but nothing else, or the core would stop being portable "
                    + "across adapter choices. The released filter api and Jackson 2's databind package are "
                    + "allowed only because the criteria bridge (FilterNodeToCriterion) reads a rollup's "
                    + "FieldFilter, whose value is a Jackson 2 JsonNode. The released jena adapter and the "
                    + "testfixtures are not allowed, so core stays free of any concrete ontology backend.");

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

    /**
     * The four automation classes that call the Command use cases: the host's ObjectReadPort and StoreCommandPort
     * bindings, the type lookup they share, and the configuration that registers them (spec 17 P3, Q3). No other
     * automation class may reach into core. The outbox relay in particular must not (spec 13 P5b).
     */
    private static final String AUTOMATION_BINDING_CONFIGURATION =
            "org.sequeless.app.automation.AutomationBindingConfiguration";

    private static final String BUSINESS_OBJECT_READ_PORT = "org.sequeless.app.automation.BusinessObjectReadPort";
    private static final String BUSINESS_OBJECT_STORE_COMMAND_PORT =
            "org.sequeless.app.automation.BusinessObjectStoreCommandPort";
    private static final String OBJECT_TYPES = "org.sequeless.app.automation.ObjectTypes";

    @ArchTest
    static final ArchRule onlyAppRestConfigAndAutomationBindingsMayDependOnCore = noClasses()
            .that()
            .resideInAPackage(APP_PACKAGE)
            .and()
            .resideOutsideOfPackages("org.sequeless.app.rest..", "org.sequeless.app.config..")
            .and()
            .doNotHaveFullyQualifiedName(AUTOMATION_BINDING_CONFIGURATION)
            .and()
            .doNotHaveFullyQualifiedName(BUSINESS_OBJECT_READ_PORT)
            .and()
            .doNotHaveFullyQualifiedName(BUSINESS_OBJECT_STORE_COMMAND_PORT)
            .and()
            .doNotHaveFullyQualifiedName(OBJECT_TYPES)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_PACKAGE)
            .because("Within sequeless-app, only the REST inbound adapter, the wiring/configuration packages and "
                    + "the four automation bindings (spec 17 P3: the host's ObjectReadPort and StoreCommandPort call "
                    + "the Command use cases) may reach into core use cases; other app packages, such as the port "
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

    private static final String TEMPORAL_ADAPTER_PACKAGE = "org.sequeless.automation.temporal..";

    private static final String TEMPORAL_AUTOCONFIGURE_PACKAGE = "org.sequeless.automation.autoconfigure..";

    @ArchTest
    static final ArchRule onlyTemporalAdapterMayDependOnTemporal = noClasses()
            .that()
            .resideOutsideOfPackages(TEMPORAL_ADAPTER_PACKAGE, TEMPORAL_AUTOCONFIGURE_PACKAGE)
            .and()
            .doNotHaveFullyQualifiedName("org.sequeless.app.automation.TemporalFailureRecorder")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.temporal..")
            .because("io.temporal is the released automation-temporal-adapter's own implementation library "
                    + "(spec 17 P3: org.sequeless.automation.temporal and its autoconfiguration). Confining it there "
                    + "(stronger than coreDoesNotDependOnAdapterSpecificLibraries, which only guards sequeless-core) "
                    + "keeps every other module free to swap or omit the Temporal adapter. The one exception is the "
                    + "host's TemporalFailureRecorder, which reads Temporal's attempt number inside an activity.");

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
     * Index preparation is a host concern (#24): the host calls {@code IndexPreparationPort#prepare} after every
     * ontology activation, through {@code org.sequeless.app.config}. Use cases and adapters never call it,
     * so no caller can prepare indexes for a Schema that is not the active one.
     */
    @ArchTest
    static final ArchRule onlyHostPreparesIndexes = noClasses()
            .that()
            .resideOutsideOfPackages(HOST_CONFIG_PACKAGE, TESTKIT_QUERY_PACKAGE)
            .should()
            .callMethodWhere(target(name(PREPARE)).and(target(declaredIn(assignableTo(IndexPreparationPort.class)))))
            .because("index preparation after an ontology activation is a host responsibility (#24). Only the "
                    + "host's configuration package may call IndexPreparationPort#prepare in production code, so "
                    + "that the use cases never see the index preparation port for that purpose and the host can "
                    + "swap it in one place. The call is matched on its owner type, so a call through a concrete "
                    + "store such as PostgresQueryStore is caught too. The testkit's query contract kit "
                    + "(org.sequeless.testkit.query) is excluded: it exercises the port contracts and is not a "
                    + "production caller.");

    /**
     * Automation's ingress is fed only by the outbox relay (spec 13 P5b, #20 point 4): no class outside
     * {@code org.sequeless.app.automation} calls {@code AutomationPort#ingest}. The testkit's automation contract
     * ({@code org.sequeless.testkit.automation}) is excluded: it drives the port from the adapters' contract tests
     * and is not a production caller. That exclusion is a deviation from spec line 207, which names only the relay
     * package; the testkit's main code is imported here, so it must be named.
     */
    @ArchTest
    static final ArchRule onlyTheRelayFeedsAutomation = noClasses()
            .that()
            .resideOutsideOfPackages(RELAY_PACKAGE, TESTKIT_AUTOMATION_PACKAGE)
            .should()
            .callMethodWhere(target(name(INGEST)).and(target(declaredIn(assignableTo(AutomationPort.class)))))
            .because("the outbox relay is the only feeder of automation's Store-event ingress (spec 13 P5b, #20 "
                    + "point 4). A second caller would hand automation events the relay never ordered: the relay "
                    + "runs the recompute before ingest, and automation's guards read the materialised values that "
                    + "recompute writes. The call is matched on its owner type, so a call through a concrete "
                    + "adapter is caught too.");

    /**
     * The positive half of {@link #onlyTheRelayFeedsAutomation}: the relay itself calls {@code AutomationPort#ingest},
     * so that rule's predicate matches a real call. If the method were renamed, this rule fails, and the negative
     * rule above does not pass vacuously (spec section 6.2: a new rule must match at least one class).
     */
    @ArchTest
    static final ArchRule theRelayFeedsAutomation = classes()
            .that()
            .haveFullyQualifiedName(RELAY_CLASS)
            .should()
            .callMethodWhere(target(name(INGEST)).and(target(declaredIn(assignableTo(AutomationPort.class)))));

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
        assertThat(classes.get("org.sequeless.ontology.jena.JenaOntologyPort").getFullName())
                .isEqualTo("org.sequeless.ontology.jena.JenaOntologyPort");
    }
}
