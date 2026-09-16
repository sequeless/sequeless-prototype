# Phase 0 acceptance sweep

This document is the honest, executed record of Phase 0's acceptance criteria. Every command below
was actually run against this repository on 2026-09-16 (JDK 25.0.4 Temurin, Maven 3.9.10, Docker
29.5.3 / Docker Compose v5.1.4). Output is pasted verbatim from the terminal, not reconstructed or
tidied. Where a criterion did not hold as originally worded, that is called out explicitly rather
than glossed over — see criterion 2.

## 1. `mvn -B -ntp clean verify` from a clean tree

Command:

```
mvn -B -ntp clean verify
```

Per-module aggregate test counts, taken from the same run:

```
[INFO] Tests run: 37, Failures: 0, Errors: 0, Skipped: 0        -- sequeless-spi
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0         -- sequeless-spi-testkit
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0        -- sequeless-core
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0        -- sequeless-adapter-authz-permitall
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0        -- sequeless-app
```

Total: **85 tests**, 0 failures, 0 errors, 0 skipped, across the reactor.

Reactor summary:

```
[INFO] Reactor Summary for sequeless-parent 0.1.0-SNAPSHOT:
[INFO] 
[INFO] sequeless-parent ................................... SUCCESS [  0.173 s]
[INFO] sequeless-spi ...................................... SUCCESS [  1.894 s]
[INFO] sequeless-spi-testkit .............................. SUCCESS [  0.622 s]
[INFO] sequeless-core ..................................... SUCCESS [  0.636 s]
[INFO] sequeless-adapter-authz-permitall .................. SUCCESS [  0.734 s]
[INFO] sequeless-adapter-ontology-jena .................... SUCCESS [  0.057 s]
[INFO] sequeless-adapter-persistence-postgres ............. SUCCESS [  0.056 s]
[INFO] sequeless-adapter-expression-jexl .................. SUCCESS [  0.058 s]
[INFO] sequeless-adapter-automation-temporal .............. SUCCESS [  0.089 s]
[INFO] sequeless-adapter-automation-inprocess ............. SUCCESS [  0.060 s]
[INFO] sequeless-app ...................................... SUCCESS [  4.651 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  9.171 s
```

**Verdict: MET.** All 11 modules build and all 85 tests pass on a clean tree.

## 2. ArchUnit module-boundary demonstration

**This criterion is more subtle than "add a Spring import to core and watch ArchUnit fail."** We
verified, empirically, that a bare `import org.springframework.stereotype.Component;` added to
`sequeless-core` does **not** reach ArchUnit at all: `sequeless-core` has no Spring on its classpath,
so that import fails to compile, and to even get as far as a compiled class you must first add a
`spring-context` dependency to `sequeless-core/pom.xml`. The moment you do that, the module's
`maven-enforcer-plugin` `bannedDependencies` rule fires at the `validate` phase — before compilation,
before ArchUnit ever runs. This is a *stronger* result than the acceptance criterion asked for: there
are two independent enforcement layers, and both were exercised and captured.

### Layer 1 — Maven Enforcer (`bannedDependencies`), blocks first

Temporary changes (reverted immediately after, see below):
- Added `@Component`/`import org.springframework.stereotype.Component;` to
  `sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultWhoAmI.java`.
- Added an undeclared-version `org.springframework:spring-context` dependency to
  `sequeless-core/pom.xml` (resolved via the root POM's imported `spring-boot-dependencies` BOM),
  leaving the enforcer's `org.springframework*` exclusion in place.

Command:

```
mvn -B -ntp -pl sequeless-core -am validate
```

Verbatim output (failure):

```
[INFO] --------------------< org.sequeless:sequeless-core >--------------------
[INFO] Building sequeless-core 0.1.0-SNAPSHOT                             [3/3]
[INFO]   from sequeless-core/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- enforcer:3.6.3:enforce (enforce-versions) @ sequeless-core ---
[INFO] 
[INFO] --- enforcer:3.6.3:enforce (banned-dependencies) @ sequeless-core ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for sequeless-parent 0.1.0-SNAPSHOT:
[INFO] 
[INFO] sequeless-parent ................................... SUCCESS [  0.155 s]
[INFO] sequeless-spi ...................................... SUCCESS [  0.030 s]
[INFO] sequeless-core ..................................... FAILURE [  0.027 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin:3.6.3:enforce (banned-dependencies) on project sequeless-core: 
[ERROR] Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies failed with message:
[ERROR] org.sequeless:sequeless-core:jar:0.1.0-SNAPSHOT
[ERROR]    org.springframework:spring-context:jar:7.0.9 <--- banned via the exclude/include list
[ERROR]       org.springframework:spring-aop:jar:7.0.9 <--- banned via the exclude/include list
[ERROR]          org.springframework:spring-beans:jar:7.0.9 <--- banned via the exclude/include list
[ERROR]          org.springframework:spring-core:jar:7.0.9 <--- banned via the exclude/include list
[ERROR]       org.springframework:spring-expression:jar:7.0.9 <--- banned via the exclude/include list
[ERROR] -> [Help 1]
```

**Layer 1 verdict: the enforcer blocks the hexagon boundary before ArchUnit is ever invoked.**

### Layer 2 — ArchUnit, blocks the same violation if the enforcer is bypassed

Additional temporary change: commented out the `<exclude>org.springframework*</exclude>` line in
`sequeless-core/pom.xml`'s `banned-dependencies` enforcer execution, keeping the `spring-context`
dependency and the `@Component` annotation from Layer 1 in place.

Command:

```
mvn -B -ntp clean verify
```

Verbatim output (failure, `sequeless-app`'s `ArchitectureTest`):

```
[INFO] Running org.sequeless.app.ArchitectureTest
[ERROR] Tests run: 10, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 0.823 s <<< FAILURE! -- in org.sequeless.app.ArchitectureTest
[ERROR] org.sequeless.app.ArchitectureTest.coreDependsOnlyOnJdkSpiAndItself -- Time elapsed: 0.003 s <<< FAILURE!
java.lang.AssertionError: 
Architecture Violation [Priority: MEDIUM] - Rule 'classes that reside in a package 'org.sequeless.core..' should only depend on classes that reside in any package ['java..', 'org.sequeless.core..', 'org.sequeless.spi..'] or is a primitive or array type (ArchUnit places these in the default package), because sequeless-core holds the use cases and orchestration logic; it may depend on the ports it calls through (sequeless-spi) and on the JDK, but nothing else, or the core would stop being portable across adapter choices.' was violated (1 times):
Class <org.sequeless.core.usecase.DefaultWhoAmI> is annotated with <org.springframework.stereotype.Component> in (DefaultWhoAmI.java:0)

[ERROR] org.sequeless.app.ArchitectureTest.spiAndCoreDoNotDependOnSpring -- Time elapsed: 0.001 s <<< FAILURE!
java.lang.AssertionError: 
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in any package ['org.sequeless.spi..', 'org.sequeless.core..'] should depend on classes that reside in a package 'org.springframework..', because sequeless-spi and sequeless-core form the framework-agnostic hexagon; Spring is a wiring concern that belongs to adapters' auto-configurations and to the app, never to the ports or use cases themselves, or the hexagon could not be run or tested outside a Spring context.' was violated (1 times):
Class <org.sequeless.core.usecase.DefaultWhoAmI> is annotated with <org.springframework.stereotype.Component> in (DefaultWhoAmI.java:0)

[INFO] 
[INFO] Results:
[INFO] 
[ERROR] Failures: 
[ERROR]   ArchitectureTest.coreDependsOnlyOnJdkSpiAndItself Architecture Violation [Priority: MEDIUM] - Rule 'classes that reside in a package 'org.sequeless.core..' should only depend on classes that reside in any package ['java..', 'org.sequeless.core..', 'org.sequeless.spi..'] or is a primitive or array type (ArchUnit places these in the default package), because sequeless-core holds the use cases and orchestration logic; it may depend on the ports it calls through (sequeless-spi) and on the JDK, but nothing else, or the core would stop being portable across adapter choices.' was violated (1 times):
Class <org.sequeless.core.usecase.DefaultWhoAmI> is annotated with <org.springframework.stereotype.Component> in (DefaultWhoAmI.java:0)
[ERROR]   ArchitectureTest.spiAndCoreDoNotDependOnSpring Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in any package ['org.sequeless.spi..', 'org.sequeless.core..'] should depend on classes that reside in a package 'org.springframework..', because sequeless-spi and sequeless-core form the framework-agnostic hexagon; Spring is a wiring concern that belongs to adapters' auto-configurations and to the app, never to the ports or use cases themselves, or the hexagon could not be run or tested outside a Spring context.' was violated (1 times):
Class <org.sequeless.core.usecase.DefaultWhoAmI> is annotated with <org.springframework.stereotype.Component> in (DefaultWhoAmI.java:0)
[INFO] 
[ERROR] Tests run: 19, Failures: 2, Errors: 0, Skipped: 0
[INFO] 
[INFO] Reactor Summary for sequeless-parent 0.1.0-SNAPSHOT:
[INFO] 
[INFO] sequeless-parent ................................... SUCCESS [  0.169 s]
[INFO] sequeless-spi ...................................... SUCCESS [  1.861 s]
[INFO] sequeless-spi-testkit .............................. SUCCESS [  0.618 s]
[INFO] sequeless-core ..................................... SUCCESS [  0.658 s]
[INFO] sequeless-adapter-authz-permitall .................. SUCCESS [  0.690 s]
[INFO] sequeless-adapter-ontology-jena .................... SUCCESS [  0.066 s]
[INFO] sequeless-adapter-persistence-postgres ............. SUCCESS [  0.051 s]
[INFO] sequeless-adapter-expression-jexl .................. SUCCESS [  0.052 s]
[INFO] sequeless-adapter-automation-temporal .............. SUCCESS [  0.066 s]
[INFO] sequeless-adapter-automation-inprocess ............. SUCCESS [  0.051 s]
[INFO] sequeless-app ...................................... FAILURE [  4.384 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
```

Note the rule `coreDependsOnlyOnJdkSpiAndItself` fires too, as a side effect of the same annotation —
it was not specifically targeted, but the violation legitimately falls under it as well.

**Layer 2 verdict: with the enforcer's exclusion disabled, ArchUnit independently catches the exact
same boundary violation.**

### Revert and re-verify green

Both temporary changes were reverted with:

```
git checkout -- sequeless-core/pom.xml sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultWhoAmI.java
```

`git status --porcelain` was empty immediately after the revert (confirmed). A subsequent
`mvn -B -ntp clean verify` from that clean tree reproduced the exact green reactor summary and 85/85
passing tests shown under criterion 1 above (module timings differed by fractions of a second, as
expected from run to run; module order and outcome were identical: all 11 modules SUCCESS, `BUILD
SUCCESS`).

**Verdict: MET, and demonstrated more rigorously than the acceptance criterion required** — both the
Maven Enforcer layer and the ArchUnit layer independently enforce the `sequeless-spi`/`sequeless-core`
"no Spring" boundary, and the repository was left exactly as it started (`git status --porcelain`
empty, build green).

## 3. Unknown adapter name fails startup with a clear banner

Command:

```
java -jar sequeless-app/target/sequeless-app-0.1.0-SNAPSHOT.jar --sequeless.authz.adapter=missing
```

Process exit code: `1`.

Verbatim banner:

```
2026-09-16T05:15:28.791Z  WARN 29853 --- [sequeless-app] [           main] ConfigServletWebServerApplicationContext : Exception encountered during context initialization - cancelling refresh attempt: org.sequeless.app.port.PortBindingException: Property 'sequeless.authz.adapter' is set to 'missing', which is not a known AuthorizationPort adapter. Available adapters: [permit-all]
2026-09-16T05:15:28.794Z  INFO 29853 --- [sequeless-app] [           main] o.apache.catalina.core.StandardService   : Stopping service [Tomcat]
2026-09-16T05:15:28.802Z  INFO 29853 --- [sequeless-app] [           main] .s.b.a.l.ConditionEvaluationReportLogger : 

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
2026-09-16T05:15:28.810Z ERROR 29853 --- [sequeless-app] [           main] o.s.b.d.LoggingFailureAnalysisReporter   : 

***************************
APPLICATION FAILED TO START
***************************

Description:

Property 'sequeless.authz.adapter' is set to 'missing', which is not a known AuthorizationPort adapter. Available adapters: [permit-all]

Action:

Set 'sequeless.authz.adapter' to one of: permit-all
```

**Verdict: MET.** The banner is clear, names the offending property and value, and lists the
available adapters, matching the earlier live capture in finding F38.

## 4. `docker compose up -d` brings up both services healthy

Command:

```
docker compose up -d
```

Then polled `docker compose ps` every 5 seconds. Verbatim output on the second poll (10 seconds
after `up`):

```
NAME                             IMAGE                       COMMAND                  SERVICE    CREATED          STATUS                    PORTS
sequeless-prototype-postgres-1   postgres:18                 "docker-entrypoint.s…"   postgres   10 seconds ago   Up 10 seconds (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
sequeless-prototype-temporal-1   temporalio/temporal:1.9.1   "temporal server sta…"   temporal   10 seconds ago   Up 10 seconds (healthy)   0.0.0.0:7233->7233/tcp, [::]:7233->7233/tcp, 0.0.0.0:8233->8233/tcp, [::]:8233->8233/tcp
```

Then torn down:

```
docker compose down -v
```

```
 Container sequeless-prototype-postgres-1 Stopping 
 Container sequeless-prototype-temporal-1 Stopping 
 Container sequeless-prototype-postgres-1 Stopped 
 Container sequeless-prototype-postgres-1 Removing 
 Container sequeless-prototype-postgres-1 Removed 
 Container sequeless-prototype-temporal-1 Stopped 
 Container sequeless-prototype-temporal-1 Removing 
 Container sequeless-prototype-temporal-1 Removed 
 Network sequeless-prototype_default Removing 
 Volume sequeless-prototype_sequeless-pgdata Removing 
 Volume sequeless-prototype_sequeless-temporal Removing 
 Volume sequeless-prototype_sequeless-pgdata Removed 
 Volume sequeless-prototype_sequeless-temporal Removed 
 Network sequeless-prototype_default Removed
```

**Verdict: MET.** Both `postgres` and `temporal` reported `(healthy)` within 10 seconds of `up -d`,
and `down -v` cleaned up containers, network and volumes with no errors.

## 5. Running app answers `/actuator/health` and `/whoami`

Started the app with no adapter override (default `sequeless.authz.adapter=permit-all` from
`application.yaml`):

```
java -jar sequeless-app/target/sequeless-app-0.1.0-SNAPSHOT.jar
```

```
curl -s -w "\nHTTP_STATUS:%{http_code}\n" localhost:8080/actuator/health
```

```
{"groups":["liveness","readiness"],"status":"UP"}
HTTP_STATUS:200
```

```
curl -s -w "\nHTTP_STATUS:%{http_code}\n" localhost:8080/whoami
```

```
{"tenant":"default","principal":"anonymous","displayName":"Anonymous","roles":[],"decision":{"allowed":true,"reason":"permit-all adapter: all requests are allowed"}}
HTTP_STATUS:200
```

The process was then stopped (`pkill` against the jar's process); confirmed no matching Java process
remained afterward.

**Verdict: MET.** Both endpoints return `200` with the expected JSON shapes; `/whoami` correctly
reports the default tenant/anonymous principal and a `permit-all` allow decision, matching finding
F32's earlier capture.

## 6. Explicit acceptance verdict

| # | Criterion | Verdict |
|---|-----------|---------|
| 1 | `mvn -B -ntp clean verify` succeeds on a clean tree | **MET** — 11/11 modules SUCCESS, 85/85 tests pass |
| 2 | ArchUnit enforces the SPI/core-must-not-depend-on-Spring boundary | **MET** (and exceeded — see write-up above: both the Maven Enforcer and ArchUnit independently catch the violation; repo left clean and green after revert) |
| 3 | An unknown `sequeless.authz.adapter` value fails startup with a clear, actionable banner | **MET** — exit code 1, banner names the bad value and lists `[permit-all]` |
| 4 | `docker compose up -d` brings up PostgreSQL 18 and Temporal, both reporting healthy | **MET** — both healthy within 10s; clean teardown with `down -v` |
| 5 | The running app serves working `/actuator/health` and `/whoami` endpoints | **MET** — both return `200` with correct JSON |

**No criterion failed to hold.** All five Phase 0 acceptance criteria are met, with criterion 2
demonstrated more thoroughly than originally asked (two independent enforcement layers rather than
one) precisely because the naive single-layer demonstration (a bare Spring import in
`sequeless-core`) turns out not to reach ArchUnit at all — it is stopped earlier by the Maven
Enforcer's `bannedDependencies` rule. Both layers were exercised, captured verbatim above, and fully
reverted; the working tree is unmodified except for the addition of this document.
