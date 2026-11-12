# Review 03: sequeless-kernel, sequeless-expression, sequeless-filter

## VERDICT: REVISE

Most claims are correct (about 25 of 31 that I checked). But the filter version section and the "Uncertain" list rest on stale checkouts, and some of that text is wrong. The draft also states "Java 21" for filter while the filter POM compiles for release 25. The STE problems are systematic, not local. Fix the facts below, then do one STE pass.

Important method note for the writer: the local checkouts are not current.

| Repo | Local HEAD | origin/main | Behind |
|---|---|---|---|
| sequeless-kernel | da8b48f (main) | da8b48f | 0 |
| sequeless-expression | 052a4a6 (main) | 052a4a6 | 0 |
| sequeless-filter | c901a88 (branch `scratch/isnotnull`) | 9ec6195 | 5 |
| sequeless-build | d7e6b6c (branch `bom/filter-0.1.4`) | 6ba3d29 | 16 |
| sequeless-prototype | 5a6c7fa (main) | 4ae250c | 4 (docs and tests only; the BOM and parent pins do not change) |

Read filter and build facts with `git show origin/main:<path>` or `git show <tag>:<path>`, not from the working tree.

## 1. Accuracy

### Claims that I verified as correct

1. Prototype root imports `sequeless-bom` 0.1.3 (P/pom.xml:60-67). Correct.
2. BOM 0.1.3 (`git show sequeless-bom-v0.1.3:sequeless-bom/pom.xml`) lists kernel-identity 0.1.0, kernel-values 0.1.0, filter-core 0.1.3, and all four expression modules at 0.1.1. Correct.
3. BOM 0.1.4 lists filter-core 0.1.4. Correct (it also lists filter-sql-adapter 0.1.5, and filter-sql-testkit and filter-sql-postgres-adapter at 0.1.1).
4. Kernel tags are `kernel-identity-v0.1.0` and `kernel-values-v0.1.0` only. The manifest has 0.1.0 for both. The module POMs have 0.1.1-SNAPSHOT. Correct.
5. Kernel `release.yml` calls `maven-release.yml@workflows-v0.1.2`. Correct (ci.yml and codeql.yml use the same tag; `workflows-v0.1.3` exists but kernel does not use it).
6. Kernel release config has no `versioning` or `skip-snapshot` keys. Correct.
7. `Value` is a sealed interface with eight permitted types and static factories (`text`, `integer`, `decimal`, `bool`, `dateTime`, `date`, `ref`, `list`). Correct. Note: the boolean factory is `Value.bool`, not `boolean`.
8. `Principal.ANONYMOUS` and `TenantId.DEFAULT` exist. Correct.
9. Prototype Java has 61 files that import `ObjectId` and 48 files that import `Value`. Correct (file counts).
10. `ExpressionContext(Map<String,Value> selfProperties, Optional<String> selfState, String principalId)`. Correct. `ExpressionException extends RuntimeException`. Correct.
11. Expression release config: four packages, `always-bump-patch`, `skip-snapshot`. Manifest 0.1.1 for all four. Tags 0.1.0 and 0.1.1 for each. Correct.
12. expression-api CHANGELOG 0.1.1 is dated 2026-11-06. Correct.
13. expression-api POM pins `kernel-values` 0.1.0 directly. Correct.
14. Autoconfig sets `spring-boot.version` 4.1.1. Correct. Build needs JDK 25, consumers Java 21 (README:55,60; CONTRIBUTING:5-8). Correct.
15. `application.yaml` sets `sequeless.expression.adapter: jexl`. Correct.
16. `StateMachineInterpreter` builds `ExpressionContext` with `scope.principal().id()` and calls `evaluate` per guard. Correct.
17. `DefaultActionExecutor` calls `renderTemplate` for webhook `url`, `body`, and log `message`. Correct.
18. core depends on `expression-api` and `filter-core`. app depends on `expression-spring-boot-autoconfig` and `expression-api`, with no versions. Correct.
19. `FilterNodeToCriterion` flattens `AndFilter`, maps each `FieldFilter` to one `Criterion`, and throws `IllegalArgumentException` for `OrFilter` and `AnyFilter`. Correct.
20. No prototype code imports the parser, serializer, validator, completion, or any `filter-sql-*` module. Correct (the only filter imports are `AndFilter`, `AnyFilter`, `FieldFilter`, `FilterNode`, `OrFilter`).
21. `FilterParser.parsePartial` "never throws" and returns `Complete` or `Partial`. Correct (javadoc, origin/main).
22. `SqlFragment(String sql, List<Object> parameters)`. Correct. `PostgresQueryBuilder` renders like as `ILIKE`. Correct.
23. Filter CONTRIBUTING: every release is a patch bump, including breaking changes. Correct. ADR 0002 accepts the `QueryBuilder` break as a patch because the library is pre-1.0. Correct (ADR 0002 is titled "field-expression-port"; say that).
24. Renovate: Sequeless bumps use commit type `fix` and do not auto-merge. Correct (sequeless-build `default.json`). Other patch and minor updates do auto-merge; the draft does not say this.
25. Prototype README is stale: it lists `sequeless-adapter-expression-jexl` (README:185). Correct.
26. remaining-work.md:141 records that the prototype deleted its expression port and JEXL adapter. Correct.

### Wrong or unsupported claims

| # | Draft claim | Correct fact | Evidence |
|---|---|---|---|
| W1 | Uncertain 1: "manifest filter-core 0.1.3 / sql-adapter 0.1.4; tags 0.1.4 / 0.1.5" (the numbers disagree) | There is no disagreement on the real main branch. `origin/main` manifest: filter-core 0.1.4, filter-sql-adapter 0.1.5, filter-sql-postgres-adapter 0.1.1, filter-sql-testkit 0.1.1. These match the tags and BOM 0.1.4. The draft read a stale working tree on the branch `scratch/isnotnull`. | `git -C sequeless-filter show origin/main:.release-please-manifest.json`; `git show filter-core-v0.1.4:.release-please-manifest.json` |
| W2 | Filter: "Java 21" | The README and CONTRIBUTING say JDK 21, but `filter-core/pom.xml` sets `maven.compiler.release` 25 (on origin/main and at tag filter-core-v0.1.3). The parent `sequeless-build-parent` 0.1.3 sets 21; filter-core overrides it. So consumers of filter-core need Java 25. The prototype compiles with release 25, so it works. Report this as a doc-versus-POM conflict. | `git show origin/main:filter-core/pom.xml` line 22; `git show sequeless-build-parent-v0.1.3:sequeless-build-parent/pom.xml` line 37 |
| W3 | "A BoundaryRulesTest enforces JDK-only for kernel-values." | Both modules have a `BoundaryRulesTest`. Each test is an ArchUnit rule that forbids Spring and forbids a dependency on the other kernel module. "JDK-only" is the README's wording, not what the test checks. | `kernel-identity/src/test/.../BoundaryRulesTest.java:12-24`; kernel README "A test checks that neither module depends on Spring" |
| W4 | "Prototype is an app using three sibling repos as libraries [P/pom.xml:60-67]" | The prototype also uses sequeless-ontology artifacts (`ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter`, `ontology-testfixtures`) and takes `sequeless-build-parent` 0.1.2 as its parent. Lines 60-67 are the BOM import only. Say "three of the sibling repos". | `sequeless-core/pom.xml:28-67`, `sequeless-app/pom.xml`, root `pom.xml:7-8` |
| W5 | Kernel "Prototype use: declares both modules ... [P/sequeless-core/pom.xml:26-33]" | Correct for core, but too narrow. Kernel modules appear in almost every prototype module (spi, spi-testkit, app, and all adapters). `filter-core` is also in `sequeless-spi-testkit` (`QueryFixtures`), not only in core. | prototype module POMs |
| W6 | "BOM tags v0.1.0..v0.1.4" | The tags are `sequeless-bom-v0.1.0` ... `sequeless-bom-v0.1.4`. The repo also has `sequeless-build-parent-v*` and `workflows-v*` tags. | `git -C sequeless-build tag` |
| W7 | "0.1.3 BOM lists kernel..., filter-core 0.1.3, four expression modules 0.1.1" | Incomplete in a way that can mislead. BOM 0.1.3 also lists filter-sql-adapter 0.1.4, filter-sql-testkit 0.1.0, filter-sql-postgres-adapter 0.1.0, and six ontology artifacts at 0.1.0. | `git show sequeless-bom-v0.1.3:sequeless-bom/pom.xml` |
| W8 | Uncertain 6: build is on `bom/filter-0.1.4` | True, but say more: `origin/main` of sequeless-build already released BOM 0.1.4 (manifest `sequeless-bom` 0.1.4, commit 6ba3d29). The prototype still imports 0.1.3. | `git -C sequeless-build show origin/main:.release-please-manifest.json` |
| W9 | Sandbox: "blocks reflection, static methods, classes [README:3-18; JexlExpressionPort.java:85-100]" | The README says this. The code at lines 85-100 does something more specific: a default-deny package allow-list (`java.lang`, `java.math`, `java.text`, `java.util`, `java.time`). It explicitly denies `Runtime`, `System`, `ProcessBuilder`, `Class`, `ClassLoader`, `Thread`, and others. It denies all of `java.lang.reflect`, `java.lang.invoke`, `java.net`, and `java.rmi`. Because `java.io` and `java.nio` are not on the list, it denies them too. The engine runs with `strict(true)` and `silent(false)`. The code does not show a "no static methods" rule. Label that part as a README claim. | `JexlExpressionPort.java:40-77, 85-115` |
| W10 | Filter: "README says all modules release together" | True (README:38-40 on origin/main). But the README is wrong, not uncertain: the root aggregator is version 0.1.0, and each module has its own version (filter-core 0.1.4). Say "README is stale" so the reader trusts the config. | `git show origin/main:README.md`, `origin/main:filter-core/pom.xml` |
| W11 | "Main code reads only tree types: FilterNodeToCriterion ..." | Correct, but the draft omits where the main code calls it: `DerivationPlanner.java:178` (rollup criteria) and `TypeResponseMapper.java:318` in sequeless-app. Without this, the reader cannot see why filter matters. | grep `FilterNodeToCriterion` in `src/main` |

Unverified (I did not check these; mark them as such or remove them): the `-am` build detail of the shared release workflow; the expression 0.1.0 deploy failure (the draft already flags this).

## 2. Missing important topics

- **The rollup use case.** Filter trees come from `sq:filter` on a rollup. The released ontology loader (sequeless-ontology) writes the tree, and `DerivationPlanner` turns it into `Criterion` values. Name the repo; "a loader in another repo" is too vague.
- **FilterNodeToCriterion details that matter.** It maps nine op names (`is`, `is not`, `is in`, `is less than` ..., `contains`, `starts with`). `is` and `is not` with no value become `IS_NULL` and `NOT_NULL`. `is in` makes a `ListValue`. The OWL integer-as-decimal rule exists. Other ops are rejected.
- **Expression bindings.** `ReferenceValue` unwraps to a plain string id, not `ObjectId`. A null result throws `ExpressionException`.
- **Kernel release behaviour.** Kernel has no `skip-snapshot`, so release-please opens SNAPSHOT bump PRs (kernel PR #12 "chore: release main" is open). This explains 0.1.1-SNAPSHOT and the missing 0.1.1 tag (Uncertain 4 and 5 answer each other).
- **`DefaultTransitionService`** also takes an `ExpressionPort`. The draft names only `StateMachineInterpreter` and `DefaultActionExecutor`.
- **Parent POM.** Every library and the prototype inherit `sequeless-build-parent` (prototype 0.1.2; kernel, expression, and filter 0.1.3). That parent sets the Java release and publishing.
- **How to read the checkouts.** Tell the newcomer that local clones can be on other branches; use tags or origin/main.

## 3. STE (ASD-STE100) problems

### Systematic problems

1. **Undefined technical nouns.** The "Terms" line lists names without definitions (sealed interface, record, AST, SNAPSHOT). Other terms are never defined: JEXL (only partly), DSL, SPI, JsonNode, guard, rollup, cut-over, "upstream", `-am`, manifest, ArchUnit. STE requires that you define each technical noun one time, at first use.
2. **One word, many names (consistency).** The same thing has many names: "tree" / "AST" / "filter tree" / `FilterNode`; "libs" / "libraries" / "artifact" / "module"; "caller identity" / "caller ID" / "principal" / "principalId"; "pins" / "sets" / "lists" for versions. Pick one term for each and keep it.
3. **Words with more than one meaning.** "release" (noun, verb, workflow name, Maven profile); "port" (Java interface versus network port); "adapter" (hexagonal adapter versus the config value `adapter: jexl`); "pins" (informal); "sees" ("Expressions see self.<name>"). STE allows one meaning for each word.
4. **Non-approved or informal words.** pick, pins, wires, condensed, stale, unread, incl., re, "cut-over", "always-bump-patch" used as prose. Use approved simple verbs: "use", "set", "connect", "is not current", "includes", "about".
5. **Long, many-topic sentences.** Many lines have three or more facts joined by semicolons (line 12 has five topics; line 18 has six; line 26 has six). STE requires one topic per sentence and a limit of 20 words.
6. **Passive voice and missing agent.** "LIKE rendered as ILIKE", "declared without version (BOM sets it)", "Prototype deleted its own copies in cut-over", "Filter-tree loader unread".
7. **Missing articles.** "Prototype is an app", "Push to main runs release workflow calling shared workflow". STE keeps articles ("the", "a"). The condensed style is acceptable in notes, but not in the newcomer text.
8. **Symbols for words.** "->", "/", "..", "1 2 3" as list markers in running text. Write the words, or make a real list.

### Five rewrite examples

1. Draft: "Push to main runs release workflow calling shared workflow tag workflows-v0.1.2 (release-please, then builds released modules and upstream with -am)."
   STE: "When a commit goes to main, the release workflow starts. This workflow calls a shared workflow from sequeless-build at the tag `workflows-v0.1.2`. The shared workflow runs release-please. Then it builds each released module."
2. Draft: "Prototype use: core depends on filter-core (BOM version). Main code reads only tree types: FilterNodeToCriterion turns AND-only tree into Criterion list; rejects OrFilter and AnyFilter."
   STE: "The module sequeless-core uses filter-core. The BOM sets the version. The main code uses only the filter tree types. The class `FilterNodeToCriterion` changes a filter tree into a list of `Criterion` values. It accepts only AND trees. It stops with an error if the tree has an `OrFilter` or an `AnyFilter`."
3. Draft: "Expressions see self.<name>, self.state, principal."
   STE: "An expression can read three names: `self.<name>` (a property value), `self.state` (the current state), and `principal` (the caller ID)."
4. Draft: "filter-sql-postgres-adapter (PostgresQueryBuilder; LIKE rendered as ILIKE)."
   STE: "The module filter-sql-postgres-adapter contains `PostgresQueryBuilder`. This class writes the `like` operator as the SQL `ILIKE` operator. `ILIKE` ignores the difference between upper case and lower case."
5. Draft: "Release: README says all modules release together; release config and manifest version each module separately; always-bump-patch, skip-snapshot; every release is a patch bump incl. breaking changes."
   STE: "The filter README says that all modules have one shared version. This text is not current. The release configuration gives each module its own version. Each release increases only the patch number. This rule applies also to changes that break callers."
