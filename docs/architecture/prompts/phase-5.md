# Follow-up prompt: Phase 5

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 5 of Sequeless: state machines with Temporal-backed actions.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The core depends only on
sequeless-spi; it consumes a MetaModelSnapshot via OntologyPort (Jena adapter), stores instances
via ObjectStorePort (PostgreSQL, one JSONB table, atomic commit with outbox rows in sq_outbox),
queries via QueryPort, and validates via ValidationPort. Decision DR-09: state machines are data
in the ontology interpreted in the core; guards are evaluated through an ExpressionPort with a
JEXL default adapter; transition actions are outbox rows executed durably by Temporal through an
AutomationPort, with an in-process adapter for tests. Spring Statemachine is archived and must not
be used; Drools is out of scope. Reference domain: Project lifecycle Draft, Active, OnHold, Closed.
Read docs/architecture and existing modules first. Temporal already runs in Docker Compose.

Earlier phases delivered: module structure and boundary tests; ontology port with reasoner; BREAD
with validation; query and facets; derived properties on read. sq_outbox rows are written on every
commit but nothing consumes them yet.

OBJECTIVE
A Project moves through its lifecycle via user-triggered transitions whose guards are evaluated in
the core and whose actions are executed durably and idempotently by Temporal, with no action lost
if the application dies between commit and dispatch.

CONSTRAINTS
- Vocabulary: sq:StateMachine (sq:appliesTo, sq:initialState, sq:state list), sq:State (label,
  display hints), sq:Transition (sq:name, sq:from, sq:to, sq:trigger sq:UserAction, sq:guard
  expression string, sq:action list). Action kinds: sq:SetProperty (property, value or
  expression), sq:CreateObject (type, property map with expressions), sq:Webhook (url template,
  method, body template), sq:Log. All expressions use the ExpressionPort.
- SPI: StateMachineDefinition filled in the snapshot; ExpressionPort.evaluate(expression,
  context) returning a Value, with a context of self (properties, state) and principal; the
  expression adapter must be sandboxed (no reflection, no static calls, no side effects);
  ActionRequest outbox entries; AutomationPort.dispatch(scope, entry) idempotent by entry id;
  AutomationContract.
- Core interpreter: available transitions for an object are those from its current state whose
  guard evaluates true; executing a transition validates, changes state, writes a TransitionFired
  event and one ActionRequest per action in the same ChangeSet. New objects of a type with a state
  machine start in the initial state.
- Outbox relay: a component in the app polls sq_outbox for unprocessed rows (SKIP LOCKED),
  calls AutomationPort.dispatch, and marks rows processed on acknowledgement. The Temporal adapter
  starts one workflow per outbox entry with workflow id equal to the entry id (so replays are
  idempotent), whose activities call back into core use cases for SetProperty and CreateObject
  and an HTTP client for Webhook, with a retry policy configurable by property.
- The in-process adapter executes the same actions synchronously in the relay thread and is
  selected by sequeless.automation.adapter=inprocess for tests.
- Temporal must not appear outside its adapter module. Temporal is added to the Testcontainers
  setup for adapter and end-to-end tests.

DELIVERABLES
- Vocabulary update and reference ontology update with the Project lifecycle, guards (for example
  activate requires an owner), and actions (activation creates a kickoff Task and calls a
  webhook).
- SPI additions, ExpressionContract and AutomationContract in the testkit.
- sequeless-adapter-expression-jexl passing ExpressionContract with sandbox tests.
- sequeless-adapter-automation-temporal and sequeless-adapter-automation-inprocess passing
  AutomationContract; auto-configuration and descriptors.
- Core interpreter and Transition use case; outbox relay in the app.
- REST: GET /objects/{type}/{id}/transitions (available, with reasons for unavailable ones),
  POST /objects/{type}/{id}/transitions/{name}; type descriptors include the state machine.
- Operational docs: docs/architecture/automation.md describing the outbox, relay, idempotency and
  retry behaviour, and how to run Temporal locally.

ACCEPTANCE CRITERIA
- Creating a Project puts it in Draft; POST activate without an owner returns 409 with the guard
  reason; with an owner it moves to Active and the kickoff Task exists after the workflow runs.
- With a webhook stub returning 500 three times then 200, the Temporal workflow retries and the
  action completes; the outbox row is marked processed once.
- A test kills the relay between commit and dispatch (or simulates it) and shows the action is
  executed after restart with no duplicate Task.
- The full end-to-end suite passes with sequeless.automation.adapter=inprocess and with
  temporal.
- A guard expression attempting reflection or a static method call is rejected by the sandbox.
- ArchUnit fails if io.temporal appears outside its adapter module.
- mvn -B verify passes.

OUT OF SCOPE
On-change triggers, timers, external signals, materialised derivations, Drools, external event
publishing.

PROCESS
Plan before coding: propose the sq:StateMachine vocabulary, the ActionRequest payload format, the
Temporal workflow and activity design, and the relay's locking approach, and confirm with me
before implementing. Ask via AskUserQuestion when ambiguous. Verify the current Temporal Java SDK
and Spring Boot starter versions and API before coding.
```
