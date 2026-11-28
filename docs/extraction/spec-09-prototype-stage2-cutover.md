# Spec 9 — Prototype stage 2 cut-over: consume expression

Issue: [#37](https://github.com/sequeless/sequeless-prototype/issues/37). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Tracker: `docs/extraction/remaining-work.md`.

## 1. Identity, stage and dependencies

- Stage 2, cut-over. Spec 9 of 19. The prototype stops carrying the expression port, its JEXL adapter, its Spring autoconfiguration and its contract. It consumes them from `sequeless-expression` 0.1.1 through `sequeless-bom` 0.1.2.
- Waits for spec 8 ([#36](https://github.com/sequeless/sequeless-prototype/issues/36), `sequeless-expression`). Spec 8 is done and its issue is closed (`remaining-work.md`, Done; `gh issue view 36`).
- Unblocks spec 10 ([#38](https://github.com/sequeless/sequeless-prototype/issues/38)), which waits for this spec.
- Branch `spec-09-prototype-stage2-cutover`, cut from `origin/main` at `de9ebfc`.

## 2. Moves

Provenance:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `fda7c3e`, the last commit on `main` that changes the moved paths (`git log d53d3f1..origin/main -- <paths>`). It is one javadoc line in `ExpressionException.java`. The moved content at `fda7c3e` is the content that spec 8's import, `017b292dd`, copied.
- **Import commit (in `sequeless-expression`, not in this repo):** `017b292dd687d2f4bd0d587428d46601a17f6a91`, "feat(expression): import the expression port, JEXL adapter and contract (#36)". Checked with the GitHub API.
- **Date of the move:** the author date of the cut-over commit.

Deleted in the cut-over commit (13 paths):

| Source (prototype) | Target (`sequeless-expression` 0.1.1) | Files |
|---|---|---|
| `sequeless-spi/src/main/java/org/sequeless/spi/expression/` | `expression-api` | 4: `ExpressionContext.java`, `ExpressionException.java`, `ExpressionPort.java`, `package-info.java` |
| `sequeless-adapter-expression-jexl/` (whole module) | `expression-jexl-adapter` and `expression-spring-boot-autoconfig` | 7: `pom.xml`; main `JexlExpressionAutoConfiguration.java`, `JexlExpressionPort.java`, `package-info.java`; `META-INF/spring/…AutoConfiguration.imports`; tests `JexlExpressionAutoConfigurationTest.java`, `JexlExpressionPortContractTest.java` |
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/expression/` | `expression-testkit` | 2: `ExpressionContract.java`, `package-info.java` |

The rest of `sequeless-spi-testkit` stays. Nothing in the prototype is added in this spec.

## 3. Prerequisite refactors

None. The one javadoc change the moved code needed (`ExpressionException`, `fda7c3e`) is already on `main`. Spec 8 section 3.1 recorded it.

## 4. Package renames

The prototype does not rename anything. It deletes the old classes and imports the names the released artifacts use. The names come from `sequeless-expression` 0.1.1 (checked in the repo's `src/main/java` tree on `main`):

| From (prototype) | To (released 0.1.1) |
|---|---|
| `org.sequeless.spi.expression` | `org.sequeless.expression.api` |
| `org.sequeless.adapter.expression.jexl.JexlExpressionPort` | `org.sequeless.expression.jexl.JexlExpressionPort` |
| `org.sequeless.adapter.expression.jexl.JexlExpressionAutoConfiguration` | `org.sequeless.expression.autoconfigure.JexlExpressionAutoConfiguration` |
| `org.sequeless.testkit.expression.ExpressionContract` | `org.sequeless.expression.testkit.ExpressionContract` |

The autoconfiguration's package changes. The property it reads does not: `@ConditionalOnProperty(name = "sequeless.expression.adapter", havingValue = "jexl")` is unchanged, so the six `sequeless-app` tests that set it keep working.

### Consumers to update (verified by import grep)

Main code:

- `sequeless-core`: `statemachine/StateMachineInterpreter.java`, `automation/DefaultActionExecutor.java`, `usecase/DefaultTransitionService.java`.
- `sequeless-app`: `config/CoreConfiguration.java`, `rest/ObjectsController.java`.

Test code:

- `sequeless-core`: `automation/DefaultTriggerEvaluatorTest.java`, `automation/DefaultActionExecutorTest.java`, `usecase/DefaultTransitionServiceTest.java`.
- `sequeless-app`: `config/CoreConfigurationContextRunnerTest.java`.

Javadoc-only references, which break javadoc once the types leave the module, so they change in the same commit:

- `sequeless-spi/src/main/java/org/sequeless/spi/package-info.java:22`: `{@link org.sequeless.spi.expression}`. `sequeless-spi` gets no dependency on `expression-api`, so this becomes `{@code}`.
- `sequeless-spi/src/main/java/org/sequeless/spi/automation/package-info.java:9` and `ResolvedWebhookRequest.java:9`: `{@link …ExpressionPort}`. Same change: `{@code}`.
- `sequeless-spi/src/main/java/org/sequeless/spi/automation/ActionExecutor.java:37`: `{@code org.sequeless.spi.expression.ExpressionContext#selfProperties()}`. The name is updated.
- `sequeless-core/src/main/java/org/sequeless/core/automation/package-info.java:22` and `core/usecase/DefaultBusinessObjectService.java:623`: these become `{@link}` to `org.sequeless.expression.api` types, because `sequeless-core` gets the dependency (section 5).
- `sequeless-adapter-automation-inprocess/.../WebhookDeliveryException.java:10`: `{@code org.sequeless.spi.expression…}`. The name is updated.
- Comments in `sequeless-adapter-automation-temporal/pom.xml:58` and `sequeless-adapter-automation-inprocess/pom.xml:48` name `sequeless-adapter-expression-jexl`'s test. They name the expression repo's test instead.

## 5. Modules and their dependencies

- **Root `pom.xml`:** remove `<module>sequeless-adapter-expression-jexl</module>` (line 30). Change the `sequeless-bom` import from 0.1.1 to 0.1.2. 0.1.2 lists the four expression artifacts at 0.1.1 (`sequeless/sequeless-build` `sequeless-bom/pom.xml`, lines 61 to 80).
- **`sequeless-core/pom.xml`:** add `org.sequeless:expression-api`, no version (managed by the BOM). Core's main and test code import the API (section 4).
- **`sequeless-app/pom.xml`:** replace `sequeless-adapter-expression-jexl` (lines 70 to 73) with `expression-spring-boot-autoconfig`, no version. Add `expression-api`, no version, because `CoreConfiguration` and `ObjectsController` import it directly. Neither depends on it only through the autoconfig.
- **`sequeless-spi`:** no new dependency. It imports nothing from the expression packages at HEAD (section 4 lists only javadoc).
- **`sequeless-spi-testkit`:** loses the `testkit/expression/` package. The POM has no expression reference (`grep -c expression` returns 0).
- **`ArchitectureTest` (`sequeless-app`):** `coreDependsOnlyOnJdkSpiAndItself` (around line 72) allows `java..`, `CORE_PACKAGE`, `SPI_PACKAGE`, `KERNEL_PACKAGE` and `SLF4J_PACKAGE`. Core now imports `org.sequeless.expression.api`, so the rule fails unless the list gains `EXPRESSION_PACKAGE = "org.sequeless.expression.."`. This is the same kind of change spec 7 section 5 made for `KERNEL_PACKAGE`. The spi rule (around line 56) needs no change, because spi imports no expression type.
- **No SNAPSHOT dependency.** Every new dependency is the released 0.1.1 through the BOM (section 7).

## 6. Release config (first version, tags, BOM change)

- **Prototype release:** none in this spec.
- **First version of the expression artifacts:** 0.1.1 for all four modules. The owner chose it (`remaining-work.md`, spec 8). The 0.1.0 tag exists but is GitHub-only.
- **BOM:** `sequeless-bom` 0.1.2 lists `expression-api`, `expression-testkit`, `expression-jexl-adapter` and `expression-spring-boot-autoconfig` at 0.1.1. Spec 8 released it. Nothing changes here except the root POM's import, which moves from 0.1.1 to 0.1.2.
- **BOM CI:** `sequeless/sequeless-build` `bom-contracts` runs one test, `BomListsOnlyReleasesTest` (checked in the repo's tree on `main`). No testkit contract runs there. Its `pom.xml` (lines 42 and 43) says the `sequeless-bom` import returns "with the first testkit contract this module runs against the BOM". The expression contract would be that first contract. See section 9.

## 7. CI and workflows

- `.github/` is unchanged. The prototype pins stay where they are: `ci.yml` at `workflows-v0.1.0` (line 13), `codeql.yml` at `workflows-v0.1.1` (line 15). No Renovate PR moves them.
- **Rule:** the prototype's CI runs against released artifacts, never a SNAPSHOT on `main`. After this change the expression artifacts come from the BOM at 0.1.1.
- **Local gate:** `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`. The root POM has no `release` profile of its own. It comes from `sequeless-build-parent` 0.1.2, which defines `<id>release</id>` (checked in `~/.m2`). `./mvnw` does not exist in this repo.

### Definition of done

Quoted from issue #37, with status at this branch's base:

- "The new repo's CI is green." Not applicable: this spec creates no repo.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Open. The CI run on the PR is the proof.
- "The BOM's CI, which runs every testkit contract, is green." Open. The BOM CI is green today (`sequeless-build` CI on `main`), but it runs no testkit contract. Section 9, item 1.
- "The release and the BOM re-release are done." Done in spec 8: `expression` 0.1.1 is released, and `sequeless-bom` 0.1.2 lists it.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Met by the cut-over commit, which names `sequeless-expression` and `017b292dd687d2f4bd0d587428d46601a17f6a91`.

## 8. ADRs

- None new in this spec.
- The decisions it relies on are recorded in [spec 8](spec-08-sequeless-expression.md) and in issue [#23](https://github.com/sequeless/sequeless-prototype/issues/23).

## 9. Open questions

1. **BOM CI contract gap.** The DoD clause "the BOM's CI, which runs every testkit contract" cannot be met until the expression contract is wired into `sequeless-build` `bom-contracts`. The spec 8 draft (section 6) said that wiring returns the `sequeless-bom` import in the same change. It was not done in the 0.1.2 release. Decide: (a) add the expression contract to `bom-contracts` in a `sequeless-build` PR, as part of this spec; (b) leave it to a later spec and mark the clause open here; (c) something else.
2. **Explicit `expression-api` dependency in `sequeless-app`.** Drafted as explicit, although the autoconfig pulls it in transitively.
