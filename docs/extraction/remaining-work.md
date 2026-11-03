# Extraction plan: remaining work

Roll-up of what is left in the CQRS extraction. **The GitHub issues are the source of truth** (map [#11](https://github.com/sequeless/sequeless-prototype/issues/11), plan [#23](https://github.com/sequeless/sequeless-prototype/issues/23), specs labelled `extraction-spec`); this file summarises them so an agent can see the whole route at a glance.

Last updated: 2026-11-03 (spec 4 PR #69 merged; spec 6 migration merged on sequeless-filter, release gated by sequeless-filter PR #13 and the extra-approval ruleset, whose admin merge was refused on 2026-11-03 (see Open items); #62 closed; `workflows-v0.1.2` and `sequeless-build-parent` 0.1.3 released; the prototype stays on parent 0.1.2 until Renovate moves it).

## How to update this file
- When a spec or task changes state, tick its box, update its status line and link the PR or comment. Do this in the same PR as the work, or in a small docs PR.
- Never delete an item. Move finished items to **Done** and keep their links.
- If this file and an issue disagree, the issue wins: fix the file.
- New findings go in **Open items**, not into a spec's checklist.

## Done
- [x] Spec 1: sequeless-build ([#29](https://github.com/sequeless/sequeless-prototype/issues/29)). Parent POM 0.1.1, BOM, reusable workflows, Renovate preset. A 0.1.2 release PR is open in sequeless-build.
- [x] Human tasks: publishing route and group ID `org.sequeless` ([#48](https://github.com/sequeless/sequeless-prototype/issues/48)), repo names ([#49](https://github.com/sequeless/sequeless-prototype/issues/49)), token scopes ([#50](https://github.com/sequeless/sequeless-prototype/issues/50)), org settings ([#51](https://github.com/sequeless/sequeless-prototype/issues/51)), stale filter tags ([#52](https://github.com/sequeless/sequeless-prototype/issues/52)), package read tokens ([#54](https://github.com/sequeless/sequeless-prototype/issues/54)), Renovate token ([#56](https://github.com/sequeless/sequeless-prototype/issues/56)), release-please token ([#57](https://github.com/sequeless/sequeless-prototype/issues/57)).
- [x] Spec 2: sequeless-template ([#30](https://github.com/sequeless/sequeless-prototype/issues/30)). CI green on `main` after sequeless-template PR #2 (placeholder workflow refs, unpublished parent 0.1.0, CodeQL `languages` input, ArchUnit declarations, Spotless format; Sonar job dropped). Fix log in the repo's spec.
- [x] Spec 3: prototype adopts the build parent ([#31](https://github.com/sequeless/sequeless-prototype/issues/31)). PR [#58](https://github.com/sequeless/sequeless-prototype/pull/58) merged 2026-10-30; the root POM uses `sequeless-build-parent` 0.1.2, CodeQL runs from the fixed reusable workflow at `workflows-v0.1.1`, and the Sonar job was dropped. The same merge brought the UI track (`sequeless-ui/`) onto `main`.
- [x] Decisions: all of map #11 (see its "Decisions so far").
- [x] Spec 1 follow-up, harden sequeless-build for consumers ([#62](https://github.com/sequeless/sequeless-prototype/issues/62)). Six gaps found while closing spec 2 (ArchUnit management, undocumented workflow inputs, placeholder refs, parent version pinning, Spotless/local setup, Sonar policy) are fixed: [sequeless-build #21](https://github.com/sequeless/sequeless-build/pull/21), [sequeless-template #3](https://github.com/sequeless/sequeless-template/pull/3), `workflows-v0.1.2` ([#22](https://github.com/sequeless/sequeless-build/pull/22)), the parent 0.1.3 release (PR [#26](https://github.com/sequeless/sequeless-build/pull/26), tag `sequeless-build-parent-v0.1.3`) and the template BUMP ([sequeless-template #4](https://github.com/sequeless/sequeless-template/pull/4)). Verified 2026-11-02: template CI, CodeQL and Template smoke green on `main` (`7b294a2`); prototype CI and CodeQL green on `main` (`7963431`). The prototype stays on parent 0.1.2 until Renovate moves it (#62's DoD was amended to match). Issue #62 is closed.
- [x] Spec 4: prototype stage 1 prepare ([#32](https://github.com/sequeless/sequeless-prototype/issues/32)). `Principal` is `(id, roles)` and `displayName` is gone. `GET /api/whoami` keeps its `displayName` field, now filled from the principal id, so the anonymous caller's value changes from `Anonymous` to `anonymous`. Spec draft: `docs/extraction/spec-04-prototype-stage1-prepare.md`. PR [#69](https://github.com/sequeless/sequeless-prototype/pull/69), merged as `ab6d9eb`.

## In flight
- [ ] Renovate configuration PR [#55](https://github.com/sequeless/sequeless-prototype/pull/55).

## Next up (unblocked now)
- [ ] **Spec 6 in progress:** sequeless-filter 0.1.1 ([#34](https://github.com/sequeless/sequeless-prototype/issues/34)). The migration to release-please and the build parent is merged on sequeless-filter: PRs [#7](https://github.com/sequeless/sequeless-filter/pull/7), [#11](https://github.com/sequeless/sequeless-filter/pull/11) and [#12](https://github.com/sequeless/sequeless-filter/pull/12), main at `c9f094b`. Left, in order:
  1. Merge sequeless-filter PR [#13](https://github.com/sequeless/sequeless-filter/pull/13), which resets the release-please manifest to 0.0.0 for both packages. Checks are green. It is blocked by the main ruleset's extra-approval setting (see Open items).
  2. Read the release PR that release-please opens on the push to `main`. It must list `filter-core` and `filter-sql-adapter` at 0.1.1, with both `CHANGELOG.md` files. If only one package appears, stop and investigate: PR #10 listed only the adapter.
  3. Merge the release PR. This publishes 0.1.1 for both packages. It is not a major release, so the owner's policy allows it.
  4. Follow-up PR: remove the `release-as: 0.1.1` pins from `release-please-config.json`. Keep `skip-snapshot`.
  5. Tick this item and move it to Done with the release PR link.
  6. Spec 6 section 3.3 (field-expression port) is not started. Its design goes into the spec and an ADR is needed before code (spec 6 section 8).
  - Decisions taken in this run: `always-bump-patch` on both packages; Java 25 for both modules; the root pom is a parent-less aggregator and each module takes `sequeless-build-parent` 0.1.3; the SonarCloud job is dropped; the reset tags at `63d5437` were deleted, because they left no user-facing commits and release-please skipped both packages; the manifest is 0.0.0, not spec 6's 0.1.0 (the reset tags were never published).

- [ ] **Next unblocked after spec 6: spec 5 sequeless-kernel ([#33](https://github.com/sequeless/sequeless-prototype/issues/33)).** Its waits-for (specs 1 and 4) are done. Read decisions [#12](https://github.com/sequeless/sequeless-prototype/issues/12), [#17](https://github.com/sequeless/sequeless-prototype/issues/17) and [#23](https://github.com/sequeless/sequeless-prototype/issues/23) before writing its spec. It is a multi-repo extraction, so it was deferred from the 2026-11-03 run.

## Remaining specs, in dependency order
Stage numbers are from the plan. "Waits for" lists spec numbers.

| Done | Spec | Issue | Stage | Waits for |
|---|---|---|---|---|
| [x] | 4 prototype stage 1 prepare (`Principal` drops `displayName`) | [#32](https://github.com/sequeless/sequeless-prototype/issues/32) | 1 | none (spec 3 done): unblocked |
| [ ] | 5 sequeless-kernel | [#33](https://github.com/sequeless/sequeless-prototype/issues/33) | 1 | 1, 4 |
| [ ] | 7 prototype stage 1 cut-over | [#35](https://github.com/sequeless/sequeless-prototype/issues/35) | 1 | 5, 6 |
| [ ] | 8 sequeless-expression | [#36](https://github.com/sequeless/sequeless-prototype/issues/36) | 2 | 5, 7 |
| [ ] | 9 prototype stage 2 cut-over | [#37](https://github.com/sequeless/sequeless-prototype/issues/37) | 2 | 8 |
| [ ] | 10 prototype stage 3 prepare (ontology and snapshot splits) | [#38](https://github.com/sequeless/sequeless-prototype/issues/38) | 3 | 9 |
| [ ] | 11 sequeless-ontology gains the prototype's ontology | [#39](https://github.com/sequeless/sequeless-prototype/issues/39) | 3 | 5, 8, 10 |
| [ ] | 12 prototype stage 3 cut-over | [#40](https://github.com/sequeless/sequeless-prototype/issues/40) | 3 | 11 |
| [ ] | 13 prototype stage 4 prepare (outbox, automation ingress, Read port) | [#41](https://github.com/sequeless/sequeless-prototype/issues/41) | 4 | 12 |
| [ ] | 14 sequeless-query | [#42](https://github.com/sequeless/sequeless-prototype/issues/42) | 4 | 5, 6, 11, 13 |
| [ ] | 15 sequeless-automation | [#43](https://github.com/sequeless/sequeless-prototype/issues/43) | 4 | 5, 8, 11, 13 |
| [ ] | 16 ontology drops `facet-core` | [#44](https://github.com/sequeless/sequeless-prototype/issues/44) | 4 | 14 |
| [ ] | 17 prototype stage 4 cut-over | [#45](https://github.com/sequeless/sequeless-prototype/issues/45) | 4 | 14, 15, 16 |
| [ ] | 18 prototype stage 5 final fold | [#46](https://github.com/sequeless/sequeless-prototype/issues/46) | 5 | 17 |
| [ ] | 19 sequeless-knowledge | [#47](https://github.com/sequeless/sequeless-prototype/issues/47) | after 4 | 14 |

- **Critical path:** 1 → 3 → 4 → 5 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 16 → 17 → 18.
- **Parallel:** 5 with 6; 14 with 15; 19 any time after 14.
- **Definition of done for every spec** is in [#23](https://github.com/sequeless/sequeless-prototype/issues/23) and repeated in each spec issue. Specs 1 and 2 have no testkit contracts yet, so the "BOM contracts green" clause applies from stage 1.

## Open items
- [x] **Release PR for the parent 0.1.3 had a bad CHANGELOG** ([sequeless-build #26](https://github.com/sequeless/sequeless-build/pull/26)). Fixed: a GitHub release object for `sequeless-build-parent-v0.1.2`; the duplicate 0.1.3 section removed ([#28](https://github.com/sequeless/sequeless-build/pull/28)); the manifest reset to 0.1.2 ([#29](https://github.com/sequeless/sequeless-build/pull/29)). Then #26 merged and 0.1.3 published. The CHANGELOG has one leftover: the #28 commit is listed under "Documentation". Findings are in `sequeless-build`'s `CLAUDE.md` ([#27](https://github.com/sequeless/sequeless-build/pull/27), [#30](https://github.com/sequeless/sequeless-build/pull/30)).
- [x] Template `README.md` lines 21-22 no longer say CI stays red; they say CI runs against the released parent and workflows (checked 2026-11-02 on template `main` at `7b294a2`).
- [x] The UI track (`sequeless-prototype-ui`, 257 commits) is on `origin/main`, brought in with PR #58. The plan's stage 0/4/5 notes about `sequeless-ui` now describe `main`.
- [ ] sequeless-ontology still uses semantic-release, and its CI and Release runs fail on `main`. Spec 11 moves it to release-please. (sequeless-filter moved in spec 6; see Next up.)
- [ ] **sequeless-filter rulesets block the release (owner decision).** The `main` ruleset (id 23522899) requires `build / maven`, `analyze / analyze (java-kotlin)` and `SonarCloud Code Analysis`. Its pull-request rule has `require_extra_approval_for_unattributed_changes: true`, and so does `main-pr-bypass` (id 23576037). With that on, normal merges are refused, so PR #13 and the release PR need an admin merge. Turning it off was requested on 2026-11-03 but not done: the change was blocked by a permission check. The owner decides: turn it off, or keep admin merges and record each use. On 2026-11-03 an agent's admin merge of PR #13 (`gh pr merge --rebase --admin`) was refused by the auto-mode classifier as "Merge Without Review"; nothing was merged. Spec 6 steps 1 to 4 stay blocked until the owner merges PR #13 and the release PR, or changes the ruleset.
- [ ] **Renovate PRs on sequeless-filter** [#8](https://github.com/sequeless/sequeless-filter/pull/8) and [#9](https://github.com/sequeless/sequeless-filter/pull/9) are open and not from this run. The owner decides on them.
- [ ] **Java 25 floor on sequeless-filter** (`filter-core`, `filter-sql-adapter`) is consumer-facing. No POM in this repo references the filter yet. Record it in an ADR together with spec 6 section 3.3.
- [ ] sequeless-ontology's `origin` has the tag `v0.1.0` only; the failed 1.0.0 release was followed by a "reset version" commit. No tag deletion is needed.
- [ ] `docs/specs/README.md` status text predates the UI track's finish (it says phases 5 to 8 are unimplemented).
- [ ] Extraction spec drafts live only on branches or local clones (`docs/extraction/spec-03-prototype-stage0.md` on `spec-03-adopt-build-parent`; spec 1's draft in a local clone). Commit them with their specs.
- [ ] Deferred human task: Maven Central namespace verification ([#53](https://github.com/sequeless/sequeless-prototype/issues/53)).
- [ ] Phase 7 maps ([#1](https://github.com/sequeless/sequeless-prototype/issues/1), [#9](https://github.com/sequeless/sequeless-prototype/issues/9), [#10](https://github.com/sequeless/sequeless-prototype/issues/10)) resume only after the extraction.
- [ ] Local Maven runs against GitHub Packages need the read token in the environment: run `source ~/.bashrc.ext` before `mvn` or `./mvnw` (owner's machine). Agents must not write tokens into files.
- [ ] Renovate PR [#55](https://github.com/sequeless/sequeless-prototype/pull/55) overlaps the `renovate.json` already on `main` (shared preset); recommend closing it. Closing it awaits the owner's confirmation.
- [ ] The prototype's `.github/workflows/ci.yml` pins `maven-ci.yml` at `workflows-v0.1.0` (line 13), and `codeql.yml` pins `maven-codeql.yml` at `workflows-v0.1.1` (line 15). Renovate moves them with the parent bump.
