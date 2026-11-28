# Spec 5 — Stage 1: `sequeless-kernel` (identity, values)

Issue: [#33](https://github.com/sequeless/sequeless-prototype/issues/33). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Tracker: `docs/extraction/remaining-work.md`.

## 0. Scope and decisions

- **Change:** a new two-module repo `sequeless-kernel` ([#12](https://github.com/sequeless/sequeless-prototype/issues/12), resolution item 1; [#33](https://github.com/sequeless/sequeless-prototype/issues/33)).
  - `kernel-identity`: `Scope`, `TenantId`, `Principal`.
  - `kernel-values`: `ObjectId`, the sealed `Value` and its eight permitted variants, `ObjectNotFoundException`.
- **Packages (decided, option A):** `org.sequeless.kernel.identity` and `org.sequeless.kernel.values`. This follows the repo-segment convention of sequeless-filter (`org.sequeless.filter.api`). Option B (`org.sequeless.identity`) could collide with a future repo; option C keeps packages that no longer match their repo.
- **Not moved:** `StaleObjectException` stays on the Command side ([#12](https://github.com/sequeless/sequeless-prototype/issues/12), item 2). The rest of `org.sequeless.spi.object` also stays.
- **Prerequisite (decided, option A):** the prototype demotes links to types that stay, before the move (section 3.1). This is javadoc-only; it is not a behaviour change.
- **History:** fresh, with a `PROVENANCE.md` in the first commit ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §3).
- **Owner-level, not done by this spec:** creating the repo, the 0.1.0 release and the BOM re-release. All three are outward-facing.

## 1. Identity, stage and dependencies

- Stage 1. Waits for spec 1 ([#29](https://github.com/sequeless/sequeless-prototype/issues/29), done) and spec 4 ([#32](https://github.com/sequeless/sequeless-prototype/issues/32)). The spec 4 code is merged ([#69](https://github.com/sequeless/sequeless-prototype/pull/69), `ab6d9eb`), and its issue is closed.
- Runs in parallel with spec 6 ([#34](https://github.com/sequeless/sequeless-prototype/issues/34), sequeless-filter).
- Unblocks spec 7 ([#35](https://github.com/sequeless/sequeless-prototype/issues/35), prototype stage 1 cut-over), which also waits for spec 6.

## 2. Moves

Provenance source: `sequeless-prototype`, `main` at `a2e402a` when this spec was written. The move commit records the SHA it actually moves from. Date: the date of the move.

| Types | Source (`sequeless-spi/src/main/java/org/sequeless/spi/…`) | Target module |
|---|---|---|
| `Scope`, `TenantId`, `Principal` | `Scope.java`, `TenantId.java`, `Principal.java` | `kernel-identity` |
| `ObjectId` | `object/ObjectId.java` | `kernel-values` |
| `Value` (sealed), `TextValue`, `IntegerValue`, `DecimalValue`, `BoolValue`, `DateTimeValue`, `DateValue`, `ReferenceValue`, `ListValue` | `object/Value.java` and `object/*Value.java` | `kernel-values` |
| `ObjectNotFoundException` | `object/ObjectNotFoundException.java` | `kernel-values` |

Tests that move with them: `PrincipalTest`, `ScopeTest`, `TenantIdTest` (`spi/`); `ObjectIdTest`, `ValueTest`, `ObjectNotFoundExceptionTest` (`spi/object/`).

**Not moved:** `StaleObjectException` (and `StaleObjectExceptionTest`), and every Command-side type. `StaleObjectException` is in the same package as the moving types, so `org.sequeless.spi.object` is split by this move.

**`PROVENANCE.md`** (first commit of `sequeless-kernel`): source repo, source SHA, source paths, the move, the date.

## 3. Prerequisite refactors (in `sequeless-prototype`, before the move)

### 3.1 Javadoc links to types that stay (option A)

Demote `{@link X}` to `{@code X}` where `X` stays in the prototype. Nothing else changes.

- `object/Value.java:9` and `:14`: `BusinessObject`.
- `object/ObjectId.java:7`: `BusinessObject`.
- `object/ReferenceValue.java:6`: `ObjectStorePort`, `BusinessObject`.
- `object/ObjectNotFoundException.java:6`, `:8`, `:9`, `:11`: `ObjectStorePort#commit`, `Update`, `Delete`, `ChangeSet`, `StaleObjectException`.

Links to moving types (`ListValue`, `TextValue`, `ObjectId`, `ReferenceValue`) and to JDK types (`UUID`, `BigDecimal`, `Instant`, `Set`) stay as they are. The check was a grep over the 13 moving files at `a2e402a`. Javadoc fails on an unresolved link, and the 0.1.1 release of sequeless-filter already failed in javadoc.

### 3.2 `Principal` shape

Already done in [spec 4](spec-04-prototype-stage1-prepare.md). `Principal` is `(id, roles)`, and nothing more changes here.

## 4. Package renames

| From | To | Types |
|---|---|---|
| `org.sequeless.spi` | `org.sequeless.kernel.identity` | `Scope`, `TenantId`, `Principal` |
| `org.sequeless.spi.object` | `org.sequeless.kernel.values` | `ObjectId`, `Value` and its eight variants, `ObjectNotFoundException` |

- The renames happen in place, in the first commit of `sequeless-kernel` ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §2).
- The prototype does not rename in this spec. Its cut-over ([spec 7](https://github.com/sequeless/sequeless-prototype/issues/35)) deletes the old classes and updates the imports in one commit. At `a2e402a`, **125 files** import one of the six moving types. There is no bridge package ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §2).

## 5. Modules and their dependencies

- **`kernel-identity`:** depends on the JDK only. Package `org.sequeless.kernel.identity`.
- **`kernel-values`:** depends on the JDK only. Package `org.sequeless.kernel.values`. It does **not** depend on `kernel-identity`: the value-side files do not reference `Scope`, `TenantId` or `Principal`, and the identity files do not reference the value types (checked by grep at `a2e402a`).
- **Reactor:** the root is an aggregator and is never released ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §4). Each module takes `sequeless-build-parent` directly.
- **Java:** libraries compile with `--release 21` ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §1).
- **Enforcement:** an enforcer `bannedDependencies` rule that bans Spring, Jena and Lombok, plus `dependency:analyze` ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §5). Each module gets its own `BoundaryRulesTest`, from `sequeless-template`.
- **Versioning after 0.1.0** ([#12](https://github.com/sequeless/sequeless-prototype/issues/12), item 6): adding a permitted subtype to the sealed `Value` is a **major** bump; adding a method with a default implementation is a **minor** bump.

## 6. Release config

- **First version:** `0.1.0` for `kernel-identity` and for `kernel-values` ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §4: first releases of never-published modules are pinned to 0.1.0).
- **Mechanism:** release-please manifest mode with the `maven-workspace` plugin, as in sequeless-filter ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §4). The first release uses `release-as: 0.1.0`, and a follow-up PR removes the pins, as sequeless-filter did in PR #16. Keep `skip-snapshot`.
- **Tags:** `kernel-identity-v0.1.0` and `kernel-values-v0.1.0`.
- **Deploy namespace:** set `sequeless.github.repository` to `sequeless-kernel` in each module's POM. The parent hard-codes `sequeless-build`, and without the override the deploy gets 403. This is the sequeless-filter failure ([sequeless-filter #19](https://github.com/sequeless/sequeless-filter/pull/19)). The root aggregator's property does not reach the modules.
- **BOM:** `sequeless-bom` in `sequeless-build` is first released in this stage ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §1). Its re-release follows the kernel 0.1.0 release and runs every testkit contract together ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §6).

## 7. CI and workflows

- Created from `sequeless-template` ([spec 2](https://github.com/sequeless/sequeless-prototype/issues/30)): Makefile, `mvnw`, commitlint, devcontainer and the canonical `BoundaryRulesTest` ([#17](https://github.com/sequeless/sequeless-prototype/issues/17), §2).
- Reusable workflows `maven-ci.yml`, `maven-codeql.yml` and `maven-release.yml`, pinned at `workflows-v0.1.2`, the latest tag ([#62](https://github.com/sequeless/sequeless-prototype/issues/62)). No Sonar job ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §1).
- **Known issue:** a bot-created release PR gets no CI runs. Closing and reopening it starts them ([sequeless-filter #18](https://github.com/sequeless/sequeless-filter/pull/18)).
- **Branch ruleset:** the owner sets the policy. The extra-approval setting blocked merges in sequeless-filter ([remaining-work.md, Open items](remaining-work.md)).

## 8. ADRs

- **In `sequeless-kernel`'s `docs/adr/`:** an ADR that package names follow the repo segment (navigator recommendation). Status `proposed` until the 0.1.0 release.
- **Draft ADR 0003 (sequeless-ontology):** `facet-core` may depend on `kernel-identity`, and nothing else. The draft is on branch `wayfinder/kernel-adr-0002-amendment` at `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`. It is not committed. It belongs in `sequeless-ontology` at stage 3 ([spec 11](https://github.com/sequeless/sequeless-prototype/issues/39)), not here.
- **Not in this spec:** the vocabulary (Ontology, Schema, Behaviour) in [#12](https://github.com/sequeless/sequeless-prototype/issues/12), item 4. It arrives with the Schema and Behaviour split ([spec 11](https://github.com/sequeless/sequeless-prototype/issues/39)).

## 9. Implementation order

1. **Prototype PR:** demote the staying-type links (section 3.1). CI green, no behaviour change ([#23](https://github.com/sequeless/sequeless-prototype/issues/23), §2).
2. **Owner:** create `sequeless-kernel` from `sequeless-template`.
3. **`sequeless-kernel` first commit:** the moved code with the renamed packages, plus `PROVENANCE.md`. Fresh history.
4. **Release config:** release-please manifest, `release-as: 0.1.0`, the deploy namespace per module, `workflows-v0.1.2`.
5. **Owner-approved release** of 0.1.0 for both modules. Then a follow-up PR removes the `release-as` pins.
6. **Owner-approved BOM re-release** in `sequeless-build`, including kernel 0.1.0.
7. **Prototype:** no cut-over here. Spec 7 consumes the released artifacts and deletes the moved code.

## 10. Definition of done

- `sequeless-kernel` CI is green on `main` (`build / maven`, `analyze`).
- Both modules are released at 0.1.0 and visible in GitHub Packages under the `sequeless-kernel` namespace (checked with `gh api`, as for sequeless-filter).
- The BOM CI runs every testkit contract green, and the BOM is re-released.
- The section 3.1 javadoc fix is merged in the prototype before the move.
- The prototype's CI against released artifacts (never a SNAPSHOT on `main`) applies from spec 7, not from this spec.
- Not done here, and needing the owner: repo creation, the 0.1.0 release, the BOM re-release, and closing [#33](https://github.com/sequeless/sequeless-prototype/issues/33).
