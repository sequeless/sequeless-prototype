# Spec 8 — Stage 2: `sequeless-expression`

Issue: [#36](https://github.com/sequeless/sequeless-prototype/issues/36). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Tracker: `docs/extraction/remaining-work.md`.

Status: planning spec. Nothing in it is done yet. The new repo holds only the template, no expression code has moved, and there is no release or BOM change. Sections 2 to 7 are plan items unless they say "verified".

## 1. Identity, stage and dependencies

- Stage 2. Spec 8 of 19. It creates the `sequeless-expression` repo and moves the expression port, its JEXL adapter and its contract out of the prototype.
- Waits for spec 5 ([#33](https://github.com/sequeless/sequeless-prototype/issues/33), `sequeless-kernel`, done and closed) and spec 7 ([#35](https://github.com/sequeless/sequeless-prototype/issues/35), prototype stage 1 cut-over, done and closed). Spec 7's PR, [#80](https://github.com/sequeless/sequeless-prototype/pull/80), reached `main` as `d53d3f1` (verified: an ancestor of `main`; the commit subject names #35).
- Unblocks spec 9 ([#37](https://github.com/sequeless/sequeless-prototype/issues/37), prototype stage 2 cut-over), which waits for this spec (`remaining-work.md`, table).
- Issue #36 is open. Decision issue [#22](https://github.com/sequeless/sequeless-prototype/issues/22), "Shape of the sequeless-expression repo", is closed. It says the repo graph is kernel ← expression ← ontology. That graph is not verified against the ontology repo's POM in this spec.
- Scope, quoted from #36:

  > New repo with modules `expression-api` (depends on `kernel-values` only), `-jexl-adapter`, `-testkit`, `-spring-boot-autoconfig`. Locked-down JEXL sandbox, per-site Signatures, per-format Template encoding, Dialect `sq-expr/1`. First release 0.1.0.

- Repo state (verified with `gh api`, read-only): `sequeless/sequeless-expression` exists, is public, was created 2026-11-06 at 13:39Z, and has one commit, `916aa015f` ("Initial commit"). Its tree is the template's tree: `template-core/`, `.github/`, `docs/adr/` with the template's two files, and a `PROVENANCE.md` that lists template sources. It has no expression code.
- It was created the same way as `sequeless-kernel` (`remaining-work.md`, Done, spec 5 entry).

## 2. Moves

Status: planned. No move commit exists in any repo. The expression code is still in the prototype at `d53d3f1` (verified). This spec does not delete prototype code. The deletion is spec 9's job.

PROVENANCE content for the new repo's first commit (planned, in the same shape as the kernel's `PROVENANCE.md`):

- **Created from:** `sequeless/sequeless-template`. The starting commit is `916aa015f`. Whether it is the init-script output of template commit `26fd82d`, as the kernel's `PROVENANCE.md` records for itself, is not verified in this spec.
- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** recorded in the move commit, as spec 5 section 2 requires. At the time of writing, the branch HEAD and `main` are both `d53d3f1`. The expression paths were last changed in `d53d3f1` (verified with `git log`). That is a candidate for the move's SHA, not the SHA itself.
- **Source paths:** in the table below. All of them exist at `d53d3f1` (verified with `ls`).
- **The move:** copy the paths into the new repo, with the package renames in section 4. Not done.
- **Date:** not yet. It is recorded when the move commit is made.

| Target (module name per #36) | Source paths at `d53d3f1` | Java files |
|---|---|---|
| API (`expression-api`) | `sequeless-spi/src/main/java/org/sequeless/spi/expression/`: `ExpressionPort.java`, `ExpressionContext.java`, `ExpressionException.java`, `package-info.java` | 4 main. No test for this package in `sequeless-spi`. |
| JEXL adapter (`expression-jexl-adapter`) | `sequeless-adapter-expression-jexl/`: `pom.xml`; `src/main/java/org/sequeless/adapter/expression/jexl/`: `JexlExpressionPort.java`, `JexlExpressionAutoConfiguration.java`, `package-info.java`; `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; `src/test/java/org/sequeless/adapter/expression/jexl/`: `JexlExpressionPortContractTest.java`, `JexlExpressionAutoConfigurationTest.java` | 3 main, 2 test |
| Testkit (`expression-testkit`) | `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/expression/`: `ExpressionContract.java`, `package-info.java` | 2. Only this package moves. The rest of `sequeless-spi-testkit` stays. |

- The autoconfig class and its imports file are in the adapter module today. Whether they move to their own module is the open question in section 5.
- Nine files outside the moved set import `org.sequeless.spi.expression` (grep at `d53d3f1`). Spec 9 covers them.
- Six `sequeless-app` test classes set `sequeless.expression.adapter=jexl` (grep). That property name is a configuration contract the app depends on. Spec 9 covers any change to it.
- Stale text to fix in the move commit (verified by reading the files):
  - `org/sequeless/spi/expression/package-info.java` says the package imports nothing outside the JDK except sibling `org.sequeless.spi.*` types, and that `Value` comes from `org.sequeless.spi.object`. At `d53d3f1` the import is `org.sequeless.kernel.values.Value`.
  - `ExpressionPort.java` names `sequeless-spi-testkit`'s `ExpressionContract` in `{@code}`. After the move the contract lives in `expression-testkit`.
  - `ExpressionContext.java` refers to "plan.md §3". There is no `plan.md` at the repo root. Whether it exists elsewhere is not verified in this spec.

## 3. Prerequisite refactors

### 3.1 Javadoc link to a type that stays (in the prototype, before the move)

- `sequeless-spi/src/main/java/org/sequeless/spi/expression/ExpressionException.java` has `{@link org.sequeless.spi.ontology.OntologyException}` in its class javadoc (lines 9 and 10). That type is not one of the moving types. The ontology types stay in the prototype until specs 11 and 12 (`remaining-work.md`, table). After the move the link does not resolve in `sequeless-expression`, and javadoc fails on an unresolved link (spec 5 section 3.1).
- Fix, as spec 5 section 3.1 did for the kernel: demote it to `{@code org.sequeless.spi.ontology.OntologyException}` in the prototype before the move. This is javadoc-only and changes no behaviour. Status: not done.
- Every other `{@link}` in the moving files points to a moving type, to `org.sequeless.kernel.values` (the kernel dependency), to a JDK type, or to an external JEXL type (grep at `d53d3f1`). `{@code org.sequeless.spi.object.StaleObjectException}` is already code-formatted and needs no change.

### 3.2 Spec 7 is already met

- At `d53d3f1` the expression API imports `org.sequeless.kernel.values.Value` (grep). The import comes from spec 7's cut-over, so the API can depend on `kernel-values` from the kernel repo. Nothing from the prototype's `org.sequeless.spi.object` is needed.

## 4. Package renames

- Current packages (verified at `d53d3f1`):
  - API: `org.sequeless.spi.expression`
  - JEXL adapter, port and autoconfig: `org.sequeless.adapter.expression.jexl`
  - Testkit contract: `org.sequeless.testkit.expression`
- The API's only non-JDK import is `org.sequeless.kernel.values.Value`. That package is renamed by spec 5, not here.
- New names are not decided in this spec. Spec 5 section 0 decided the kernel packages by the repo-segment rule, citing sequeless-filter's `org.sequeless.filter.api`. Kernel ADR 0001, "Package names follow the repo segment" (status `proposed`), records the same rule. Under that rule the API would be `org.sequeless.expression.api`. The owner has not confirmed this for expression (not verified). The adapter and testkit names are not decided here.
- Renames happen in place in the new repo's first commit, with no bridge package (spec 5 section 4). The prototype does not rename anything. It deletes the old classes in spec 9.

## 5. Modules and their dependencies

- **Module count: decided by the owner (2026-11-06).** Four modules, per #36: `expression-api`, `expression-jexl-adapter`, `expression-testkit`, `expression-spring-boot-autoconfig`. The two-module list in #22 is superseded for the module count. The rest of the sub-points below are the evidence the decision was made on.
  - #36 names four modules: `expression-api` (depends on `kernel-values` only), `-jexl-adapter`, `-testkit` and `-spring-boot-autoconfig`. The last three are written with a leading dash only, so the full names above are inferred from the `expression-api` prefix. They are not confirmed.
  - #22 (closed) names two: `expression-api` and `expression-jexl-adapter`. It says `ExpressionContract` is "in the testkit" without naming a module for it, and it names no autoconfig module.
  - Sections 6 and 7 use #36's four-module list where a module name is needed, and mark it.
- **API (`expression-api`):** the four files import only the JDK and `org.sequeless.kernel.values.Value` (verified by grep of the import lines). So it depends on `kernel-values` only, as #36 says. `sequeless-spi`'s POM also declares `kernel-identity`, but no expression file imports an identity type.
- **JEXL adapter (`expression-jexl-adapter`):** its POM declares `kernel-values`; `sequeless-spi` (becomes `expression-api`); `commons-jexl3` (version managed in the root POM, per the adapter POM's comment); `spring-boot-autoconfigure` (optional); `spring-context`; and, in test scope, `sequeless-spi-testkit` (becomes `expression-testkit`), Spring Boot test artifacts, JUnit Jupiter API and AssertJ.
- **Testkit (`expression-testkit`):** `ExpressionContract` imports JUnit Jupiter, AssertJ, `org.sequeless.kernel.values` and `org.sequeless.spi.expression`. After the move it needs `expression-api`, `kernel-values`, JUnit and AssertJ. Today it sits in `sequeless-spi-testkit`, which holds the other contracts.
- **Spring autoconfig:** `JexlExpressionAutoConfiguration` is `@AutoConfiguration` with `@ConditionalOnProperty(name = "sequeless.expression.adapter", havingValue = "jexl")`. It is registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It moves to `expression-spring-boot-autoconfig`, per the module decision above.
- **Consuming the kernel:** how the new repo gets `kernel-values` (through the BOM or as a direct version) is not verified in this spec. Whatever it is, no dependency may be a SNAPSHOT (the rule in spec 7 section 7).
- **Parent and Java level:** not verified in this spec. The new repo's root POM is still the template's aggregator. It names a `template-core` module and a `sequeless-template` artifact. Kernel libraries use `--release 21` (spec 5 section 5). The filter's Java 25 floor is recorded in `remaining-work.md`. Which level this repo uses is not verified.
- **Prototype reactor:** `pom.xml` line 30 lists `sequeless-adapter-expression-jexl` (verified). That line is removed in spec 9, not here.
- **Versioning after 0.1.0:** not decided here. #36 says only "First release 0.1.0".

## 6. Release config (first version, tags, BOM change)

- **First version:** 0.1.0 for each shipped module (#36). Which modules those are depends on the section 5 question.
- **Mechanism (plan, as the kernel):** release-please in manifest mode with the `maven-workspace` plugin, `release-as: 0.1.0` for the first release, then a follow-up PR that removes the pin (spec 5 section 6).
  - Kernel (verified): `release-please-config.json` names `kernel-identity` and `kernel-values`, each with `initial-version` 0.1.0, and has no `release-as`.
  - Expression (verified): the config still names `template-core`, and `.release-please-manifest.json` is `{}`. Both must be rewritten. This is the first change the new repo needs.
- **Tags (plan):** `<module>-v0.1.0` for each module, the pattern the kernel uses (`kernel-identity-v0.1.0`, `kernel-values-v0.1.0`). Kernel's config has `include-component-in-tag` set to true (verified). The exact names depend on section 5. Whether any tag exists in the new repo is not verified in this spec. The repo has one commit, so no release PR has merged.
- **Deploy namespace (plan):** set `sequeless.github.repository` to `sequeless-expression` in each module POM. Both kernel POMs do this (verified, line 24 of `kernel-identity/pom.xml` and of `kernel-values/pom.xml`). Without it the parent deploys to `sequeless-build` and gets a 403 (spec 5 section 6, sequeless-filter #19). Not done in the new repo, whose POMs are still the template's.
- **BOM change (plan):** `sequeless-bom` in `sequeless/sequeless-build` is at 0.1.1 (verified). It lists `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `filter-sql-adapter` 0.1.4, `filter-sql-testkit` 0.1.0 and `filter-sql-postgres-adapter` 0.1.0. It lists no expression artifact. The re-release adds the expression artifacts at 0.1.0 after their release, with owner approval. The next BOM version is not decided here.
- **BOM CI depends on the first contract (plan).** `bom-contracts` in `sequeless-build` has one test, `BomListsOnlyReleasesTest` (verified). So the BOM CI runs no testkit contract today. `bom-contracts/pom.xml` says the `sequeless-bom` import returns "with the first testkit contract this module runs against the BOM" (lines 42 to 43, verified). The expression contract would be that first contract, so the import comes back in the same change. The comment also says that importing the BOM at `0.1.0-SNAPSHOT` broke the BOM release PR.

## 7. CI and workflows

- **New repo (verified with `gh api`):** `ci.yml`, `codeql.yml` and `release.yml` call the reusable workflows in `sequeless/sequeless-build` at `workflows-v0.1.2`, the latest tag (`remaining-work.md`). `pr-title.yml` is present. `template-smoke.yml` is present in `sequeless-expression` but not in `sequeless-kernel` (tree listings). Whether it stays is not decided in this spec.
- **Runs on `main` (verified with `gh run list`):** CI, CodeQL, Template smoke and Release all completed with success at 2026-11-06 13:39Z. They ran on the template's content, so they show that the template builds. They do not show that the expression modules build. What the Release run did is not verified.
- **Prototype pins (verified, not changed by this spec):** `.github/workflows/ci.yml` line 13 pins `maven-ci.yml` at `workflows-v0.1.0`. `.github/workflows/codeql.yml` line 15 pins `maven-codeql.yml` at `workflows-v0.1.1`. `remaining-work.md` notes that no Renovate PR moves either pin.
- **Prototype CI (verified):** the CI and CodeQL runs for `d53d3f1` completed with success on `main` at 2026-11-06 12:39Z.
- **BOM CI (verified):** `sequeless/sequeless-build` CI on `main` completed with success at `b0e1a845b` (2026-11-04). See section 6 for what it runs.
- **Branch ruleset for the new repo:** not verified. Spec 5 section 7 says the owner sets it.
- **Known issue:** a bot-created release PR gets no CI runs until it is closed and reopened (spec 5 section 7, sequeless-filter #18). Carried from spec 5. Not re-verified here.

### Definition of done

Quoted from issue #36, with the status at this HEAD:

- "The new repo's CI is green." Status: open. CI on `sequeless-expression` `main` completed with success, but it ran the template's content. The expression modules do not exist yet, so the item is not met in the sense the issue means.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Status: partly met for this spec. Prototype CI on `main` at `d53d3f1` is green (verified). The prototype does not consume any expression artifact yet, so the expression part of this item starts at spec 9 (#37). The statement that the kernel is consumed as a released artifact comes from spec 7 and is not re-verified here.
- "The BOM's CI, which runs every testkit contract, is green." Status: open. `sequeless-build` CI on `main` is green (verified). But the BOM CI runs no testkit contract today (section 6), so "every testkit contract" is empty until the expression contract is added.
- "The release and the BOM re-release are done." Status: open, not started. There is no expression release, since the repo has one commit. The BOM does not list an expression artifact (section 6).
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Status: not applicable to spec 8, which is not a cut-over. Spec 9 (#37) repeats this item.

## 8. ADRs

- None new in this spec. The decisions it relies on are recorded in issue [#12](https://github.com/sequeless/sequeless-prototype/issues/12) (the Command-side split, and `StaleObjectException` staying), [#22](https://github.com/sequeless/sequeless-prototype/issues/22) (the repo shape, closed), [#23](https://github.com/sequeless/sequeless-prototype/issues/23) (order, history and release rules), and [spec 5](spec-05-sequeless-kernel.md) (sections 0 and 4).
- Verified: `sequeless-expression` has `docs/adr/` with only the template's `0000-record-architecture-decisions.md` and `_template.md`. The prototype has no `docs/adr/` directory at `d53d3f1`. The kernel has `docs/adr/0001-package-names-follow-the-repo-segment.md`, status `proposed`, dated 2026-11-04. The repo's agent docs (`docs/agents/domain.md`) put architecture decisions in `docs/adr/`.
- Not decided in this spec: whether the sandbox design, the per-site Signatures, the per-format Template encoding and Dialect `sq-expr/1` need ADRs in `sequeless-expression`.
- Verified: `sq-expr` appears nowhere in the prototype's text files (grep). No Java line in the prototype mentions both `Signature` and `expr` or `template`. `GLOSSARY.md` defines Dialect as "the versioned definition of what Expressions and Templates may say and what they mean. Each Ontology version declares exactly one." The glossary does not name `sq-expr/1`. Per-format Template encoding is not verified in this spec.
