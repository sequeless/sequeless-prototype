# Extraction plan: remaining work

Roll-up of what is left in the CQRS extraction. **The GitHub issues are the source of truth** (map [#11](https://github.com/sequeless/sequeless-prototype/issues/11), plan [#23](https://github.com/sequeless/sequeless-prototype/issues/23), specs labelled `extraction-spec`); this file summarises them so an agent can see the whole route at a glance.

Last updated: 2026-10-31 (spec 2 done; #62 fixes merged; `workflows-v0.1.2` released; parent 0.1.3 waits for its release PR and a publish decision).

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

## In flight
- [ ] Renovate configuration PR [#55](https://github.com/sequeless/sequeless-prototype/pull/55).

## Next up (unblocked now)
- [ ] **First priority, in progress:** Spec 1 follow-up, harden sequeless-build for consumers ([#62](https://github.com/sequeless/sequeless-prototype/issues/62)). Six gaps found while closing spec 2 (ArchUnit management, undocumented workflow inputs, placeholder refs, parent version pinning, Spotless/local setup, Sonar policy). Done: fixes merged in [sequeless-build #21](https://github.com/sequeless/sequeless-build/pull/21) and [sequeless-template #3](https://github.com/sequeless/sequeless-template/pull/3); `workflows-v0.1.2` released ([#22](https://github.com/sequeless/sequeless-build/pull/22)); release findings in `CLAUDE.md` ([#27](https://github.com/sequeless/sequeless-build/pull/27)). Left, in order: publish parent 0.1.3 (release PR [#26](https://github.com/sequeless/sequeless-build/pull/26), once its CHANGELOG is fixed and the owner confirms); then the template BUMP (parent 0.1.3, `workflows-v0.1.2`, drop the explicit ArchUnit versions) and VERIFY. Do this before Spec 4, Spec 6 or any new repo.
- [ ] Spec 4: prototype stage 1 prepare ([#32](https://github.com/sequeless/sequeless-prototype/issues/32)). `Principal` drops `displayName`.
- [ ] Spec 6: sequeless-filter 0.1.1 ([#34](https://github.com/sequeless/sequeless-prototype/issues/34)). Includes moving filter from semantic-release to release-please.

## Remaining specs, in dependency order
Stage numbers are from the plan. "Waits for" lists spec numbers.

| Done | Spec | Issue | Stage | Waits for |
|---|---|---|---|---|
| [ ] | 4 prototype stage 1 prepare (`Principal` drops `displayName`) | [#32](https://github.com/sequeless/sequeless-prototype/issues/32) | 1 | none (spec 3 done): unblocked |
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
- [ ] **Release PR for the parent 0.1.3 has a bad CHANGELOG** ([sequeless-build #26](https://github.com/sequeless/sequeless-build/pull/26)): a second `0.1.3` heading, a self-referencing compare link, and older commits (#7, #10, #15). Fix: create the missing GitHub release for `sequeless-build-parent-v0.1.2`, remove the duplicate `0.1.3` section that #23 left on `main`, rerun Release. Publishing 0.1.3 waits for the owner's go-ahead.
- [ ] Template `README.md` lines 21-22 still say CI stays red until `sequeless-build` has a release. That is out of date.
- [x] The UI track (`sequeless-prototype-ui`, 257 commits) is on `origin/main`, brought in with PR #58. The plan's stage 0/4/5 notes about `sequeless-ui` now describe `main`.
- [ ] sequeless-filter and sequeless-ontology still use semantic-release and their CI and Release runs fail on `main`. Specs 6 and 11 move them to release-please.
- [ ] sequeless-ontology's `origin` has the tag `v0.1.0` only; the failed 1.0.0 release was followed by a "reset version" commit. No tag deletion is needed.
- [ ] `docs/specs/README.md` status text predates the UI track's finish (it says phases 5 to 8 are unimplemented).
- [ ] Extraction spec drafts live only on branches or local clones (`docs/extraction/spec-03-prototype-stage0.md` on `spec-03-adopt-build-parent`; spec 1's draft in a local clone). Commit them with their specs.
- [ ] Deferred human task: Maven Central namespace verification ([#53](https://github.com/sequeless/sequeless-prototype/issues/53)).
- [ ] Phase 7 maps ([#1](https://github.com/sequeless/sequeless-prototype/issues/1), [#9](https://github.com/sequeless/sequeless-prototype/issues/9), [#10](https://github.com/sequeless/sequeless-prototype/issues/10)) resume only after the extraction.
- [ ] Local Maven runs against GitHub Packages need the read token in the environment: run `source ~/.bashrc.ext` before `mvn` or `./mvnw` (owner's machine). Agents must not write tokens into files.
- [ ] Renovate PR [#55](https://github.com/sequeless/sequeless-prototype/pull/55) overlaps the `renovate.json` already on `main` (shared preset); recommend closing it.
