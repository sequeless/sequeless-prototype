# Review 04: Ontology and the Sequeless repos

VERDICT: REVISE

Most of the facts are correct. 22 of the 25 checked claims are correct or close enough. But one claim about a sibling repo is wrong, the list of compile steps leaves out a step that rejects ontologies, and the STE has problems throughout. Fix the items below, then the draft can go to a newcomer.

## 1 Accuracy

### 1.1 Wrong or unsupported claims

| # | Draft claim | Correct fact | Evidence |
|---|---|---|---|
| W1 | §7: in `sequeless-build-expression-bom`, commit 2ab3e78 "is not on main; likely pending". | The change is already merged. sequeless-build origin/main has `1e5c73d fix(bom): list the expression modules at 0.1.1 (sequeless-expression #36)`. That commit has the same message and body, and it is dated about two minutes after 2ab3e78. 2ab3e78 is the pre-merge commit on branch `bom-list-expression`. The clone is a stale worktree, and no work is pending. The BOM 0.1.4 on main lists the expression modules at 0.1.1. | `git -C sequeless-build log origin/main` shows 1e5c73d. `git show origin/main:sequeless-bom/pom.xml` lines 64-80. |
| W2 | §3: the JenaOntologyPort pipeline has 6 steps, and the list ends at "Map". | The draft leaves out one check. After SnapshotMapper, `buildStateFrom` calls `unknownPluginIssues`. This check makes sure that each `sq:Plugin` rule names a registered `DerivationPlugin`. If a name is unknown, the check adds an ERROR, and the ontology becomes "not consistent". So this step can reject an ontology, and the list must include it. | `JenaOntologyPort.java` lines 466-503 and the class javadoc on the merged report. |
| W3 | §3 steps 2-3 and §5 "Limit: unresolvable owl:imports does not throw; detected after build". | Two corrections. (a) ImportResolver is not a separate step. `OntModelBuilder.build` passes `ImportResolver.withBundledMeta()` while it parses. (b) `buildStateFrom` finds an unresolved import *inside* the build. The import does not throw, but `buildStateFrom` adds an ERROR issue and sets consistent=false. Then reload and import reject the ontology the same way they reject an inconsistent one. "Detected after build" tells the reader that nothing stops the ontology, and that is not true. | `OntModelBuilder.java` line 64; `JenaOntologyPort.java` lines 474-486. |
| W4 | §3 step 6: "Stamp all three records with same Ontology version" is part of the build. | The version is stamped only when a build is **adopted**, and that happens after `buildState`. A failed reload or import gets no version. `buildStateFrom` uses a temporary version, `FIRST_VERSION`. | Class javadoc, section "Versions"; the comment at line 510. |
| W5 | Uncertain: "Local prototype main 5a6c7fa behind origin/main 1d35e13". | The local `origin/main` ref is `4ae250c` (#131, 2026-11-12). 1d35e13 is an ancestor of 4ae250c. The 1d35e13 value looks like it came from memory or the handoff, not from the repo. This is a small error, but the draft must state a ref only after it checks the repo. | `git rev-parse --short refs/remotes/origin/main` gives 4ae250c. |
| W6 | Uncertain: "sequeless-ontology README stale ('Scaffolded')". | Partly true. The README at origin/main is current for the dependency snippet, the build parent 0.1.3 and the facet modules. Only the `ontology-core` and `ontology-jena-adapter` rows still say "Scaffolded; no domain types yet". The README also lists only 4 of the 8 modules, so the module table is incomplete. Write it that way. | `git show origin/main:README.md` lines 24-31. |

### 1.2 Verified claims (spot checks)

1. ontology origin/main is c9726a7 "chore: release main". The local checkout is on `fix/version`. Correct.
2. The root pom has 8 modules. The release-please manifest lists 6 of them at 0.1.0. The README says facet-core and facet-core-testfixtures are frozen and not published. Correct.
3. ontology-core depends on kernel-identity 0.1.0 and kernel-values 0.1.0. Correct.
4. schema-contract depends only on the kernel. Correct.
5. behaviour-contract depends on schema-contract and filter-core 0.1.3. Correct, but the draft leaves out the kernel dependencies. That is acceptable.
6. automation-contract depends on behaviour-contract and schema-contract. Correct.
7. The jena-adapter uses jena-ontapi and jena-shacl, and an enforcer rule (`ban-spring`) bans Spring. Correct.
8. The public classes are JenaOntologyPort, ShaclValidationPort, ReasonerSetting and JenaOntologySettings, plus the `internal` package. JenaOntologyPort implements SchemaSource, BehaviourSource, AutomationSource and OntologyAdministration. Correct.
9. One volatile field holds the state, so a reader sees all-old or all-new. The constructor always adopts its build. A failed reload or import throws and keeps the previous state. Correct (class javadoc).
10. With a document store, the version comes from the store. Without a store, a counter starts at 1. Correct.
11. ReasonerSetting has OWL, RDFS and NONE, and only `owl` detects inconsistency. Correct (ConsistencyChecker javadoc, application.yaml comment).
12. application.yaml has adapter jena, reasoner owl and source classpath:ontology/reference.ttl. Correct.
13. The sealed `DerivationRule` permits RollupRule and PluginRule. The sealed `Action` permits SetPropertyAction, CreateObjectAction, WebhookAction and LogAction. Correct.
14. Automation is `Map<TransitionRef, List<Action>>`, and the triggers stay on Behaviour. Behaviour has stateMachines and derivations. Correct.
15. In reference.ttl, ex:Task is a subclass of ex:WorkItem, with owl:minCardinality 1 on ex:title. Correct. Note that this rule is an OWL cardinality rule, not a SHACL shape. The draft puts it next to the SHACL text, and a newcomer can mix up the two.
16. The sq: namespace is `https://sequeless.dev/ns/meta#`. Correct.
17. GLOSSARY defines "Ontology" as the OWL and SHACL source, and "Ontology version" as one numbered activation. Correct.
18. sequeless-spi depends on ontology-core and schema-contract. sequeless-core depends on ontology-core and the three contracts. Correct.
19. JenaOntologyAutoConfiguration has `@ConditionalOnProperty sequeless.ontology.adapter=jena`. ValidationSchemaAdapter is a copy, because the released `SchemaValidationSchema` is package-private. Plugin names come from ServiceLoader and are read again on each build. Correct.
20. remaining-work.md line 153 is Spec 12 (#112, 3328993). Correct.
21. The sequeless-bom on origin/main is 0.1.4 and lists the six ontology modules at 0.1.0. The prototype pins 0.1.3. sequeless-build-parent is 0.1.3. The README says "BOM (for hosts only)". Correct.
22. sequeless-template has `make init name=<repo>`, BoundaryRulesTest in template-core and an empty GLOSSARY. Correct.
23. sequeless-platform is an empty directory. Correct (`ls -la` shows only `.` and `..`).
24. The old monorepo: commit 0b07fab removed sequeless-filter. The README describes a "last-writer-wins mirror" with sync-client and connector-jpa. The repo has **no git remote**, its last commit is 2026-07-25, it is on branch `feature/extract-sequeless-filter-module`, and no prototype doc refers to it. The verdict "obsolete" is supported. Add this evidence to the draft, because the draft now says "Old monorepo usage unchecked".
25. ADR 0004 and ADR 0006 have the status "proposed". Correct.

## 2 Missing important topics

- **Other sibling repos.** The list leaves out `sequeless-kernel` (kernel-identity and kernel-values, which every ontology module uses), `sequeless-filter` (it supplies filter-core, and behaviour-contract depends on it) and `sequeless-expression`. A newcomer sees these names in the poms and does not know what they are. Add one line for each, or a link to the draft that covers them. Also add one line for `sequeless-prototype-ui`, `sequeless-workspace` and `sequeless.github.io`, or say that they are out of scope.
- **Name collision.** The old monorepo has a module `modules/sequeless-platform` (a library bundle). The `sequeless-platform` directory is empty and is not related to that module. Tell the reader this, so that the reader does not mix up the two.
- **The plugin-name check** (see W2), and the fact that an unknown plugin name blocks activation.
- **The SHACL path in the prototype.** `ShaclValidationAutoConfiguration` is active when `sequeless.validation.adapter=shacl`. The prototype `ShaclValidationPort` maps types and then calls the released `ShaclValidationPort`, and the released port reads the shapes from the live model of the same JenaOntologyPort. The draft leaves out this whole command-side use.
- **The three factories.** `fromDocument`, `fromSource` and `fromStore` decide what `reload` reads again. With a store, `reload` reads the active stored document. Without a store, `reload` reads the original source and discards an earlier import. This behaviour can surprise a reader.
- **Definition of "activation".** The draft uses "activation" in §4 and §5 but never defines it. Give it one sentence: an activation is one adopted build that has an Ontology version.

## 3 STE (ASD-STE100) problems

### 3.1 Systematic problems

1. **Many topics in one line.** Most bullets join 3 to 6 facts with semicolons. Two examples are §5 (one paragraph, about 12 topics) and the §2 "Reasoner" bullet. STE rule: one topic in each sentence. Split the §5 paragraph into a list.
2. **Technical names not defined.** The draft does not define these names at first use: Turtle, reasoner, port, adapter, activation, BOM, javadoc, volatile, Renovate, release-please, owl:imports, DR-03 and "test kit". STE requires you to define a technical name one time and then use it the same way each time.
3. **One word with many meanings.** The draft calls the same thing a "record", a "half" and an "output". It uses "build" as a noun (the result), as a verb (compile), and in "sequeless-build" (a repo). It uses "version" for a Maven version and for an Ontology version. It uses "contract" for a module, for javadoc rules and for a test kit. Select one term for each meaning. For example: "the three outputs (Schema, Behaviour, Automation)"; "build result" for the noun; "Ontology version" vs "artifact version".
4. **Words that STE does not approve, and informal words.** The draft uses: derives, implied, adopts, stamp, glue, crosses, frozen, stale, obsolete, likely, pending, keyed by, funnels, half. Use approved words or define the term, for example "makes", "uses", "is not changed", "is old", "is not used now", "is not merged".
5. **Passive voice and hidden agents.** Examples: "stored as Turtle", "detected after build", "Build stays private until adopted", "Inconsistent reload/import throws". STE wants active voice, so name the agent: "JenaOntologyPort finds...".
6. **Missing articles.** Examples: "Parse into Jena model", "Consistency check by reasoner", "BOM ... lists six ontology modules". STE keeps "a" and "the".
7. **Private abbreviations.** The path prefixes `P/` and `O/`, and "spec-11:14", mean nothing to a newcomer. Use the full repo names, or define the prefixes in a key above the text.
8. **Notes for the author in the reader text.** The "Uncertain" section is a list of notes for the author. Move it to the review, or rewrite it as "Known limits of this document" in full sentences.

### 3.2 Rewrite examples

1. Draft: "Reasoner derives implied facts."
   STE: "A reasoner is a program that reads the ontology. It adds the facts that the ontology states indirectly."

2. Draft: "Inconsistent reload/import throws; previous version stays active. Constructor exception: always adopts its build; read calls report problem later."
   STE: "If a reload or an import makes an inconsistent ontology, JenaOntologyPort throws an exception. The previous version stays active. The constructor does not throw. It always uses its build result. Later, the read methods report the problem."

3. Draft: "Build stays private until adopted; one volatile field holds state (reader sees all-old or all-new)."
   STE: "JenaOntologyPort makes each build result in private memory. It replaces the active state only when the build result is consistent. One field holds the active state. Thus a reader sees all of the old state or all of the new state, never a mixture."

4. Draft: "Limit: unresolvable owl:imports does not throw; detected after build."
   STE: "An ontology can import another document with owl:imports. The adapter can resolve only the bundled sq: vocabulary. If the ontology imports a different document, the adapter does not throw an exception. It records an error, and it does not activate the ontology."

5. Draft: "sequeless: older monorepo; filter module moved out (0b07fab); different design (sync mirror); skipped as obsolete."
   STE: "The sequeless repo is an older monorepo. It has a different design: it copies the state of other systems. Commit 0b07fab moved the filter module to its own repo. The repo has no remote, and no current document refers to it. Do not use it."
