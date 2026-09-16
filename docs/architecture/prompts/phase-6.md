# Follow-up prompt: Phase 6

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 6 of Sequeless: event-driven automation and materialised derivations.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The core depends only on
sequeless-spi; it consumes a MetaModelSnapshot via OntologyPort (Jena adapter), stores instances
via ObjectStorePort (PostgreSQL, one JSONB table, atomic commit with outbox rows), queries via
QueryPort, validates via ValidationPort, evaluates guards via ExpressionPort (JEXL), and executes
transition actions via AutomationPort (Temporal default, in-process for tests) fed by an outbox
relay. State machines and derivation rules are data in the ontology. Reference domain: Projects,
Tasks, People; Project lifecycle Draft, Active, OnHold, Closed; Project has derived openTaskCount
and totalEstimatedHours computed on read. Read docs/architecture and existing modules first.

Earlier phases delivered everything above, including user-triggered transitions with durable
actions, ObjectCreated/Updated/Deleted/TransitionFired outbox events, and on-read rollups.

OBJECTIVE
Transitions can fire from data changes, timers and external signals, and derived properties can be
materialised and kept consistent from events, so they become filterable and sortable.

CONSTRAINTS
- Vocabulary: sq:trigger values sq:OnChange (with sq:guard evaluated on every change of the
  object or of related objects declared by sq:watch), sq:Timer (sq:after ISO-8601 duration since
  entering the from-state), sq:ExternalSignal (sq:signalName). sq:materialised true on a
  derivation rule.
- On-change evaluation runs in the automation adapter, not in the request thread: the relay
  dispatches domain events; a workflow (or the in-process equivalent) evaluates candidate
  transitions by calling a core use case, which performs the transition through the normal path
  (guard, commit, actions).
- Timers: on entering a state that has a timer transition, the core adds a TimerScheduled outbox
  entry; the Temporal adapter runs a per-object-and-state workflow that sleeps and then requests
  the transition; leaving the state cancels the timer (a TimerCancelled entry). The in-process
  adapter uses a scheduler with the same semantics for tests, with a clock override.
- External signals: POST /objects/{type}/{id}/signals/{name} with a body; core writes a
  SignalReceived entry; the adapter routes it to the transition.
- Materialised derivations: on relevant source events the automation adapter calls a core
  Recompute use case that stores the value in props via a normal commit (optimistic locking with
  retry). Materialised values are filterable and sortable through QueryPort like any property
  and are still readOnly for clients. Whether a rule is materialised is visible in the type
  descriptor.
- No Drools. No new infrastructure.

DELIVERABLES
- Vocabulary and reference ontology updates: Project auto-closes when openTaskCount reaches zero
  from Active; Project in OnHold for a configurable duration moves to Closed; an external signal
  "reopen" moves Closed to Active; openTaskCount becomes materialised.
- SPI: new outbox entry kinds, Recompute and EvaluateTriggers use cases in core, extended
  AutomationContract (on-change, timer with fake clock, signal).
- Temporal and in-process adapters extended and passing the contract.
- REST signal endpoint; OpenAPI updated.
- Load-style test: 200 Task updates against one Project produce a consistent materialised count
  with no lost update.
- docs/architecture/automation.md updated with trigger semantics and event flow diagram.

ACCEPTANCE CRITERIA
- Closing the last open Task of an Active Project results in the Project being Closed within the
  test's wait window, via the normal transition path (a TransitionFired event exists).
- With the fake clock, a Project entering OnHold transitions to Closed after the configured
  duration, and does not if it left OnHold first.
- POST the reopen signal on a Closed Project moves it to Active.
- The materialised openTaskCount is filterable (filter[openTaskCount][eq]=0) and matches the
  on-read computation under the concurrent update test.
- Both automation adapters pass the extended contract and the end-to-end suite.
- mvn -B verify passes.

OUT OF SCOPE
Ontology change handling with existing data, external event publishing, authorisation beyond
permit-all, new adapters.

PROCESS
Plan before coding: propose the event-to-trigger routing design, the timer workflow design
including cancellation, and the recompute retry strategy, and confirm with me before implementing.
Ask via AskUserQuestion when ambiguous.
```
