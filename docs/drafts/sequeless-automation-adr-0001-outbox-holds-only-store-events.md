<!-- Draft for sequeless-automation docs/adr/. Not yet committed there. Decided in
     "Where automation and Observable go" (sequeless/sequeless-prototype#20). -->

# 0001. The Store's outbox holds only Store events; automation owns Side-effect durability

- Status: proposed
- Date: 2026-10-18

In the prototype, firing a transition wrote `TransitionFired` together with one `ActionRequest` row
per action and `TimerScheduled`/`TimerCancelled` rows, all in the Store's transaction. Signals were
also queued there. The outbox was therefore the durable queue for automation's work, and the Command
side had to know the shape of every action. We narrow the outbox to **Store events** only (object
created, edited or deleted, transition fired, each stamped with the Ontology version). The host
relays them into the Store's Observable. sequeless-automation evaluates triggers against the
automation contract of that version and hands the resulting Side effects to its runtime adapter,
which owns retries, idempotency and timers. A Side effect aimed at a Store comes back as a Store
Command through store-core's inbound port, so authorization and validation apply. This lets the Command side
and automation be released independently and keeps the outbox a log of what happened, which a
later move to event sourcing can build on.

## Considered options

- **Outbox also queues Side-effect Commands.** Firing and enqueueing stay atomic, but the Command
  side keeps depending on automation's action vocabulary, and every new action kind changes the
  Store's table contract.
- **Keep the prototype's mix.** Rejected for the same reason, and because timers and signals are
  not things a Store did.

## Consequences

- Durability of pending Side effects and timers moves to the runtime. Temporal is the durable
  production runtime. The in-process adapter is in-memory and only for development and tests.
- Timers are scheduled by automation from Store events. When one elapses, automation sends a
  "fire transition" Store Command guarded by the expected state.
- Signals become a Store Command on the Command API, answered synchronously.
- Store events must carry the Ontology version, and Behaviour and automation definitions are looked up
  by (tenant, version) so that an in-flight event is handled under the rules it was committed
  under.
- Recomputing materialised rollups remains the Command side's own consumer of Store events, not
  automation.
