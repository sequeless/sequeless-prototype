# Spec 4 — Prototype stage 1 prepare: `Principal` drops `displayName`

Issue: [#32](https://github.com/sequeless/sequeless-prototype/issues/32). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Tracker: `docs/extraction/remaining-work.md`.

## 0. Scope and decisions

- **Change:** `Principal` becomes `(id, roles)`. The `displayName` component, its javadoc and its blank check go.
- **Why:** the kernel's `Principal` is decided as `(id, roles)` in [#12](https://github.com/sequeless/sequeless-prototype/issues/12), resolution item 7. `facet-core` already carries that shape. Draft ADR 0003 (sequeless-ontology, not yet committed) records the reason: one shared `Principal`, so two types do not drift apart.
- **/whoami is the one visible effect.** [#12](https://github.com/sequeless/sequeless-prototype/issues/12) and [#32](https://github.com/sequeless/sequeless-prototype/issues/32) do not say what `GET /whoami` returns. The owner chose: keep the `displayName` field on the body and fill it from the principal id. For the anonymous caller the value changes from `"Anonymous"` to `"anonymous"`. Nothing in the docs fixes the value; phase 0 only requires "the anonymous principal and the default tenant" (`docs/architecture/prompts/phase-0.md`).
- **Unchanged:** the UI model `WhoAmIResponse` (`sequeless-ui/projects/core-api/src/lib/models/index.ts`) and its type test keep `displayName`. The phase-3 spec requires UI interfaces to mirror DTO records field-for-field.
- **Not a move.** No code leaves the prototype in this spec.

## 1. Identity, stage and dependencies

- Stage 1, prepare. Waits for spec 3 ([#31](https://github.com/sequeless/sequeless-prototype/issues/31), done).
- Unblocks spec 5 ([#33](https://github.com/sequeless/sequeless-prototype/issues/33), `sequeless-kernel`), which takes the new `Principal` shape.

## 2. Moves

None. Provenance: not applicable (this spec moves no code).

## 3. Prerequisite refactors

### 3.1 `Principal` record (`sequeless-spi`)

- `Principal(String id, Set<String> roles)`. `ANONYMOUS` is `new Principal("anonymous", Set.of())`.
- The compact constructor keeps the id and roles checks.

### 3.2 Construction sites

Every `new Principal(id, displayName, roles)` call loses its middle argument. The grep at the time of writing found 25 calls, 5 in `src/main` and 20 in tests:

- `src/main`: `sequeless-spi/.../spi/Principal.java` (ANONYMOUS); `sequeless-adapter-automation-temporal/.../ActionWorkflowImpl.java`; `sequeless-spi-testkit/.../testkit/Fixtures.java`; `sequeless-spi-testkit/.../testkit/automation/AutomationContract.java`; `sequeless-app/.../automation/OutboxRelay.java`.
- Tests: 20 calls across `sequeless-spi`, `sequeless-core` and `sequeless-adapter-automation-inprocess`.

### 3.3 `/whoami` mapping

`WhoAmIResponse.from()` fills `displayName` from `result.principal().id()`. The record keeps the component.

### 3.4 Automation rebuild

`OutboxRelay` and `ActionWorkflowImpl` rebuild the principal from its id alone. Their javadocs no longer mention `displayName`.

## 4. Package renames

None.

## 5. Modules and their dependencies

No module boundary changes. `sequeless-spi`, `sequeless-spi-testkit`, `sequeless-core`, `sequeless-app`, `sequeless-adapter-automation-temporal` and `sequeless-adapter-automation-inprocess` compile against the new record. `sequeless-ui` is unchanged.

## 6. Release config

None in this spec. The new `Principal` shape ships with the kernel's first release, 0.1.0, in spec 5 ([#33](https://github.com/sequeless/sequeless-prototype/issues/33)). Releasing is outward-facing and needs owner approval.

## 7. CI and workflows

No change. The local gate is `mvn -B verify` (`README.md:25`; the prototype has no `mvnw`). CI is unchanged.

## 8. ADRs

No new ADR. The decision is recorded in [#12](https://github.com/sequeless/sequeless-prototype/issues/12) (resolution item 7) and draft ADR 0003 (sequeless-ontology, not yet committed there).

Docs that show the old value: `README.md` (updated to `"anonymous"`). `docs/phase-0-verification.md` is a dated verification record of a past run and is left unchanged.

## 9. Implementation order

1. `Principal` record and `ANONYMOUS`.
2. `WhoAmIResponse.from()`.
3. `OutboxRelay`, `ActionWorkflowImpl`, `Fixtures`, `AutomationContract` (construction sites and javadocs).
4. Tests: `PrincipalTest` (drop the `displayName` assertion and `rejectsNullOrBlankDisplayName`); `OutboxRelayTest` (assert the id); `WhoAmIEndToEndTest` (expect `"anonymous"`); the 20 test construction sites.
5. `README.md` `/api/whoami` example.
6. `mvn -B verify`.

## 10. Definition of done

- `mvn -B verify` passes in the prototype, against the sequeless-build parent as already pinned (0.1.2 on `main`).
- `grep -rn displayName --include=*.java` finds only `WhoAmIResponse`'s field and its javadoc.
- Outward, needs owner approval and not done here: the prototype's CI against released artifacts, the kernel release and BOM re-release (spec 5), and closing #32.
