# Round 2 approval gate: final-ste.md (revised)

Check date: 2026-11-12. Read-only check against /Users/bbartolome/workspace/sequeless-prototype at local main 5a6c7fa (origin/main b15b73b for the resume plan section 12).

## ACCURACY VERDICT: REVISE

All round-1 corrections are applied, except one part of A4 (who marks the row done). The new section 10 has one error that a newcomer will hit: after a plain Maven build, the UI is not at `/`, because the UI build output is git-ignored and Maven does not build the UI. All fixes are mechanical. No round 3 is necessary after the orchestrator applies the replacements below.

### Round-1 accuracy corrections: status

| Item | Status |
|---|---|
| A1 Delete without `If-Match`; Edit and transition 428 | Applied (7.1). Correct per `ObjectsController.java` 339-342, 368-375, 466-470. |
| A2 Timer rows in a transition change set | Applied (7.1). Correct per `DefaultTransitionService.java` javadoc 306-309, 349-357. |
| A3 Retry for all Temporal activities, default values, settings | Applied (7.3). Correct: `TemporalAutomationProperties` 1 s, 2.0, 5 attempts; in-process 3 attempts, 200 ms. |
| A4 Bug scope and who marks the row done | Scope applied. NOT applied: the text says "The relay then marks the row done". `PostgresOutboxPort.claimNext` (the UPDATE of `processed_at`, lines 79-83) marks it. Fix F-A1. |
| A5 Import error and the `owl` setting | Applied (section 6 step 1 and "Only `owl` finds a logical conflict"). |
| A6 Tenant and the REST API | Applied in section 4. The term "call" in section 2 is still broad. Fix F-A5 (minor). |
| I1 P0 satisfied, not merged | Applied. |
| I2 Header commit and origin/main | Applied. |
| I3 Incompatible SPI version | Applied. |
| Omission: double recalculation with Temporal | Applied (7.3). |
| Omission: how to run | Added as section 10. One error (F-A2). |

### New or changed claims: verification

| Claim | Result | Evidence |
|---|---|---|
| 10.1 `docker compose up` starts PostgreSQL and Temporal | Correct. `postgres:18` on 5432, `temporalio/temporal:1.9.1` dev server on 7233. The Temporal adapter default target is 127.0.0.1:7233. | `docker-compose.yml`; `TemporalAutomationProperties.target` javadoc |
| 10.2 values `temporal` and `inprocess` | Correct. | `@ConditionalOnProperty(... havingValue = "temporal" / "inprocess")` |
| 10.3 Maven needs "the Packages token" | Correct but the term is undefined. It is a GitHub token with the `read:packages` scope, in a `github` server entry in `~/.m2/settings.xml`. The README also says to run `mvn -B -DskipTests install` before the first `spring-boot:run`. | README "Develop the UI"; root `pom.xml` repository id `github` |
| 10.4 and 9: UI at `http://localhost:8080/` | Incomplete, and wrong for a new clone. Port 8080 is correct (Spring default; `proxy.conf.json`). The static folder is git-ignored. README: "`mvn -B verify` alone builds an API-only jar. To serve the UI from the same jar, build the UI first: `npm run build:ui`". | `.gitignore`; README "Build"; `application.yaml` `web.spa` |
| 7.1 Delete and Transition | Correct. | see A1, A2 |
| 7.3 retry numbers | Correct. "The settings can change these values" applies to both adapters. | see A3 |
| 4 PortRegistry failure cases | Correct: property not set, unknown adapter, incompatible version, no bean, more than one bean. | `PortRegistry.java` 73-90 |
| 4 slots not examined | Correct. `PortRegistryConfiguration` lists only authz, ontology, persistence, validation. `sequeless.query.adapter` and `sequeless.outbox.adapter` exist with `matchIfMissing = true`; `sequeless.expression.adapter` is in the expression autoconfig. | `PortRegistryConfiguration.java` 35-38 |
| 11 spec 13 parts | Correct: P1, P2a, P5a, P6a, P6b, P7 merged; P0 satisfied by the earlier gate; P2, P3, P4, P5b wait on O-3 and O-4. O-3 is about non-development databases and about the four kinds that spec 13 removes. The kinds are not removed yet. O-4 is about four workflow types. Fix F-A3 and F-A4 (minor precision). | resume plan sections 9, 11, 12 (origin/main) |
| 12 lost work | Correct, except the agent that marks the row done. | `InProcessAutomationPort.dispatch` 110-122 |

## STE VERDICT: REVISE

Fixed since round 1: sentence length (no sentence is over the limit), the section 6 steps (now descriptive, "It parses ..."), adopt, pins, wiring, held, lives in, recomputes, restricts, the definitions of slot, bean, descriptor, change set, claim, worker, workflow, guard, trigger, state machine, ETag, sealed interface, JEXL and reasoner.

Remaining faults by severity:

Major (rule: one word, one meaning; approved words only):
1. "authority", "talks to the outside", "walking skeleton", "adapter swap proof", "dead-letter", "durable", "implies", "resolves", "rolls ... back", "go into", "expands", "enforces", "does use" are still in the text.
2. "check" still has the owner-task meaning (section 11).
3. "state" in the Store definition means stored data. Elsewhere it means the state-machine state. The Store definition also does not include the Ontology versions (section 6 reads the Ontology from the Store).
4. "gives" has four meanings (provide, cause, return, send).
5. "import" has three meanings (Maven, OWL, REST upload).
6. "build" (noun) in section 6 step 6 conflicts with the Maven build.
7. "Outbox relay" is used in section 4 before it has a definition.

Minor (approve after fix, or accept):
8. "read" plain verb next to the term Read. The text states the rule in section 2, so this is acceptable. Replacements are given for clarity.
9. Noun "try"/"tries"; "differ", "disagree"; "fixes" (means repair in STE); "done", "closed", "complete" for one meaning; present perfect "have not started"; "Object version" not defined.
10. Undefined terms that stay understandable from context: signal, timer, webhook, cardinality, reference target, abstract type, facet bucket, Flyway, migration, expression index, POM, Maven Enforcer, ArchUnit, meta-model, build parent, template. Four short definitions are given below (F-S30 to F-S33). The others can stay.
11. The permit-all line is security information. A WARNING form is better.

## Replacement list (apply in order; each OLD is an exact string in final-ste.md)

### Accuracy fixes

F-A1 (section 12)
OLD: The next try sees the identity and stops. The relay then marks the row done. The work never runs.
NEW: At the next attempt, the adapter finds the identity and stops. The PostgreSQL outbox adapter then marks the row done. The work never runs.

F-A2 (section 10, steps 3 and 4)
OLD: 3. Build and start the app with Maven. The Maven build needs the Packages token.
4. Open `http://localhost:8080/` to see the UI.
NEW: 3. Add a GitHub token with the `read:packages` scope to `~/.m2/settings.xml`, as a server with the id `github`. Maven needs this token to get the Sequeless libraries.
4. Build the UI with `npm run build:ui`. Then install the modules with `mvn -B -DskipTests install`.
5. Start the app with `mvn -pl sequeless-app spring-boot:run`.
6. Open `http://localhost:8080/` to see the UI. If you do not do the UI build, the app has only the REST API.

F-A2b (section 9)
OLD: The build writes the UI into `sequeless-app/src/main/resources/static`. The jar serves it at `/`.
NEW: The UI build writes the UI into `sequeless-app/src/main/resources/static`. The Maven build does not build the UI. When the UI build is there, the app serves the UI at `/`.

F-A3 (section 11)
OLD:   - O-3: does a database hold unprocessed outbox rows of the four removed kinds?
NEW:   - O-3: does a database that is not a development database contain unprocessed outbox rows of the four kinds that spec 13 removes?

F-A4 (section 11)
OLD:   - O-4: does Temporal run workflows now?
NEW:   - O-4: does Temporal now run workflows of the four Sequeless workflow types?

F-A5 (section 2, minor)
OLD: - **Tenant**: one separate group of data. Each call names a tenant.
NEW: - **Tenant**: one separate group of data. Each port call names a tenant.

F-A6 (section 6, minor precision)
OLD: If no Ontology is active, the adapter writes the source file to the Store.
NEW: If no Ontology is active, the adapter compiles the source file and then writes it to the Store. If the source file has an error, the app does not start.

### STE fixes

F-S1 (section 2)
OLD: - **Store**: the place that keeps the state of objects. Only a Store Command changes it.
NEW: - **Store**: the database that contains the objects and the Ontology versions. Only a Store Command changes an object in it.

F-S2 (section 2, add after the Store Command line)
OLD: - **Change set**: one group of Store Commands and outbox rows. The Store saves the group as one unit.
NEW: - **Change set**: one group of Store Commands and outbox rows. The Store saves the group as one unit.
- **Object version**: a number that increases at each change of an object.

F-S3 (section 2)
OLD: - **Workflow**: a long task that the Temporal engine runs and keeps after a restart.
NEW: - **Workflow**: a long task that the Temporal engine runs. The workflow continues after a restart.

F-S4 (section 2, add after the Outbox line)
OLD: - **Claim**: to take one outbox row so that no other worker takes it.
NEW: - **Outbox relay**: the class `OutboxRelay`. It sends outbox rows to the automation adapter.
- **Claim**: to take one outbox row so that no other worker takes it.

F-S5 (section 2)
OLD: - **Descriptor**: a small record that an adapter publishes. It gives the adapter name, its port and its SPI version range.
NEW: - **Descriptor**: a small record that an adapter publishes. It contains the adapter name, its port and its SPI version range.

F-S6 (section 1)
OLD: The platform gives the same five operations for every type:
NEW: The platform supplies the same five operations for every type:

F-S7 (section 3)
OLD: The core talks to the outside only through ports.
NEW: The core connects to other systems only through ports.

F-S8 (section 3)
OLD: The build enforces these rules with Maven Enforcer and ArchUnit tests:
NEW: The build uses Maven Enforcer and ArchUnit tests to make sure of these rules:

F-S9 (section 4)
OLD: A wrong value in `sequeless.automation.adapter` gives no error. The relay then does nothing.
NEW: A wrong value in `sequeless.automation.adapter` causes no error. The outbox relay then does nothing.

F-S10 (section 5)
OLD: The root POM imports `sequeless-bom` 0.1.3.
NEW: The root POM uses `sequeless-bom` 0.1.3.

F-S11 (section 5.1)
OLD: A `BoundaryRulesTest` in each module enforces the dependency rules.
NEW: A `BoundaryRulesTest` in each module makes sure that the module obeys the dependency rules.

F-S12 (section 5.3)
OLD: BOM 0.1.3 fixes `filter-core` at 0.1.3.
NEW: BOM 0.1.3 sets `filter-core` to 0.1.3.

F-S13 (section 6, step 1)
OLD: 1. It parses the text into a Jena model. In the same step it resolves imports. Only the bundled `sq:` vocabulary can be imported. Any other import causes the adapter to reject the Ontology.
NEW: 1. It parses the text into a Jena model. In the same step, it finds each imported document. An OWL import adds another document to the Ontology. The adapter accepts only the bundled `sq:` vocabulary as an import. Any other import causes the adapter to reject the Ontology.

F-S14 (section 6, step 2)
OLD: 2. It uses a reasoner on the model. A reasoner calculates the facts that the document implies.
NEW: 2. It uses a reasoner on the model. A reasoner is a program. It finds the facts that the document states indirectly.

F-S15 (section 6, step 6)
OLD: 6. It starts to use the new build and gives it the Ontology version.
NEW: 6. It makes the result the active Ontology. It sets the next Ontology version number on the result.

F-S16 (section 6)
OLD: The call `POST /api/ontology` imports Turtle.
An Ontology with an error gives HTTP status 422.
NEW: The call `POST /api/ontology` sends new Turtle to the app.
If the Ontology has an error, the app returns HTTP status 422.

F-S17 (section 7.1, step 2)
OLD: Both must have the same Ontology version. If they differ, the core tries again one time and then fails.
NEW: Both must have the same Ontology version. If the versions are different, the core tries again one time and then fails.

F-S18 (section 7.1, Edit)
OLD: **Edit.** The caller names the version that it expects.
If the stored version is different, the Store throws `StaleObjectException`.
An Edit without a version gives HTTP status 428.
NEW: **Edit.** The caller names the object version that it expects. The version is in the body or in the `If-Match` header.
If the stored object version is different, the Store throws `StaleObjectException`.
If an Edit has no object version, the app returns HTTP status 428.

F-S19 (section 7.1, Delete)
OLD: **Delete.** A Delete is a soft delete. The row stays and has a deleted mark.
NEW: **Delete.** A Delete is a soft delete. The object row stays and has a deleted mark.

F-S20 (section 7.1, Transition)
OLD: A transition without a version gives HTTP status 428.
NEW: If a transition request has no object version, the app returns HTTP status 428.

F-S21 (section 7.2)
OLD: It expands an abstract type into its concrete types.
NEW: It replaces an abstract type with its concrete types. An abstract type has no objects of its own. A concrete type has objects.

F-S22 (section 7.3)
OLD: The class `OutboxRelay` reads these rows every second.
NEW: The outbox relay gets these rows every second.

F-S23 (section 7.3)
OLD: It then gives the row to the automation adapter.
NEW: It then sends the row to the automation adapter.

F-S24 (section 7.3, Temporal)
OLD: Temporal is a workflow engine. It keeps workflows after a restart and tries failed steps again.
NEW: Temporal is a workflow engine. Its workflows continue after a restart. It tries failed steps again.

F-S25 (section 7.3, Temporal)
OLD: Each Temporal activity has 5 tries. The wait doubles after each try, from 1 second.
NEW: Temporal runs each activity a maximum of 5 times. An activity is one step of a workflow. The first wait is 1 second. Each wait after it is two times longer.

F-S26 (section 7.3, in-process)
OLD: **In-process adapter.** This adapter is for tests and local work. It is not durable.
NEW: **In-process adapter.** This adapter is for tests and local work. It does not keep its data after a restart.

F-S27 (section 7.3, in-process)
OLD: A webhook gets 3 tries, 200 milliseconds apart.
NEW: The adapter sends a webhook a maximum of 3 times. It waits 200 milliseconds between attempts. A webhook is an HTTP call to another system.

F-S28 (section 8)
OLD: Properties that have `sq:searchable` go into a full-text search column.
NEW: The adapter adds the properties that have `sq:searchable` to a full-text search column.

F-S29 (section 8)
OLD: Its header text is old. The app does use PostgreSQL.
NEW: Its header text is old. The header says that the app does not use these services. This is not correct.

F-S30 (section 8)
OLD: Flyway runs two sets of migrations: one for the Command side and one for the Query side.
NEW: Flyway is a tool that changes the database structure. It runs two sets of changes. One set is for the Command side. One set is for the Query side.

F-S31 (section 8)
OLD: **Permit-all.** This adapter allows every request. Use it only for local work.
NEW: **Permit-all.** This adapter allows every request.
**WARNING: Do not use the permit-all adapter outside local work. It allows all requests from all callers.**

F-S32 (section 9)
OLD: The UI reads the type metadata.
NEW: The UI gets the type metadata.

F-S33 (section 9)
OLD: The Ontology editor works on Turtle in the browser. The backend is the authority.
NEW: The Ontology editor works on Turtle in the browser. The backend validates the Ontology. The browser result is not final.

F-S34 (section 11, phases)
OLD: **Backend phases 0 to 8.** The system has phases 0 to 6.
They are: walking skeleton, meta-model core, objects in PostgreSQL, query and facets, derived values, state machines with actions, and event-driven automation.
Phases 7 and 8 do not exist yet.
Phase 7 is meta-model migration. Phase 8 is the adapter swap proof with SPI 1.0.
NEW: **Backend phases 0 to 8.** Phases 0 to 6 are complete. They are:
0. A minimum app that runs from end to end.
1. The core of the meta-model. The meta-model is the model that describes the types.
2. Objects in PostgreSQL.
3. Query and facets.
4. Derived values.
5. State machines with actions.
6. Automation that events start.
Phases 7 and 8 are not complete. Work on them did not start.
Phase 7 changes the stored data when the meta-model changes. Phase 8 shows with SPI 1.0 that an adapter can be replaced.

F-S35 (section 11, UI phases)
OLD: **UI phases 1 to 9.** All nine are done. Phase 9 closed on 2026-10-25.
NEW: **UI phases 1 to 9.** All nine are complete. Phase 9 became complete on 2026-10-25.

F-S36 (section 11, specs)
OLD: - Specs 1 to 12 are done.
NEW: - Specs 1 to 12 are complete.

F-S37 (section 11, spec 13)
OLD: - Spec 13 is partly done. Part P0 is satisfied by an earlier gate. Parts P1, P2a, P5a, P6a, P6b and P7 are merged.
NEW: - Spec 13 is partly complete. Spec 13 has parts. Each part has a name, for example P2. An earlier verification step (commit 29c2492) satisfied part P0. Parts P1, P2a, P5a, P6a, P6b and P7 are merged. "Merged" means that the code is in the main branch.

F-S38 (section 11)
OLD: - Four parts wait: P2, P3, P4 and P5b. They wait for two checks that only the owner can do.
NEW: - Four parts wait: P2, P3, P4 and P5b. They wait for the answers to two questions. Only the project owner can answer them.

F-S39 (section 11)
OLD: - Specs 14 to 19 have not started.
NEW: - Work on specs 14 to 19 did not start.

F-S40 (section 11)
OLD: When the tracker and a GitHub issue disagree, the issue is correct.
NEW: When the tracker and a GitHub issue do not agree, the issue is correct.

F-S41 (section 12, lost work)
OLD: If the work fails, the database rolls the claim back.
NEW: If the work fails, the database cancels the transaction. The outbox row is then not claimed.

F-S42 (section 12)
OLD: - **A failing row stops the relay.** The relay has no dead-letter path and no skip path.
NEW: - **A failing row stops the outbox relay.** The outbox relay cannot put a failed row aside, and it cannot ignore a failed row.

F-S43 (section 12)
OLD: Their version numbers differ from the GitHub main branches.
NEW: Their version numbers are different from the version numbers on the GitHub main branches.

F-S44 (section 4, warning)
OLD: **WARNING: Automation is off in the default settings. The outbox relay then does nothing.**
NEW: **WARNING: The default settings have no automation adapter. Set `sequeless.automation.adapter`, or the outbox relay does nothing.**

F-S45 (section 12)
OLD: - **Automation is off in the default settings.**
NEW: - **The default settings have no automation adapter.**

Optional (minor, accept if not applied): define signal ("an external message to an object that can start a transition"), timer ("a transition that starts after a time"), cardinality ("the number of values that a property can have"), and facet bucket ("one value and its count in a facet") in section 2.
