# Spec 7 — Prototype stage 1 cut-over: consume kernel and filter

Issue: [#35](https://github.com/sequeless/sequeless-prototype/issues/35). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Tracker: `docs/extraction/remaining-work.md`.

## 1. Identity, stage and dependencies

- Stage 1, cut-over. Spec 7 of 19. The prototype stops carrying the moved identity and value types and consumes them from `sequeless-kernel`.
- Waits for spec 5 ([#33](https://github.com/sequeless/sequeless-prototype/issues/33), `sequeless-kernel`) and spec 6 ([#34](https://github.com/sequeless/sequeless-prototype/issues/34), sequeless-filter). Both are done and closed (`remaining-work.md`, Done).
- Unblocks spec 8 ([#36](https://github.com/sequeless/sequeless-prototype/issues/36), sequeless-expression), which waits for specs 5 and 7 (`remaining-work.md`, table).
- Branch `spec-07-prototype-stage1-cutover` is five commits ahead of `main` (`52fa2a5`):
  - `d5f56bd` and `8d1997a` (`docs(extraction)`): the pre-factor plan, and the decision that spec 7 runs first.
  - `c31a6df` and `5d2df27` (`docs(testkit)`, `docs(javadoc)`): javadoc-only prerequisites (section 3).
  - `193d0b8` (`refactor(spi)`): the cut-over.
- The issue title says "consume kernel and filter". The cut-over consumes the kernel only: no `pom.xml` at HEAD declares a filter artifact. Whether the filter part of the title belongs to this spec is not verified in this spec, and the owner should confirm it.
- Status, per `remaining-work.md` (In flight): the cut-over is committed locally and not pushed. The CI run against released artifacts, the push and the PR are still open. Issue #35 is open.

## 2. Moves

Provenance, as recorded in the kernel's `PROVENANCE.md` and in the cut-over commit:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `3a070ad` (`main`, after the javadoc prerequisite [#75](https://github.com/sequeless/sequeless-prototype/pull/75)), as recorded in the kernel's `PROVENANCE.md`. The cut-over commit's parent is `5d2df27`. `git diff 3a070ad 5d2df27` is empty for all 20 deleted paths, so the moved content is the same at both SHAs.
- **Source paths:** the 20 files in the table below, all under `sequeless-spi/`.
- **The move:** the files are deleted from the prototype in `193d0b8`. The same types come from `sequeless-kernel` 0.1.0 through `sequeless-bom` 0.1.1 (section 5).
- **Import commit (in `sequeless-kernel`, not in this repo):** `f036c1d814e1dbbe7f27ccb4c54c4086c33ad3e8`, "feat(kernel): import identity and values from sequeless-prototype (#2)", dated 2026-11-04. Checked with the GitHub API.
- **Date of the move:** 2026-11-05 (author date of `193d0b8`).

Deleted in `193d0b8` (20 files: 14 main, 6 test):

| Target module | Main sources (`src/main/java/org/sequeless/spi/…`) | Tests (`src/test/java/org/sequeless/spi/…`) |
|---|---|---|
| `kernel-identity` | `Principal.java`, `Scope.java`, `TenantId.java` | `PrincipalTest.java`, `ScopeTest.java`, `TenantIdTest.java` |
| `kernel-values` | `object/BoolValue.java`, `object/DateTimeValue.java`, `object/DateValue.java`, `object/DecimalValue.java`, `object/IntegerValue.java`, `object/ListValue.java`, `object/ObjectId.java`, `object/ObjectNotFoundException.java`, `object/ReferenceValue.java`, `object/TextValue.java`, `object/Value.java` | `object/ObjectIdTest.java`, `object/ObjectNotFoundExceptionTest.java`, `object/ValueTest.java` |

The list is from `git diff --diff-filter=D` between `5d2df27` and `193d0b8`.

**Not moved:** `object/StaleObjectException.java` and `object/StaleObjectExceptionTest.java`. Both stay in the prototype and get a one-line addition in `193d0b8`, not a deletion.

**The whole commit:** 184 files changed, 20 deleted, 164 modified, none added (`git show --stat 193d0b8`). A grep at HEAD finds no Java file that imports the old identity or value names.

## 3. Prerequisite refactors

### 3.1 Javadoc links (spec 5, already on `main`)

`3a070ad` (`refactor(spi): demote javadoc links to types that stay in the prototype`, [#75](https://github.com/sequeless/sequeless-prototype/pull/75)) is on `main`. It is the spec 5 section 3.1 change. It is not part of this branch.

### 3.2 `Principal` shape (spec 4, already on `main`)

`Principal` is `(id, roles)`. That was done in [spec 4](spec-04-prototype-stage1-prepare.md) ([#32](https://github.com/sequeless/sequeless-prototype/issues/32)). Nothing changes here.

### 3.3 Javadoc-only commits on this branch

- `c31a6df` (`docs(testkit)`): fixes one unresolved javadoc link in `AggregateContract`. One line in one file.
- `5d2df27` (`docs(javadoc)`): fixes the remaining javadoc errors in core, app and automation. Six lines in five files.
- Every changed line in both commits is a javadoc comment line (checked with `git show`), so there is no behaviour change.
- `remaining-work.md` records why they come first: five javadoc errors that were already in HEAD failed the release javadoc run.

## 4. Package renames

The renames happened in the kernel ([spec 5](spec-05-sequeless-kernel.md), section 4), in place. The prototype does not rename anything. It deletes the old classes and imports the new packages.

| From | To | Types |
|---|---|---|
| `org.sequeless.spi` | `org.sequeless.kernel.identity` | `Scope`, `TenantId`, `Principal` |
| `org.sequeless.spi.object` | `org.sequeless.kernel.values` | `ObjectId`, `Value` and its eight variants, `ObjectNotFoundException` |

- The kernel's `PROVENANCE.md` records the renames as done in place.
- The old packages stay in the prototype for the types that remain there, such as the ports and `StaleObjectException`.

## 5. Modules and their dependencies

- **Root `pom.xml`:** imports `sequeless-bom` 0.1.1 (`type` `pom`, `scope` `import`) in `dependencyManagement`. The root declares neither kernel artifact directly.
- **Parent:** unchanged, `sequeless-build-parent` 0.1.2.
- **`kernel-identity`** is declared without a version by 9 modules: `sequeless-spi`, `sequeless-core`, `sequeless-spi-testkit`, `sequeless-app`, `sequeless-adapter-authz-permitall`, `sequeless-adapter-automation-inprocess`, `sequeless-adapter-automation-temporal`, `sequeless-adapter-ontology-jena`, `sequeless-adapter-persistence-postgres`.
- **`kernel-values`** is declared without a version by 7 modules: `sequeless-spi`, `sequeless-core`, `sequeless-spi-testkit`, `sequeless-app`, `sequeless-adapter-expression-jexl`, `sequeless-adapter-ontology-jena`, `sequeless-adapter-persistence-postgres`.
- **No SNAPSHOT dependency:** a scan of the 11 POMs at HEAD finds no SNAPSHOT version inside any `<dependency>` block. The `0.1.0-SNAPSHOT` strings are the prototype's own project version.
- **`sequeless-spi` `package-info.java`:** no longer claims that the module imports nothing outside the JDK. It names `org.sequeless.kernel..` as allowed.
- **`ArchitectureTest` (in `sequeless-app`):** `KERNEL_PACKAGE` (`org.sequeless.kernel..`) is allowed in the spi and core rules. `suiteActuallyAnalyzesEveryModule` looks up `org.sequeless.kernel.identity.Scope`.

## 6. Release config (first version, tags, BOM change)

- **Prototype release:** none in this spec.
- **First version of the moved types:** `kernel-identity` 0.1.0 and `kernel-values` 0.1.0, released from `sequeless-kernel` in [spec 5](spec-05-sequeless-kernel.md). The tags `kernel-identity-v0.1.0` and `kernel-values-v0.1.0` exist in `sequeless/sequeless-kernel` (checked with the GitHub API).
- **BOM change in `193d0b8`:** `pom.xml` gains a 9-line `sequeless-bom` 0.1.1 import in `dependencyManagement`. The root `pom.xml` at `5d2df27` has no `sequeless-bom` import.
- **BOM tags:** `sequeless-bom-v0.1.0` and `sequeless-bom-v0.1.1` exist in `sequeless/sequeless-build` (checked with the GitHub API). `remaining-work.md` records the 0.1.1 release as sequeless-build [#35](https://github.com/sequeless/sequeless-build/pull/35) and release [#36](https://github.com/sequeless/sequeless-build/pull/36).
- **BOM CI state in `sequeless-build`:** not verified in this spec.

## 7. CI and workflows

- `.github/workflows/ci.yml` calls `maven-ci.yml` at `workflows-v0.1.0` (line 13). `.github/workflows/codeql.yml` calls `maven-codeql.yml` at `workflows-v0.1.1` (line 15).
- The latest tag in `sequeless/sequeless-build` is `workflows-v0.1.2` (checked with the GitHub API). `remaining-work.md` (Open items) notes that no Renovate PR is open to move either pin.
- `.github/` is unchanged on this branch against `main` (`git diff main HEAD -- .github` is empty). This spec does not move the pins.
- Rule: the prototype's CI runs against released artifacts, never a SNAPSHOT on `main`. Section 5 shows no SNAPSHOT dependency at HEAD.
- Local gate: `-Prelease verify` passed on JDK 25 at this HEAD, and `remaining-work.md` records it as green.
- CI run against released artifacts: not yet run (`remaining-work.md`, In flight).

### Definition of done

Quoted from issue #35, with the status at this HEAD:

- "The new repo's CI is green." Status: not verified in this spec. This is the `sequeless-kernel` CI from [spec 5](spec-05-sequeless-kernel.md), section 10.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Status: open. The local release verify is green. The CI run is not done yet.
- "The BOM's CI, which runs every testkit contract, is green." Status: not verified in this spec.
- "The release and the BOM re-release are done." Status: kernel 0.1.0 is released (tags present). The BOM 0.1.1 tag is present in `sequeless-build`, and `remaining-work.md` records the release.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Status: met by `193d0b8`. Its subject names `sequeless-kernel`, and its body names the import commit by its short SHA, `f036c1d81`.

## 8. ADRs

- None new in this spec.
- The decisions this spec relies on are recorded in issue [#12](https://github.com/sequeless/sequeless-prototype/issues/12), in [#23](https://github.com/sequeless/sequeless-prototype/issues/23), and in [spec 5](spec-05-sequeless-kernel.md) (sections 3.1 and 4).
