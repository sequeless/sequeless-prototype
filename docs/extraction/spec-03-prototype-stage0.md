# Spec 3 — Prototype stage 0: adopt the sequeless-build parent

Issue: [#31](https://github.com/sequeless/sequeless-prototype/issues/31). Part of the map
[Decompose the prototype into a CQRS repo layout](https://github.com/sequeless/sequeless-prototype/issues/11), spec 3 of 19.

This spec changes the prototype itself, so it lives here, at `docs/extraction/spec-03-prototype-stage0.md`,
and stays here.

Decisions this spec carries out, and must not reopen:

- [Build baseline across repos (#17)](https://github.com/sequeless/sequeless-prototype/issues/17), §3 in particular: "the parent POM never imports `spring-boot-dependencies` … The prototype adopts our parent".
- [What stays in the prototype (#19)](https://github.com/sequeless/sequeless-prototype/issues/19): the repo keeps its name and stays one deployable host.
- [Order of the extraction plan and how git history is kept (#23)](https://github.com/sequeless/sequeless-prototype/issues/23), including its "Update after the `sequeless-prototype-ui` merge" (stage 0 row) and the amendment on the parent's first Packages version (0.1.1).
- Spec 1 as built: `sequeless-build/docs/spec-01.md` at `27e7dce`, parent `org.sequeless:sequeless-build-parent:0.1.1`, workflows `workflows-v0.1.0`.

## 0. Corrections to the issue, and how they were found

A trial adoption was run on 2026-10-29 against `main` at `f1451a7` (JDK 25.0.3, Maven 3.9.10, Docker
available). It found three facts that change the work:

1. **The prototype has no `spring-boot-starter-parent` to replace.** The root POM
   `org.sequeless:sequeless-parent` already has no `<parent>`, imports `spring-boot-dependencies`
   in its own `<dependencyManagement>` and pins its own plugins. That was done on purpose so that
   `sequeless-spi` would not inherit Spring Boot's build conventions (see the root POM's
   `<description>`). "Imports Spring Boot itself" is therefore already true. The work is: add the
   parent, delete what it now supplies, and meet the conventions it brings (`dependency:analyze`,
   `dependencyConvergence`, Spotless, JaCoCo). Spring Boot's resource filtering was never in
   play, because no POM here ever inherited it.
2. **Parent 0.1.1 cannot build a Java 25 app.** With `maven.compiler.release=25`:
   - `maven-dependency-plugin` 3.8.1 fails `analyze-only` on `sequeless-spi` with
     `Unsupported class file major version 69`;
   - JaCoCo 0.8.12 fails to instrument every test class (`IllegalClassFormatException … Unsupported class file major version 69`), and then `jacoco:report` fails the build.

   Spec 1 did not catch this, because `sequeless-build` and its smoke test only ever compile at
   `--release 21`. Overriding the version properties (`-Dmaven-dependency-plugin.version=3.10.0`,
   `-Djacoco.version=0.8.14`) fixes both. A parent release with those versions is therefore a
   prerequisite (§3.1).
3. **The conventions the parent turns on fail on today's prototype.** §3.2 gives the exact findings
   and fixes.

With the overrides of §3.1 and the fixes of §3.2 applied, the trial passed a full `mvn -B -ntp verify`:
all 11 reactor projects built, 872 tests passed (unit tests and Testcontainers ITs, 0 failures), and
JaCoCo wrote its reports. After `npm run build:ui`, `FatJarServesUiAndApiIT` also passed against an
exec jar that contained the SPA (`BOOT-INF/classes/static/index.html` and 34 other assets).

## 1. Identity, stage and dependencies

| | |
|---|---|
| Repo | `sequeless/sequeless-prototype` (existing; keeps its name, #19) |
| Stage | 0, after spec 1. Spec 2 (`sequeless-template`) is not a dependency: the prototype does not adopt the template. |
| Waits for | spec 1 (#29, closed) **and** `sequeless-build-parent` **0.1.2** (§3.1) |
| Blocks | spec 4 (#32, Principal drops displayName), and through it every later prototype spec |
| Consumes | `org.sequeless:sequeless-build-parent:0.1.2` (released, GitHub Packages); reusable workflows at `workflows-v0.1.0` |
| Publishes | nothing (§6) |

### Root artifact ID: rename `sequeless-parent` to `sequeless-prototype`

Spec 1 left this decision to spec 3. **Decided: rename.** `sequeless-parent` next to
`sequeless-build-parent` reads as the same thing at two versions. The root was never published, so
renaming it costs only the ten `<parent>` blocks and three doc references. The new name follows the
convention spec 1 set, where a reactor root is named after its repo (`org.sequeless:sequeless-build`).
The root stays the parent of every module, because it still carries the Spring Boot import and the
prototype's own `<dependencyManagement>`.

## 2. Moves

**None.** No code leaves the prototype, so there is no `PROVENANCE.md`, no import commit and no
deletion commit with a reverse pointer.

Spec 1's provenance says "each source repo deletes its copy when it adopts this parent (prototype:
spec 3)". For the prototype, the copy to delete is the build configuration that the parent now
supplies:

| Deleted from the prototype | Now inherited from `sequeless-build-parent` |
|---|---|
| root properties `maven-compiler-plugin.version`, `maven-surefire-plugin.version`, `maven-failsafe-plugin.version`, `maven-enforcer-plugin.version`, `archunit.version`, `project.build.sourceEncoding` | the same properties (same values, or newer) |
| root `pluginManagement` entries for compiler (with its hard-coded `<release>25</release>`), surefire, failsafe and enforcer | the parent's `pluginManagement` (compiler uses `${maven.compiler.release}`) |
| root `dependencyManagement` entry for `archunit-junit5` | the parent's entry (test scope) |
| root enforcer execution `enforce-versions` (`requireMavenVersion [3.9,)`, `requireJavaVersion [25,)`) | `enforce-baseline`, which adds `dependencyConvergence` and `banDuplicatePomDependencyVersions` |
| root failsafe `integration-test`/`verify` executions | the parent's identical executions |
| `banned-dependencies` executions in `sequeless-spi` and `sequeless-core` | `ban-core-dependencies`, switched on with `<sequeless.enforcer.core.skip>false</sequeless.enforcer.core.skip>` in those two modules. Same three bans: `org.apache.jena*` matches what `org.apache.jena:*` matched. |
| the inline `build` job in `.github/workflows/ci.yml` | `maven-ci.yml@workflows-v0.1.0` (§7) |

What **stays** in the prototype root: the `spring-boot-dependencies` import and `spring-boot.version`,
the Jena, JEXL, springdoc and Temporal pins, `japicmp` (its `pluginManagement` entry and
`japicmp.version`; the parent has no japicmp), and `maven.compiler.release=25` (an app, #17 §1).

## 3. Prerequisite refactors

The rule is that behaviour changes land before the move (#23 §2). This spec moves nothing, but each
convention the parent turns on needs a change first. Every item below is build-only. The one runtime
effect is §3.2 b, which is called out there.

### 3.1 In `sequeless-build`: release parent 0.1.2 for Java 25 consumers

One PR in `sequeless-build`, committed as `fix(build): read Java 25 class files in dependency:analyze and JaCoCo`, so that
release-please makes a patch release:

- `maven-dependency-plugin.version`: `3.8.1` → the newest 3.x on the day, at least **3.9.0**
  (3.9.0 and 3.10.0 were both verified against class file major version 69);
- `jacoco.version`: `0.8.12` → the newest 0.8.x on the day, at least **0.8.14** (0.8.14 verified);
- the same two versions in `bom-contracts/pom.xml`, which repeats the parent's conventions by hand
  (spec 1 §5).

Add a sentence to `sequeless-build`'s README: the parent's plugins must read the class files of the
highest `maven.compiler.release` that a consumer uses (25 for apps), not just the 21 that libraries
use.

Check before merging: run the prototype trial command from §9 step 2 with
`-Dmaven-dependency-plugin.version=… -Djacoco.version=…` set to the new values. After the release,
`org.sequeless:sequeless-build-parent:0.1.2` must be on the repo's Packages page.

**Fallback** if 0.1.2 cannot be released in time: the prototype root sets the two version properties
itself, with a `<!-- remove when sequeless-build-parent >= 0.1.2 -->` comment. This works because the
parent pins every plugin through a property. It is a fallback only: every later Java 25 consumer
would need the same workaround.

### 3.2 In the prototype

**a. `dependency:analyze` hygiene.** The parent runs `analyze-only` with `failOnWarning=true` and
`ignoreNonCompile=true` (#17 §5: nothing crosses through a transitive dependency). The trial findings
on 2026-10-29 (Spring Boot 4.1.1 BOM versions):

| Module | Used but undeclared | Declared but unused | Non-test scope, used only by tests |
|---|---|---|---|
| `sequeless-spi` | `junit-jupiter-api`, `junit-jupiter-params` (test) | | |
| `sequeless-spi-testkit` | `junit-jupiter-api` (compile) | `junit-jupiter` (compile) | |
| `sequeless-core` | `junit-jupiter-api` (test) | | |
| `sequeless-adapter-authz-permitall` | `spring-context`; `assertj-core`, `junit-jupiter-api` (test) | | |
| `sequeless-adapter-persistence-postgres` | `spring-context`, `spring-boot`, `spring-jdbc`, `spring-tx`, `HikariCP`; `assertj-core`, `junit-jupiter-api`, `spring-boot-test` (test) | `flyway-database-postgresql`, `lombok`, `spring-boot-starter-jdbc` | `postgresql` |
| `sequeless-adapter-expression-jexl` | `spring-context`, `spring-boot`; `assertj-core`, `junit-jupiter-api`, `spring-boot-test` (test) | | `spring-boot` |
| `sequeless-adapter-automation-inprocess` | `spring-context`, `spring-beans`, `spring-boot`; `assertj-core`, `junit-jupiter-api`, `spring-boot-test` (test) | | |
| `sequeless-adapter-ontology-jena`, `sequeless-adapter-automation-temporal` | not analysed in the trial: they stopped at `dependencyConvergence` first (b). Re-run after b. | | |
| `sequeless-app` | 22 artifacts | 9, including every adapter module | 4 |

Fix them by these rules, then re-run until analyze is clean:

1. **Used but undeclared:** declare the artifact directly, at the reported scope, with no version
   (the Spring Boot BOM manages it).
2. **Unused Spring Boot starters** (`spring-boot-starter-*`): keep them, because they bring in the
   auto-configuration. In the root, add
   `<ignoredUnusedDeclaredDependencies><ignoredUnusedDeclaredDependency>org.springframework.boot:spring-boot-starter*</ignoredUnusedDeclaredDependency></ignoredUnusedDeclaredDependencies>`
   to the `analyze-only` execution (same execution id, so it merges with the parent's
   configuration).
3. **Drivers and Flyway database modules** (`postgresql`, `flyway-database-postgresql`): `runtime`
   scope. They are loaded by name, never referenced.
4. **Lombok: delete it.** No Java file in the repo imports `lombok`. Delete it from
   `sequeless-adapter-ontology-jena`, `sequeless-adapter-persistence-postgres` and `sequeless-app`,
   and delete its root `<dependencyManagement>` entry and `lombok.version`. The `ArchitectureTest`
   rule that bans `lombok..` stays.
5. **`spring-boot` only used by tests** (`expression-jexl`): move it to `test` scope if `main`
   compiles without it. If it does not, the analyzer is wrong, and the artifact goes under
   `ignoredNonTestScopedDependencies` for that module, with a comment.
6. **`sequeless-app`: skip analyze** with `<sequeless.dependency-analyze.skip>true</sequeless.dependency-analyze.skip>`
   and a comment. Spec 1 provides this escape hatch for exactly this case. The host depends on its
   adapters only so that their auto-configuration runs (`AutoConfiguration.imports`,
   `ServiceLoader`), and no app code names them; hexagonal wiring is invisible to bytecode analysis.
   Rewriting the host's POM to satisfy analyze would mean declaring Tomcat, Jackson and twenty other
   artifacts that the starters already own. The host's boundaries stay guarded by `ArchitectureTest`.

**b. `dependencyConvergence`.** It fails in two modules:

| Module | Artifact | Versions asked for |
|---|---|---|
| `sequeless-adapter-ontology-jena` | `commons-io:commons-io` | 2.20.0 (via `commons-csv`, `commons-compress`), 2.22.0 (`jena-base` directly) |
| `sequeless-adapter-automation-temporal` | `com.google.guava:guava` | 33.4.8-android (`temporal-sdk`), 33.6.0-android and 33.6.0-jre (gRPC) |
| `sequeless-adapter-automation-temporal` | `com.google.errorprone:error_prone_annotations` | 2.18.0, 2.41.0, 2.50.0 |

Fix: in the root `<dependencyManagement>`, pin each one to the **highest version asked for**, with a
comment naming the paths: `commons-io` **2.22.0**, `guava` **33.6.0-jre** (the JVM flavour; gRPC
asks for it), and `error_prone_annotations` **2.50.0**. Spring Boot's BOM does not manage any of
these, which is why they diverge. The trial confirmed that these three pins make `mvn validate`
pass for the whole reactor.

**Runtime effect, the only one in this spec:** the host's classpath changes. Today
`sequeless-app` resolves `commons-io` 2.20.0, `guava` 33.4.8-android and `error_prone_annotations`
2.41.0, because Maven picks the nearest version. "Keep today's versions" is not an option, because
the modules disagree today (jena alone resolves `commons-io` 2.22.0). The unit tests, the ITs and
`FatJarServesUiAndApiIT` are the check. In the trial they passed with these exact versions.

**c. Spotless.** `spotless:check` runs at `verify` (palantir-java-format, import order and sortPom).
Running `spotless:apply` on `f1451a7` changes 204 of the 406 Java files and all 11 POMs (5,272 lines
added, 5,680 removed). These are layout changes only: line wrapping, continuation indents, and the
order of POM elements. Commit them **alone** (§9). Then add that commit's SHA to a new
`.git-blame-ignore-revs`, so that `git blame` skips it. GitHub honours this file by default.

## 4. Package renames

None. No Java package changes. The only rename is the root artifact (§1).

## 5. Modules and their dependencies

The module list, the module artifact IDs, the versions (`0.1.0-SNAPSHOT`) and the dependency graph
between modules are all unchanged.

### Root POM, after

```xml
<parent>
  <groupId>org.sequeless</groupId>
  <artifactId>sequeless-build-parent</artifactId>
  <version>0.1.2</version>
  <relativePath/>                  <!-- never look for ../pom.xml -->
</parent>

<groupId>org.sequeless</groupId>
<artifactId>sequeless-prototype</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
  <maven.compiler.release>25</maven.compiler.release>     <!-- an app (#17 §1) -->
  <sequeless.github.repository>sequeless-prototype</sequeless.github.repository>
  <maven.deploy.skip>true</maven.deploy.skip>             <!-- §6 -->
  <sonar.projectKey>sequeless_sequeless-prototype</sonar.projectKey>  <!-- §7 -->
  <spring-boot.version>…</spring-boot.version>  … the pins listed in §2 as staying …
</properties>

<repositories>                     <!-- repeated: the parent is resolved before its own <repositories> are read -->
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/sequeless/*</url>
  </repository>
</repositories>
```

- **`sequeless.github.repository` must be set.** The parent's value is hard-coded to
  `sequeless-build` (spec 1, PR #7), not `${project.artifactId}` as spec 1 §5 says. Left unset, it
  would point the prototype's `distributionManagement` at the `sequeless-build` package.
- Replace the `<description>`. "Deliberately has no Maven parent" is no longer true. The reason it
  gave still holds, so say so: the parent has no Spring in it, so `sequeless-spi` and `sequeless-core`
  still inherit no Spring Boot conventions.
- The ten module POMs change `<parent><artifactId>` to `sequeless-prototype`.

### What the parent newly turns on, and what was checked

| Convention | Effect on the prototype | Checked in the trial |
|---|---|---|
| surefire `includes **/*Test.java` (replaces surefire's four default patterns) | none today: no test class is named `*Tests`, `*TestCase` or `Test*` | `find` over `src/test/java` |
| failsafe `includes **/*IT.java` | none today: no `IT*` or `*ITCase` classes | same |
| compiler `<parameters>true</parameters>` | new: parameter names are kept in class files. Spring can then bind by name. Nothing relied on their absence. | full test run |
| `dependencyConvergence`, `banDuplicatePomDependencyVersions` | §3.2 b | `mvn validate` |
| `dependency:analyze-only` | §3.2 a | per-module report |
| Spotless check | §3.2 c | `spotless:apply` diff |
| JaCoCo (prepare unit/IT, report; `jacoco.check.skip=true`) | reports under `target/site/jacoco`; no coverage gate | 0.8.14 |
| `<distributionManagement>` to GitHub Packages | `mvn deploy` would now publish SNAPSHOTs. Blocked by `maven.deploy.skip` (§6). | — |
| `<repositories>` `github` | every Maven run needs a `read:packages` token (§7, local development) | parent resolved with `~/.m2/settings.xml` server `github` |

The trial also hit `japicmp`: its `oldVersion RELEASE` lookup now asks GitHub Packages for
`sequeless-spi`, logs `Could not resolve … RELEASE` and skips (`ignoreMissingOldVersion`). That is
the same no-op as before, now with a network lookup.

### UI and the exec jar (issue's "Update after the sequeless-ui merge")

- The parent configures no `<resources>` and no filtering, so `sequeless-app`'s resources (including
  the Angular build under `src/main/resources/static/`) are copied byte for byte, as today.
- The root npm scripts (`build:ui`, `build`, `build:fast`, `verify`, `start:api`, `ci:ui`,
  `ci:combined`) keep their commands. Every script that runs `mvn` now needs the token setup in §7.
- **Check:** `npm run build:ui && mvn -B -ntp verify`, with `FatJarServesUiAndApiIT` passing against
  a jar that contains `BOOT-INF/classes/static/index.html`. It also has to pass without the UI
  build, where `/` is expected to return 404.

## 6. Release config

- **The prototype is not released.** It is a host (#19), and nothing consumes its artifacts. There is
  no release-please config, no tag and no first version. All modules stay at `0.1.0-SNAPSHOT`.
  Revisit this when the host gets a deployment pipeline. Adding release-please later is easy to undo.
- **`maven.deploy.skip=true`** in the root. Before this spec, a `mvn deploy` failed for lack of
  `distributionManagement`. The parent supplies one, so without the skip an accidental deploy would
  publish a `sequeless-prototype` package.
- **BOM change: none.** The prototype keeps no Sequeless dependency besides the parent. From stage 1
  on, the cut-over specs import `sequeless-bom` in the root `<dependencyManagement>`.
- **Parent upgrades** come from Renovate (§7). The rule that `main` never references a SNAPSHOT
  (#23 §2) applies to the parent too: `<version>` is always a released version.

## 7. CI and workflows

### `.github/workflows/ci.yml`

```yaml
permissions:
  contents: read
  packages: read

jobs:
  build:
    uses: sequeless/sequeless-build/.github/workflows/maven-ci.yml@workflows-v0.1.0
    secrets: inherit
  ui:        # unchanged: npm only, no Maven
  combined:  # unchanged steps, plus the token on setup-java:
    # setup-java: server-id: github, server-username: MAVEN_USERNAME, server-password: MAVEN_PASSWORD
    # env on the run step: MAVEN_USERNAME: ${{ github.actor }}
    #                      MAVEN_PASSWORD: ${{ secrets.PACKAGES_READ_TOKEN || secrets.GITHUB_TOKEN }}
  sonar:
    uses: sequeless/sequeless-build/.github/workflows/maven-sonar.yml@workflows-v0.1.0
    secrets: inherit
```

- The check name changes from `build` to **`build / maven`**. Nothing requires the old name: `main`
  has no branch protection today.
- `maven-ci.yml` runs `mvn -B -ntp verify` without the UI build, which is the same as today's `build`
  job. The `combined` job keeps its inline steps, because it needs Node and Java in the same job.
- `PACKAGES_READ_TOKEN` and `SONAR_TOKEN` are org secrets visible to all repos (checked 2026-10-29),
  so `secrets: inherit` passes them through.
- The `on:` triggers stay as they are (`push`, `pull_request`).

### Sonar

This is the first real use of `maven-sonar.yml`, as spec 1 §7 said it would be. Because `SONAR_TOKEN`
exists, the job will run.

- The SonarCloud project already exists: key `sequeless_sequeless-prototype`, last analysed
  2026-10-27, with no CI job at that time. That means **Automatic Analysis** is probably on, and
  SonarCloud rejects a CI analysis while it is on. **Human task:** turn Automatic Analysis off for the
  project (Administration → Analysis Method) before merging. If the owner would rather keep it, drop
  the `sonar` job and record why in the README.
- Without `sonar.projectKey`, the scanner defaults to `org.sequeless:sequeless-prototype`, which is
  why the root sets it (§5).

### `.github/workflows/codeql.yml` (new)

Calls `maven-codeql.yml@workflows-v0.1.0` with `languages: '["actions","java-kotlin","javascript-typescript"]'`,
on push, PRs and a weekly cron, with `security-events: write`. The reusable workflow builds Java
manually and uses build-mode `none` for the other languages, which suits `sequeless-ui/`.

### Renovate

Add `renovate.json`: `{ "extends": ["github>sequeless/sequeless-build"] }`.

- Renovate can read Packages (#56 closed), so it will raise the parent, the reusable workflow refs
  and the Maven pins.
- The preset's `config:recommended` also covers `sequeless-ui/package.json` and
  `sequeless-ui/package-lock.json`. That is wanted (the preset is org-wide), but expect a first wave
  of npm PRs. Renovate's dependency dashboard keeps that manageable.

### Local development

Every Maven run now resolves the parent from GitHub Packages, which needs a token even though the
package is public. The README's run section gets a one-time step, placed before `start:api` and next
to the existing note on the one-time install:

```xml
<!-- ~/.m2/settings.xml -->
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USER</username>
    <password>A_PAT_WITH_read:packages</password>
  </server>
</servers>
```

Without it, the first `mvn` fails with a 401 on `sequeless-build-parent`. The README says so, so that
the error can be recognised.

### Repo settings (the agent does these with `gh`)

Branch protection on `main`: require PRs and the `build / maven` and `ui` checks. Do not require
`combined` (it is opt-in) or `sonar` (it is advisory). Set this only after the PR has merged and the
check names exist.

## 8. ADRs

None. #17 recorded none ("easy to reverse") and #23 lists none for this spec. The decisions taken in
this spec are each worth one line in the README's build section rather than an ADR, because each is
easy to undo:

- the root is renamed to `sequeless-prototype` (§1);
- `sequeless-app` skips `dependency:analyze`, and why (§3.2 a, rule 6);
- the convergence pins and their runtime effect (§3.2 b);
- the prototype is not released, and `maven.deploy.skip` (§6).

Update `README.md` (module table) and `docs/architecture/01-overview.md` (module table, line 73)
from `sequeless-parent` to `sequeless-prototype`, and state there that the root's parent is
`sequeless-build-parent`. Leave `docs/phase-0-verification.md` and
`docs/architecture/prompts/phase-0.md` alone: they are historical records.

## 9. Implementation order

1. **`sequeless-build`:** the §3.1 PR, then the release. Confirm that 0.1.2 is on Packages.
2. **Trial command** (the reference check for every later step). Run it from the prototype root:
   `npm run build:ui && mvn -B -ntp verify`.
3. **Prototype branch `spec-03-adopt-build-parent`.** Each commit must build on its own:
   1. `build(deps): pin commons-io, guava and error_prone_annotations for convergence` (§3.2 b).
      This commit is green under the old root too.
   2. `build(deps): declare used dependencies and drop unused Lombok` (§3.2 a, rules 1, 3, 4 and 5).
      Also green under the old root.
   3. `style: apply the sequeless-build Spotless format`. Produce it by running `mvn spotless:apply`
      with commit 4's POM in place, then commit **only** the formatting changes. Nothing else may be
      in this commit.
   4. `build: adopt sequeless-build-parent 0.1.2`: everything in §2 and §5, the root rename,
      rules 2 and 6 of §3.2 a, `maven.deploy.skip`, `sonar.projectKey`. Format the POMs with sortPom
      so that `spotless:check` passes.
   5. `chore: ignore the Spotless format commit in git blame` (`.git-blame-ignore-revs` with the SHA
      of commit 3).
   6. `ci: use the sequeless-build reusable workflows; add CodeQL, Sonar and Renovate` (§7).
   7. `docs: record the build parent, the root rename and the token setup` (§7 local development, §8).
4. Open the PR. Before merging: the human task on Sonar's Automatic Analysis (§7) is done, and the
   `build / maven`, `ui`, `sonar` and `codeql` checks are green. Also run `combined` once by setting
   the repo variable `CI_COMBINED_JOB=true` for the PR run, or run `npm run ci:combined` locally and
   paste the result into the PR.
5. After merging: branch protection (§7). Confirm that Renovate opened its dependency dashboard.

**New human task this spec raises** (open it as a `wayfinder:task` issue): turn off SonarCloud
Automatic Analysis for `sequeless_sequeless-prototype` (§7).

## 10. Definition of done

The issue's checklist, read for a stage-0 prototype spec that moves no code and releases nothing:

| Item | For this spec |
|---|---|
| The new repo's CI is green | There is no new repo. Read it as: `sequeless-build` CI is green at the 0.1.2 release commit (§3.1). |
| The prototype's CI is green against *released* artifacts | `build / maven`, `ui`, `sonar` and `codeql` are green on `main`, with `<parent>` at the **released** `sequeless-build-parent:0.1.2` (no SNAPSHOT anywhere in the POMs). `combined` was green once on the PR. `FatJarServesUiAndApiIT` passes with and without the UI build (§5). |
| The BOM's CI, which runs every testkit contract, is green | Unchanged by this spec. `bom-contracts` stays green in `sequeless-build`, including after the §3.1 version bumps. |
| The release and the BOM re-release are done | `sequeless-build-parent` 0.1.2 is released (§3.1). The prototype releases nothing (§6). BOM: no change; its first release is in stage 1. |
| Cut-over: the prototype deleted the moved code, with a reverse pointer | Not a cut-over spec. The duplicated build configuration in §2 is deleted in commit 4. |
