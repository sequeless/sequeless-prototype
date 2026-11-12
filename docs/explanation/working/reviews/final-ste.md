# Final ASD-STE100 review: final-ste.md

Scope: STE writing rules only. I did not check technical accuracy.

## VERDICT: REVISE

This text is much better than the drafts. Sentence length is now good: one sentence is over the limit. The arrows, symbols, "e.g.", slashes, dropped articles, private abbreviations, hedges and the "Uncertain" notes are gone. There is now a term list (section 2).

Some faults that the earlier reviews reported are still in the text. They are not minor, because they affect the core rule of one word for one meaning:
- "check" has five meanings.
- "Read" is a defined term, and the text also uses "reads" as a plain verb.
- "state", "Store", "version", "import", "keep" and "give" each have more than one meaning.
- About 20 technical nouns have no definition. Some examples are slot, bean, descriptor, change set, claim, guard, trigger, worker and workflow.
- Some unapproved words that the earlier reviews reported are still here: adopt, derive, pins, wiring, held and authority.

All fixes are local word changes or additions to section 2. A short pass can fix them. After that pass, the text can be approved.

## 1. Sentence length

Limits: 20 words for descriptive text and 25 words for procedural text. Table cells, headings and code are not counted.

**Sentences over the limit: 1.**

Only one sentence is over the limit. The list below has the 10 longest sentences. Nine of them are within the limit. I rewrote all 10 anyway, because some also have more than one topic.

| # | Line | Words | Original | STE rewrite |
|---|---|---|---|---|
| 1 | 282 | 22 (over) | "The phases are: walking skeleton, meta-model core, objects in PostgreSQL, query and facets, derived properties, state machines with actions, and event-driven automation." | Use a list: "The built phases are:" then one item for each phase (0. a minimum app that runs from end to end; 1. meta-model core; ...). This change also removes the idiom "walking skeleton". |
| 2 | 99 | 19 | "The start fails if one slot is unset, names an unknown adapter, or matches no bean or many beans." | "The app does not start in these conditions:" then use a list: "a slot has no value", "a slot names an unknown adapter", "Spring finds no bean or more than one bean for a slot". |
| 3 | 288 | 19 | "They created the build parent, the template, the kernel, the filter library, the expression library and the ontology library." | "These specs created six repositories:" then use a list of the six names. |
| 4 | 207 | 18 | "One change set then holds the new state, a `TransitionFired` row and one `ActionRequest` row for each action." | "The core then makes one change set. The change set contains the new state and a `TransitionFired` row. It also contains one `ActionRequest` row for each action." |
| 5 | 270 | 18 | "The UI is an Angular 22 application with the libraries `core-api`, `rdf`, `ui-kit`, ..." | "The UI is an Angular 22 application. It has seven libraries:" then use a list. |
| 6 | 100 | 17 | "`PortRegistry` does not check the automation slot, the expression slot, the query slot or the outbox slot." | "`PortRegistry` does not examine four slots: automation, expression, query and outbox." |
| 7 | 224 | 17 | "It takes the rows one at a time until the queue is empty or one row fails." | "It takes one row at a time. It stops when no rows remain. It also stops when one row fails." This also removes "queue", which has no definition. |
| 8 | 258 | 17 | "Flyway runs two sets of migrations: one for the Command side and one for the Query side." | "Flyway is a tool that changes the database structure. It runs two sets of changes. One set is for the Command side. One set is for the Query side." |
| 9 | 26 | 16 | "**Ontology**: the source file that defines the types of one tenant, their rules and their automation." | "**Ontology**: the source file for one tenant. It defines the types, the rules and the automation." |
| 10 | 39 | 16 | "**Facet**: the count of each value of one field, over the objects that match a Browse." | "**Facet**: a list of the values of one field. For each value, it gives the number of objects that match the Browse." |

## 2. Unapproved words and words with more than one meaning

| Word (lines) | Problem | Fix |
|---|---|---|
| check (76, 81, 97, 193, 195-196, 206, 212, 231-232, 290) | It has five meanings: validate data (SHACL), authorize the caller (193), test a port (81), evaluate guards and triggers (206, 231), and an owner task (290). Reported in review 01 and still here. | Use "validate" for data, "authorize" for permission, "test" for the testkit, "evaluate" for guards and triggers, and "examine" for PortRegistry. Keep "SHACL check" as a defined name, or change it to "SHACL validation". For O-3 and O-4, write "two questions that only the owner can answer". |
| Read / reads (38 is the term; 192, 174, 223, 274 use the plain verb) | The defined term and the plain verb are the same word. Reported in reviews 01 and 05 and still here. | Use "Read" only for the operation. For the plain verb, use "gets" or "loads": "The core gets the Schema", "`OutboxRelay` gets these rows". |
| state (35, 40, 47, 207) | Line 35 uses "state of objects" for the stored data. Lines 40, 47 and 207 use "state" for the state-machine state. Reported in review 01. | Store definition: "the place that keeps the objects". Use "state" only for the state-machine state. |
| Store (35, 174-175) | Section 2 defines the Store as the place for objects. Lines 174-175 say that the Store also contains the Ontology. "If a Store exists" also has no clear meaning. | Change the definition to "the database that contains the objects and the Ontology versions". Or name the second store with a different word. |
| version (30, 40, 125, 201-203, 107, 149) | It has three meanings: Ontology version, object version (for locking), and library version. Reported in review 04. | Add "Object version" to section 2: "a number that increases at each change of an object". Write "object version" at lines 201-203. Use "library version" or "release" for 0.1.x. |
| import / imports (107, 161, 177) | It has three meanings: a Maven BOM import, an OWL import, and a REST upload of Turtle. | 107: "The root POM uses `sequeless-bom` 0.1.3." 161: keep "import", but define it: "An OWL import adds another document to the Ontology." 177: "sends new Turtle to the app". |
| keep / keeps (8, 35, 124, 182, 240, 246, 275) | Most uses mean "store". Line 124 means "enforce", and line 240 means "make survive". | 124: "A `BoundaryRulesTest` in each module makes sure that the module obeys the dependency rules." 240: "Workflows continue after a restart." |
| give / gives (4, 13, 99, 102, 166, 168, 178, 203, 226) | It means "provide", "cause", "return" and "send". | 13: "supplies"; 102 and 178: "causes"; 168: "makes"; 226: "sends the row to". |
| adopt (166) | Unapproved. Reported in review 04 and still here. | "6. Make the new result the active Ontology. Give it the next Ontology version number." |
| build (noun, 166) | "build" also means the Maven build (line 76) and the repository `sequeless-build`. Reported in review 04. | "the result" |
| derive / implies (162) | Unapproved. Reported in review 04 and still here. | "A reasoner is a program. It finds the facts that the document states indirectly." |
| resolve (161) | Unapproved, and it has no definition. | "Find each imported document in the same step." |
| pins (150) | Informal. Reported in review 03 and still here. | "The prototype BOM 0.1.3 sets `filter-core` to 0.1.3." |
| wiring (64, 66) | Informal. Reported in review 01 and still here. | "the Spring configuration" |
| held (290) | Passive and informal. Reported in review 05. | "Four parts wait: P2, P3, P4 and P5b." |
| claim (255, 296) | Not defined. Line 296 also uses "rolls ... back", which is a phrasal verb. Reported in review 02. | 255: "to lock one outbox row for one worker". 296: "If the action fails, the database cancels the transaction." |
| talks to the outside (54) | Metaphor. | "The core connects to other systems only through ports." |
| lives in (180) | Idiom. | "The Ontology code is in the repository `sequeless-ontology`." |
| go into (257) | Informal. | "The adapter adds `sq:searchable` properties to a full-text search column." |
| authority (276) | Metaphor. Review 05 reported "authoritative". | "The backend validates the Ontology. The browser result is not final." |
| recomputes (225) | Unapproved. | "calculates again" |
| restricts (135) | Unapproved. | "limits" |
| expands (213) | Unapproved. | "It replaces an abstract type with its concrete types." |
| enforces (76) | Unapproved. | "The build uses Maven Enforcer and ArchUnit tests to make sure of these rules:" |
| differ, disagree (192, 292, 300) | Use the approved adjective. | "are different", "do not agree" |
| does use (266) | Emphatic "do" is not STE. | "The header text is old. It does not agree with the app, which uses PostgreSQL." |
| fires / fire (33, 36, 206, 237) | It is intransitive at line 33 and transitive at line 36. It has no definition. | Add to section 2: "To fire a transition: to move an object from one state to the next state." Use only the transitive form: "when the core fires a transition". |
| tries (noun, 242) | The noun "try" is not approved. | "The adapter sends a webhook a maximum of 5 times." |
| durable (245) | Not defined. | "It does not keep data after a restart." |
| walking skeleton, adapter swap proof (282) | Jargon and idiom. | "a minimum app that runs from end to end"; "a test that replaces the adapters". |
| dead-letter (297) | Jargon. | "The relay cannot put a failed row aside or ignore it." |
| done, built, merged, closed (282-291) | Four words for "complete". Reported in review 05 and only partly fixed. | Use "complete" for phases and specs. Keep "merged" only for spec 13 parts, and define it: "the code is in the main branch". |

## 3. Undefined terms

These terms appear in the text with no definition. Add each one to section 2, or define it at first use.

- **slot** (97): the place in `PortRegistry` for one port. Reported in reviews 01 and 02.
- **bean** (99): a Spring object. Reported in review 02.
- **descriptor** (101): reported in review 02.
- **change set** (197, 207): reported in review 01 as ChangeSet. Line 36 says that the code uses `Mutation`, but the text never connects "change set" to that name.
- **outbox relay** (64, 95): used before section 7.3 explains it. Give it a definition in section 2.
- **guard** (129, 137, 206)
- **trigger** and **signal** (231-232, 236)
- **timer** (233)
- **workflow** and **worker** (240-243, 255)
- **webhook** (230, 242)
- **derived value** and **derivation** (32, 215, 225)
- **state machine** (32, 156)
- **cardinality** and **reference target** (195)
- **abstract type** and **concrete type** (213)
- **facet bucket** (214): section 2 defines Facet, but not bucket.
- **sealed interface** (122): reported in review 03.
- **expression index** and **full-text search column** (256-257)
- **Flyway** and **migration** (258)
- **ETag** and **If-Match** (275): reported in review 02.
- **kernel** (117), **build parent** and **template** (288)
- **ArchUnit**, **Maven Enforcer** and **POM** (76, 107)
- **tracker** (292): used before section 12. Write "the extraction tracker (`docs/extraction/remaining-work.md`)".
- **owner** (290): "the project owner".
- **P0 to P7**, **O-3**, **O-4** and **DR-10** (289-299): give the meaning one time: "Spec 13 has parts. Each part has a name, for example P2."
- **meta-model** (282)

## 4. Consistency and other rule faults

1. **Store versus database.** The text writes "the Store saves" (198), "the database rolls ... back" (296) and "the Store throws" (202). Use one name for the agent of a transaction.
2. **Outbox row versus object row.** Line 204 ("The row stays") and line 250 use "row" for an object row. All other lines use "row" for an outbox row. Write "the object row" at line 204.
3. **"Automation" has two meanings.** It is a compiled record (section 2) and also a runtime feature ("Automation is off", 95 and 298). Write "the automation adapter is off" for the runtime feature.
4. **Section 6 steps are in the imperative** ("Parse", "Check", "Reject"), but they describe what the adapter does. The reader does not do these steps. Section 7.1 uses the correct descriptive form ("The core reads ..."). Use the same form in section 6: "1. The adapter parses the text ...".
5. **Passive voice remains in a few places:** "The Ontology is stored in this format" (29), "It is not released" (18), "can be imported" (161), "are built" and "are not built" (282), "are held" (290). Name the agent: "The Store keeps the Ontology in this format."
6. **Present perfect tense:** "have not started" (291). STE uses simple tenses. Write "Specs 14 to 19 did not start." or "Work on specs 14 to 19 is not started."
7. **Warnings.** The permit-all line (263) and the automation-off line (95) are safety or security information. State each one as a clear warning that starts with a command: "WARNING: Do not use the permit-all adapter in production. It allows all requests from all callers." Section 11 item 1 is a defect, not an instruction, so its present form is acceptable.
8. **Noun clusters:** no clusters of more than 3 words were found, except for code names. No contractions were found.

## 5. Earlier review faults: status

| Fault (review) | Status |
|---|---|
| Arrows and symbols used as verbs (01, 02, 03) | Fixed |
| Articles dropped (01-05) | Fixed |
| Long sentences with many topics (01-05) | Fixed (one sentence is still over the limit, at line 282) |
| "e.g.", slashes, semicolons that join topics (05) | Fixed |
| Private abbreviations P/, O/, AD, PG (02, 04) | Fixed |
| Hedges and the "Uncertain" author notes (02, 04) | Fixed |
| No term list (01) | Fixed (section 2), but the list is incomplete (see section 3) |
| CQRS, BOM, SPI, OWL, SHACL, Turtle, JSONB, port, adapter, outbox, facet, reasoner undefined | Fixed |
| "check", "Read", "state", "store", "version" with more than one meaning (01, 02, 04, 05) | Not fixed |
| slot, bean, descriptor, ChangeSet, claim, sealed interface, ETag undefined (01, 02, 03) | Not fixed |
| adopt, derive, pins, wiring, stale (01, 03, 04) | "stale" is fixed. The other four are not fixed. |
| Many words for "complete" and "waiting" (05) | Partly fixed |
| Passive voice (01-05) | Mostly fixed. Six instances remain. |
