# Sequeless: an explanation in Simplified Technical English

Status date: 2026-11-12. The text describes repository commit 5a6c7fa.
The remote main branch is 5 commits later (b15b73b). The status of spec 13 is the same in both.
The text uses the words of the project glossary. Section 2 gives the terms.

## 1. What Sequeless is

Sequeless is a platform that keeps business objects.
A business object is one item that a company tracks. A Project and a Task are examples.
Each object has a type.
The types do not come from Java code. They come from an Ontology.

The platform supplies the same five operations for every type:
Browse, Read, Edit, Add and Delete.
You do not write code for each type.
You can change a type while the system operates. You do not deploy again.

The project is a prototype. It is not released.
The software is in the repository `sequeless-prototype`.
It uses other repositories as libraries.

## 2. Terms

Use each term with one meaning only.
When this text uses a term as a name, the first letter is a capital letter.

- **Ontology**: the source file that defines the types of one tenant, their rules and their automation. It uses the languages OWL and SHACL.
- **OWL**: a standard language. It describes types, their properties and how they link.
- **SHACL**: a standard language. It describes the rules that verify data.
- **Turtle**: a text format. The Ontology is in this format.
- **Ontology version**: one numbered activation of an Ontology. To activate is to make a version the version in use.
- **Schema**: the object types that the system compiles from the active Ontology.
- **Behaviour**: the state machines and derivation rules that the system compiles from the active Ontology.
- **Automation record**: the actions that run when a transition occurs. The system compiles it from the active Ontology.
- **State machine**: a set of states and the transitions between them.
- **State**: the current position of an object in its state machine.
- **Transition**: a change of an object from one state to another state.
- **Guard**: an expression on a transition. The transition occurs only if the guard is true.
- **Trigger**: a rule in the Behaviour. It starts a transition when an event occurs.
- **Derived value**: a value that the system calculates when it is needed. The system does not enter it by hand.
- **Tenant**: one separate group of data. Each port call names a tenant.
- **Store**: the database that contains the objects and the Ontology versions. Only a Store Command changes an object in it.
- **Store Command**: a request to add, edit or delete an object, or to cause a transition. The code names it `Mutation`.
- **Change set**: one group of Store Commands and outbox rows. The Store saves the group as one unit.
- **Object version**: a number that increases at each change of an object.
- **Browse**: a request for the objects of one type that match a filter. The answer has facets, a sort order and a page.
- **Read**: a request for one object by its identity. In this text, "read" in lower case is the plain verb.
- **Facet**: the count of each value of one field, for the objects that match a Browse.
- **Object view**: the answer for one object. It has the identity, type, tenant, values, version, state, audit data and a deleted mark.
- **Query side**: all code that answers Browse and Read. It never changes a Store.
- **Command side**: all code that accepts Store Commands and changes a Store.
- **Port**: a Java interface. The core calls a port.
- **Adapter**: one class that implements a port with one technology.
- **Slot**: the name of one port choice. The slot "persistence" is an example.
- **Bean**: an object that the Spring framework creates and manages.
- **Descriptor**: a small record that an adapter publishes. It contains the adapter name, its port and its SPI version range.
- **SPI**: the module `sequeless-spi`. It holds the ports.
- **Outbox**: a table of rows. Each row describes work for later. The system writes the row in the same database transaction as the object change.
- **Outbox relay**: the class `OutboxRelay`. It sends outbox rows to the automation adapter.
- **Claim**: to take one outbox row so that no other worker takes it.
- **Worker**: a process that does work from a queue.
- **Workflow**: a long task that the Temporal engine runs. The workflow continues after a restart.
- **ETag**: a version mark in an HTTP answer. A client sends it back in the `If-Match` header.
- **Spec**: a numbered plan document for one extraction step.
- **Tracker**: the file `docs/extraction/remaining-work.md`. It lists the status of all specs.
- **BOM** (bill of materials): a file that lists the approved version of each library.

## 3. How the code is divided

Sequeless uses the hexagonal design (ports and adapters).
The core holds the business logic.
The core connects to other systems only through ports.
Each port has one active adapter.

The prototype has these modules:

| Module | Content |
|---|---|
| `sequeless-spi` | The ports and the value records |
| `sequeless-core` | The use cases, the validation, the state machine, the planner for derived values |
| `sequeless-spi-testkit` | One abstract test class for each port |
| `sequeless-app` | The Spring Boot application, the REST API, the connection setup, the outbox relay |
| `sequeless-adapter-persistence-postgres` | Store, Query side and Outbox in PostgreSQL |
| `sequeless-adapter-ontology-jena` | The Spring setup for the Jena Ontology adapter, and the SHACL adapter |
| `sequeless-adapter-automation-temporal` | Automation with Temporal |
| `sequeless-adapter-automation-inprocess` | Automation in the same process |
| `sequeless-adapter-authz-permitall` | An authorization adapter that allows all requests |
| `sequeless-ui` | The Angular user interface |

The dependency direction is fixed.
The app depends on the core. The core depends on the SPI.
Adapters depend on the SPI. No adapter depends on the core.

The build uses Maven Enforcer and ArchUnit tests to make sure of these rules:
- The SPI and the core do not use Spring.
- The core does not use Jena, JDBC or Temporal.
- No adapter depends on the core.

A test class in `sequeless-spi-testkit` tests each port.
An adapter is correct when its tests extend that class and pass.

## 4. How the app selects adapters

A property selects each adapter. Examples:
- `sequeless.authz.adapter=permit-all`
- `sequeless.ontology.adapter=jena`
- `sequeless.persistence.adapter=postgres`
- `sequeless.validation.adapter=shacl`
- `sequeless.expression.adapter=jexl`

The file `sequeless-app/src/main/resources/application.yaml` sets these values.
It does not set `sequeless.automation.adapter`.

**WARNING: The default settings have no automation adapter. Set `sequeless.automation.adapter`, or the outbox relay does nothing.**

At startup, the class `PortRegistry` examines four slots:
authorization, ontology, persistence and validation.
Startup fails in these cases:
- A slot has no value.
- A slot names an unknown adapter.
- An adapter is for a different SPI version.
- A slot matches no bean, or more than one bean.

`PortRegistry` does not examine the automation slot, the expression slot, the query slot or the outbox slot.
The two automation adapters have no descriptor.
A wrong value in `sequeless.automation.adapter` causes no error. The outbox relay then does nothing.

The REST API always uses the default tenant and the anonymous caller.
A REST client cannot select a tenant.

## 5. The libraries that Sequeless uses

The prototype uses these released libraries. The BOM sets their versions.
The root POM uses `sequeless-bom` 0.1.3.

| Library | Content |
|---|---|
| `kernel-identity`, `kernel-values` | Shared types: `Scope`, `TenantId`, `Principal`, `Value`, `ObjectId` |
| `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract` | The ontology ports and records |
| `ontology-jena-adapter` | The Jena implementation of the ontology ports |
| `expression-api` and related modules | The expression port and the JEXL adapter |
| `filter-core` | The filter tree types |

### 5.1 sequeless-kernel

The kernel has two modules. Each module needs only the Java platform.
`kernel-identity` holds `Scope`, `TenantId` and `Principal`.
A `Scope` holds a tenant and a caller.
`kernel-values` holds `ObjectId` and the sealed interface `Value`.
A sealed interface lists all classes that can implement it.
`Value` has eight variants: text, integer, decimal, boolean, date-time, date, reference and list.
A `BoundaryRulesTest` in each module makes sure that the module obeys the dependency rules.
The released version of both modules is 0.1.0.

### 5.2 sequeless-expression

An expression is a short formula. The Ontology can contain expressions. A guard is an example.
The library has four modules:
`expression-api` (the port), `expression-testkit`, `expression-jexl-adapter` and `expression-spring-boot-autoconfig`.
JEXL is an expression language. The adapter uses it.
An expression can use `self.<name>`, `self.state` and `principal`.
The port has two methods. `evaluate` returns one `Value`. `renderTemplate` returns text.
The adapter limits the Java classes that an expression can reach.
The released version is 0.1.1.
The core uses the port for guards and for the values and templates of actions.

### 5.3 sequeless-filter

The filter library defines a text query language.
It parses text into a tree of `FilterNode` objects.
The four kinds of node are `FieldFilter`, `AnyFilter`, `AndFilter` and `OrFilter`.
The library can also write a tree as text, validate a tree and suggest completions.
Other modules in the library change a tree into SQL. The prototype does not use them.
The prototype uses only the tree types.
The class `FilterNodeToCriterion` changes an AND-only tree into a list of criteria.
It rejects `OrFilter` and `AnyFilter`.
The GitHub main branch of `sequeless-filter` shows `filter-core` 0.1.4 and `filter-sql-adapter` 0.1.5.
BOM 0.1.3 sets `filter-core` to 0.1.3.

## 6. The Ontology

The Ontology is a Turtle document.
It uses OWL for the types and SHACL for the rules.
The `sq:` vocabulary adds terms that OWL does not have. Examples are state machines and actions.
The system compiles the Ontology into three records: Schema, Behaviour and Automation record.
All three records of one activation have the same Ontology version.

The Jena adapter (`JenaOntologyPort`) compiles the Ontology in these steps:
1. It parses the text into a Jena model. In the same step, it finds each imported document. An OWL import adds another document to the Ontology. The adapter accepts only the bundled `sq:` vocabulary as an import. Any other import causes the adapter to reject the Ontology.
2. It uses a reasoner on the model. A reasoner is a program. It finds the facts that the document states indirectly.
3. It rejects the reserved `sq:` terms.
4. It maps the model to types, state machines, derivation rules and actions.
5. It rejects unknown derivation plugins.
6. It makes the result the active Ontology. It sets the next Ontology version number on the result.

The reasoner has three settings: `owl`, `rdfs` and `none`.
Only `owl` finds a logical conflict in the Ontology.
The other steps operate with all three settings.
If a reload gives an Ontology with an error, the adapter throws an exception.
The previous version stays active.

The app starts with the file `ontology/reference.ttl`.
If a Store exists, the adapter reads the active Ontology from the Store.
If no Ontology is active, the adapter compiles the source file and then writes it to the Store. If the source file has an error, the app does not start.
The REST call `GET /api/ontology` returns Turtle.
The call `POST /api/ontology` sends new Turtle to the app.
If the Ontology has an error, the app returns HTTP status 422.

The Ontology code is in the repository `sequeless-ontology`.
It has six released modules, all at version 0.1.0.
The prototype does not keep its own copy.
The core never uses a Jena type. It uses only the records.

## 7. The main flows

All REST paths start with `/api`.

### 7.1 Add an object (a Store Command)

1. The client sends `POST /api/objects/{type}`.
2. The core reads the Schema and the Behaviour. Both must have the same Ontology version. If the versions are different, the core tries again one time and then fails.
3. The core verifies that the caller can do ADD.
4. The core changes the JSON values to typed values.
5. The core verifies the structure: required properties, cardinality and reference targets.
6. The core runs the SHACL validation. This step runs only after the structure validation passes.
7. The core builds one change set. It has one create and one `ObjectCreated` outbox row.
8. The Store saves both in one database transaction. If one fails, the Store saves nothing.

**Edit.** The caller names the object version that it expects. The version is in the body or in the `If-Match` header.
If the stored object version is different, the Store throws `StaleObjectException`.
If an Edit has no object version, the app returns HTTP status 428.

**Delete.** A Delete is a soft delete. The object row stays and has a deleted mark.
**WARNING: A Delete without an `If-Match` header deletes the current version. No version test occurs.**

**Transition.** The core evaluates the guards with the expression port.
One change set then holds the new state and a `TransitionFired` row.
It also holds one `ActionRequest` row for each action.
It holds `TimerScheduled` and `TimerCancelled` rows when the transition needs them.
If a transition request has no object version, the app returns HTTP status 428.

### 7.2 Browse and Read (the Query side)

A Browse request has filters, a text search, a sort order, facets and a page.
The core verifies all of them and collects all errors.
It replaces an abstract type with its concrete types. An abstract type has no objects of its own. A concrete type has objects.
The Query side returns the items, the total and the facet buckets.
The core adds the derived values for the page.
A facet count does not use the filter of that facet.
A Read request returns one Object view.
Browse and Read never change a Store.

### 7.3 Automation

Each Store Command writes outbox rows in the same transaction.
The outbox relay gets these rows every second.
It claims the rows one at a time until the queue is empty or one row fails.
For an object change, it first recalculates the stored derived values.
It then sends the row to the automation adapter.

| Row kind | What the adapter does |
|---|---|
| `ActionRequest` | Runs the action: set a property, create an object, write a log, or call a webhook |
| `ObjectCreated`, `ObjectUpdated`, `ObjectDeleted` | Examines the triggers of the Behaviour |
| `SignalReceived` | Examines the signal triggers |
| `TimerScheduled`, `TimerCancelled` | Starts or cancels a timer |
| `TransitionFired` | Does nothing |

A trigger never changes an object itself.
It asks `TransitionService.fireAutomated` to cause a transition.
That method does nothing if the transition is no longer valid.

**Temporal adapter.** Temporal is a workflow engine. Its workflows continue after a restart. It tries failed steps again.
The adapter uses the workflow identity to prevent a second run of the same row.
Temporal runs each activity a maximum of 5 times. An activity is one step of a workflow. The first wait is 1 second. Each wait after it is two times longer.
The settings can change these values.
If Temporal is not reachable at startup, no worker operates until the next restart.
Until spec 13 part P5b is complete, the system recalculates derived values two times for an object change.
The relay does it one time. The workflow does it a second time.

**In-process adapter.** This adapter is for tests and local work. It does not keep its data after a restart.
It keeps timers and handled identities in memory.
The adapter sends a webhook a maximum of 3 times. It waits 200 milliseconds between attempts. A webhook is an HTTP call to another system.

## 8. The adapters

**PostgreSQL.** It stores each object as one row in the table `sq_object`.
The properties are in a JSONB column. JSONB is the binary JSON type of PostgreSQL.
Each row has a tenant, a version and a deleted time.
Every query uses the tenant.
The adapter uses optimistic locking: a write with a wrong version fails.
It uses `SELECT ... FOR UPDATE SKIP LOCKED` to claim outbox rows. Two workers cannot claim the same row.
Properties that have `sq:indexed` get an expression index.
The adapter adds the properties that have `sq:searchable` to a full-text search column.
Flyway is a tool that changes the database structure. It runs two sets of changes. One set is for the Command side. One set is for the Query side.

**Jena and SHACL.** The SHACL adapter verifies an object against the shapes in the Ontology.
Each violation has a property name and a message.

**Permit-all.** This adapter allows every request.
**WARNING: Do not use the permit-all adapter outside local work. It allows all requests from all callers.**

**Docker Compose.** The file `docker-compose.yml` starts PostgreSQL 18 and a Temporal development server.
Its header text is old. The header says that the app does not use these services. This is not correct.

## 9. The user interface

The UI is an Angular 22 application. Its libraries are `core-api`, `rdf`, `ui-kit`, `objects`, `ontology`, `automation` and `testing`.
It has three routes: `/objects`, `/ontology` and `/automation`.
The UI build writes the UI into `sequeless-app/src/main/resources/static`. The Maven build does not build the UI. When the UI build is there, the app serves the UI at `/`.
During development, `npm start` runs a proxy to `localhost:8080`.
The UI gets the type metadata. It shows columns, facets and filters from that metadata. No code exists for each type.
The UI keeps the last ETag of each object. It sends `If-Match` when it has one.
The Ontology editor works on Turtle in the browser. The backend validates the Ontology. The browser result is not final.

## 10. How to run the app

1. Start PostgreSQL and Temporal with `docker compose up`.
2. Select an automation adapter. Set `sequeless.automation.adapter` to `temporal` or `inprocess`.
3. Add a GitHub token with the `read:packages` scope to `~/.m2/settings.xml`, as a server with the id `github`. Maven needs this token to get the Sequeless libraries.
4. Build the UI with `npm run build:ui`. Then install the modules with `mvn -B -DskipTests install`.
5. Start the app with `mvn -pl sequeless-app spring-boot:run`.
6. Open `http://localhost:8080/` to see the UI. If you do not do the UI build, the app has only the REST API.

## 11. History and status

There are three separate plans. Do not mix them.

**Backend phases 0 to 8.** Phases 0 to 6 are complete. They are:
0. A minimum app that runs from end to end.
1. The core of the meta-model. The meta-model is the model that describes the types.
2. Objects in PostgreSQL.
3. Query and facets.
4. Derived values.
5. State machines with actions.
6. Automation that events start.
Phases 7 and 8 are not complete. Work on them did not start.
Phase 7 changes the stored data when the meta-model changes. Phase 8 shows with SPI 1.0 that an adapter can be replaced.

**UI phases 1 to 9.** All nine are complete. Phase 9 became complete on 2026-10-25.

**CQRS extraction, specs 1 to 19.** CQRS means to separate the Command side from the Query side.
The work divides the prototype into several repositories.
- Specs 1 to 12 are complete. They made the build parent, the template, the kernel, the filter library, the expression library and the ontology library. They also changed the prototype to use these libraries.
- Spec 13 is partly complete. Spec 13 has parts. Each part has a name, for example P2. An earlier verification step (commit 29c2492) satisfied part P0. Parts P1, P2a, P5a, P6a, P6b and P7 are merged. "Merged" means that the code is in the main branch.
- Four parts wait: P2, P3, P4 and P5b. They wait for the answers to two questions. Only the project owner can answer them.
  - O-3: does a database that is not a development database contain unprocessed outbox rows of the four kinds that spec 13 removes?
  - O-4: does Temporal now run workflows of the four Sequeless workflow types?
- Work on specs 14 to 19 did not start.

When the tracker and a GitHub issue do not agree, the issue is correct.

## 12. Known problems

- **Lost work in the in-process adapter.** The method `dispatch` records an identity before it runs the work. If the work fails, the database cancels the transaction. The outbox row is then not claimed. At the next attempt, the adapter finds the identity and stops. The PostgreSQL outbox adapter then marks the row done. The work never runs. This affects all row kinds except `TransitionFired`: actions, trigger examinations, signals and timers. No test covers this case.
- **A failing row stops the outbox relay.** The outbox relay cannot put a failed row aside, and it cannot ignore a failed row.
- **The default settings have no automation adapter.**
- **Some documents are old.** The README says "phase 0". Decision record DR-10 says "no UI". The folder `docs/adr` does not exist, but `CLAUDE.md` points to it. The header text of the compose file is old.
- **Local copies differ from GitHub.** Some local checkouts are on old branches. Their version numbers are different from the version numbers on the GitHub main branches.

## 13. Where to read next

- `GLOSSARY.md`: the terms.
- `docs/architecture/02-decision-records.md`: the decisions.
- `docs/architecture/03-phased-plan.md`: the backend phases.
- `docs/extraction/remaining-work.md`: the tracker.
- `docs/agents/design-preferences.md`: the design rules.
