# Final accuracy review: final-ste.md

Reviewer check date: 2026-11-12. Read-only. Prototype local main is 5a6c7fa. origin/main of the prototype is b15b73b (fetched). Sibling repos were checked at origin/main or at release tags: sequeless-kernel da8b48f, sequeless-expression 052a4a6, sequeless-filter origin/main, sequeless-ontology c9726a7, sequeless-build tag `sequeless-bom-v0.1.3`.

## VERDICT: REVISE

The text is mostly accurate. I checked 34 claims. 25 are correct. 6 are wrong or misleading, and 3 are incomplete. Each fix is one or two sentences. One correction from review 01 was not applied (Delete and the expected version). That error can make a newcomer think that every Delete is protected. Make the fixes below, then approve.

## 1. Wrong or misleading claims, with STE corrections

| # | Section | Claim in final-ste.md | Fact | Evidence | Corrected text (STE) |
|---|---|---|---|---|---|
| A1 (major, from review 01 #18) | 7.1 | "Edit and Delete use the same path. Each write names the version that the caller expects." | Add does not give a version. For Delete, `If-Match` is optional. Without it, the app deletes the current version and does no version check. Edit and transition need a body version or `If-Match`, or the app returns 428. | `ObjectsController.java` lines 339-342, 368-375, 466-470, 513 | "An Edit must give the version that the caller expects. The version is in the body or in the `If-Match` header. If both are missing, the app returns HTTP status 428. A transition uses the same rule. For a Delete, `If-Match` is optional. If a Delete has no `If-Match`, the app deletes the current version and does not check the version." |
| A2 | 7.1 | "One change set then holds the new state, a `TransitionFired` row and one `ActionRequest` row for each action." | The change set also holds one `TimerCancelled` row for each timer of the state that the object leaves. It also holds one `TimerScheduled` row for each timer of the new state. | `DefaultTransitionService.java` lines 347-357 and 364-390 | "One change set then holds the new state, a `TransitionFired` row, one `ActionRequest` row for each action, and the `TimerScheduled` and `TimerCancelled` rows for the timers of the two states." |
| A3 | 7.3 | "A webhook gets 5 tries. The wait doubles after each try, from 1 second." | The retry policy applies to all Temporal activities: all actions and the trigger checks, not only webhooks. These numbers are defaults. The properties `sequeless.automation.temporal.retry.*` change them. Each try has a limit of 30 seconds. | `TemporalAutomationProperties.java` (Retry: 1 s, 2.0, 5 attempts; "applied uniformly to all four ActionActivities methods"); `TemporalAutomationAutoConfiguration.java` lines 133, 168-176; `ChangeEventWorkflowImpl` and `TimerWorkflowImpl` use the same options | "Each Temporal activity gets 5 tries by default. An activity is one step of a workflow, for example one action or one trigger check. The wait starts at 1 second and doubles after each try. The properties `sequeless.automation.temporal.retry.*` change these values." Add for the in-process adapter: "A webhook gets 3 tries, with 200 milliseconds between tries." (`InProcessAutomationProperties.java` lines 21, 27) |
| A4 | 11 | "The action never runs." | The bug is in the identity check that comes before the `switch`. So it affects every row kind that the adapter handles, except `TransitionFired`: actions, trigger checks for object changes, signal checks and timers. Also, the outbox port (`claimNext`) marks the row done, not the relay. | `InProcessAutomationPort.java` lines 101-122; `PostgresOutboxPort.java` line 81 | "If the work fails, the work never runs again. This applies to actions, trigger checks, signal checks and timers. The outbox port then marks the row done, and no error shows." |
| A5 | 6 | "The reasoner has three settings: `owl`, `rdfs` and `none`. Only `owl` finds an inconsistent Ontology." | This conflicts with the steps above. Steps 3 and 5, and the import check, reject an Ontology with all three settings. Only the logical contradiction check needs `owl`. Also, the text does not say what happens to an import that is not `sq:`. The adapter does not throw at parse. It records an error, and the Ontology is rejected. | `JenaOntologyPort.java` (origin/main) lines 466-503 | Step 1: "Parse the text into a Jena model. Resolve imports in the same step. Only the bundled `sq:` vocabulary can be imported. Any other import makes an error, and the adapter rejects the Ontology." After the list: "Only the setting `owl` finds a logical contradiction. The other checks run with all three settings." |
| A6 | 2 | "Tenant: one separate group of data. Each call names a tenant." | Each port call carries a `Scope` with a tenant. But the REST API always uses the default tenant and the anonymous caller. A REST client cannot select a tenant. | `ObjectsController.java` lines 84, 537; `TypesController.java` line 78 | "Tenant: one separate group of data. Each port call names a tenant. The REST API now always uses the default tenant and the anonymous caller." |

## 2. Incomplete claims

| # | Section | Gap | Corrected text (STE) |
|---|---|---|---|
| I1 (review 05 W3) | 10 | "Merged parts are P0, P1, ..." P0 has no PR. The cut-over gate at `29c2492` satisfies it (resume-plan-spec13.md line 113; remaining-work.md "Current state"). | "P0 is satisfied by an earlier gate. P1, P2a, P5a, P6a, P6b and P7 are merged." |
| I2 (review 04 W5) | Header | "Repository state: commit 5a6c7fa." origin/main is b15b73b, 5 commits ahead: #128 (the V5 migration test pin, NB-4), #129 (BrowseContract assertions), #130 (handoff docs), #131 (DR-21, the Browse id order), #132. The spec 13 status in section 10 does not change. | "Repository state: local commit 5a6c7fa. On GitHub, main is at b15b73b. It adds tests and the decision record DR-21. The status in section 10 is the same at both commits." |
| I3 (review 01 #7) | 4 | The start also fails when the version range of an adapter does not agree with the SPI version (`PortRegistry.java` line 82, `incompatibleVersion`). | "The start fails if one slot is unset, names an unknown adapter, has an adapter for a different SPI version, or matches no bean or many beans." |

Small precision items (optional): section 10, O-3 is about databases that are not development databases ("non-dev"), and O-4 asks about running workflows of four types (ActionWorkflow, TimerWorkflow, SignalEventWorkflow, ChangeEventWorkflow) (resume-plan-spec13.md section 12). Section 6: if no Ontology is active, the adapter writes the source file to the Store only when the file passes all checks. If not, the start fails (`JenaOntologyPort.fromStore`, lines 296-298).

## 3. Dangerous or important omissions

1. **Delete without a version (A1).** This is the only omission that can cause data loss. A client that sends no `If-Match` can delete an object that another user changed.
2. **The double recompute with Temporal (review 02 item 5, review 05 missing item 8).** Until spec 13 P5b, the relay recomputes derived values, and `ChangeEventWorkflowImpl` recomputes them again. The second recompute changes nothing. A newcomer who reads the Temporal code will see it. STE: "With Temporal, the derived values are recomputed two times for each object change. The second recompute changes nothing. Step P5b removes it."
3. **How to run (review 01 missing item 2, review 05 missing item 5).** Not an accuracy error, but the text has no procedure. STE: "Start PostgreSQL with `docker compose up`. Build with JDK 25 and Maven: `mvn -B verify`. Maven needs a GitHub Packages token for the Sequeless libraries. To see automation, set `sequeless.automation.adapter` to `inprocess` or `temporal`."

## 4. Earlier review corrections: applied or not

| Review | Correction | Status |
|---|---|---|
| 01 | Automation off by default; automation adapters have no descriptor | Applied (sections 4, 11) |
| 01 | `sequeless.authz.adapter` spelling; four checked slots | Applied |
| 01 | Relay drains rows one at a time per tick | Applied (7.3) |
| 01 | `/api` prefix | Applied |
| 01 | Delete version is optional; Edit without version gives 428 | 428 applied; Delete NOT applied (A1) |
| 01 | Incompatible SPI version check | Not applied (I3) |
| 02 | Expression slot not checked | Applied |
| 02 | In-process bug is confirmed, no test | Applied, but too narrow (A4) |
| 02 | `testing` UI library; ETag sends `If-Match` only when cached | Applied |
| 02 | Recompute runs twice with Temporal | Not applied (omission 2) |
| 03 | Filter versions from origin/main (0.1.4, 0.1.5); BOM 0.1.3 pins 0.1.3 | Applied |
| 03 | Prototype also uses the ontology libraries | Applied (section 5 table) |
| 03 | BoundaryRulesTest in both kernel modules | Applied |
| 04 | Plugin-name check is a reject step | Applied (step 5) |
| 04 | Imports resolved during parse; unresolved import rejects | Part applied (A5) |
| 04 | Version given only on adoption | Applied (step 6) |
| 04 | Ref from the repo, not memory | Not applied (I2) |
| 05 | Issue wins over tracker | Applied |
| 05 | Phases 0-6 built, 7-8 not built | Applied |
| 05 | P0 satisfied, not merged | Not applied (I1) |

## 5. Claims verified as correct (25)

1. application.yaml sets authz `permit-all`, expression `jexl`, ontology `jena`, persistence `postgres`, validation `shacl`, and no automation adapter; base path `/api`.
2. PortRegistry slots are authz, ontology, persistence, validation (`PortRegistryConfiguration.java` 35-38).
3. Only permit-all, postgres and ontology-jena (Jena and SHACL) have `AdapterDescriptorProvider` files; the automation modules have none.
4. Automation adapters use `@ConditionalOnProperty(sequeless.automation.adapter = inprocess | temporal)`; a wrong value makes no bean, and `OutboxRelay.pollOnce` returns at once (lines 122-124).
5. Relay: `fixedDelay` 1000 ms default, loop until empty or failure, recompute before dispatch for object-change rows.
6. Temporal worker: if Temporal is not reachable at start, the app logs a warning and no worker polls until a restart (`TemporalAutomationAutoConfiguration.java` 145-154).
7. In-process `dispatch` adds the id to `dispatched` before it runs the work (line 110).
8. Root POM imports `sequeless-bom` 0.1.3.
9. BOM 0.1.3 lists kernel 0.1.0, filter-core 0.1.3, expression modules 0.1.1, six ontology modules 0.1.0.
10. Kernel manifest 0.1.0 for both modules; no main-scope dependencies; `Scope(TenantId, Principal)`.
11. `Value` permits eight types (text, integer, decimal, bool, date-time, date, reference, list).
12. Expression manifest: four modules at 0.1.1; `ExpressionPort` has `evaluate` (returns `Value`) and `renderTemplate` (returns `String`).
13. Core uses `evaluate` for guards (`StateMachineInterpreter` 120) and for action values (`DefaultActionExecutor` 207), and `renderTemplate` for webhook URL and body and log message.
14. Filter origin/main manifest: filter-core 0.1.4, filter-sql-adapter 0.1.5; four node types in filter-core.
15. Compile order in `buildStateFrom`: reasoner, unresolved imports, reserved terms, mapping, plugin names; version given on adoption.
16. App wiring uses `fromStore` when an object store exists, else `fromSource`.
17. Ontology errors give HTTP 422; missing version gives 428 (`ApiExceptionAdvice`).
18. Postgres: `sq:indexed` makes an expression index on `sq_object`; `sq:searchable` registers in `sq_searchable_property`; Read filters `deleted_at IS NULL`.
19. Two migration sets: `postgres` and `postgres-query`.
20. docker-compose: `postgres:18` and a Temporal dev server; header text is old ("no wiring to either yet").
21. UI: Angular 22; libraries include `testing`; routes `/objects`, `/ontology`, `/automation`; output to `sequeless-app/src/main/resources/static`; `npm start` proxies to `localhost:8080`.
22. UI phase 9 done 2026-10-25 (`docs/specs/phase-9-shell-composition-and-polish.md` line 3).
23. Backend phase plan has phases 0-8 with the names given; no migration table or in-memory adapter exists.
24. Spec table: 1-12 done, 13 open, 14-19 not started; tracker line 3 says the GitHub issues are the source of truth.
25. Spec 13: P1, P2a, P5a, P6a, P6b, P7 merged; P2, P3, P4, P5b held on O-3 and O-4 (resume plan section 12). README says "phase 0"; DR-10 says "no UI"; `docs/adr` does not exist.
