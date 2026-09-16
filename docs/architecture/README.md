# Index and glossary


Sequeless architecture documents, produced 2026-09-16.

1. `01-overview.md` — hexagon, modules, port catalogue, SPI approach
2. `02-decision-records.md` — one entry per significant decision
3. `03-phased-plan.md` — nine phases with exit criteria
4. `prompts/phase-N.md` — self-contained `/beacon:plan` prompts, one per phase

## Glossary

- **Business object**: an instance of a type, such as one Project. Stored in PostgreSQL.
- **Type**: an OWL class in the ontology describing a kind of business object.
- **Meta-model (ontology of ontologies)**: the OWL ontology plus the `sq:` custom vocabulary. The single source of truth for structure and behaviour.
- **Meta-model snapshot**: the core's own immutable, reasoned, read-only view of the meta-model, supplied through the ontology port. Contains no Jena types.
- **Derived property**: a property whose value is computed from related objects by a declarative rollup rule or a named plug-in.
- **Derived object**: a type whose properties are mostly or wholly derived. Not a separate concept.
- **State machine**: states, transitions, guards and actions declared in the ontology for a type.
- **Facet**: a filterable dimension of a type, declared in the ontology, returned with bucket counts by the query port.
- **Projection**: display hints in the snapshot and API responses that tell a client what to show and how.
- **Port**: an interface the core calls (outbound) or that drives the core (inbound).
- **Adapter**: an implementation of a port using a specific technology.
- **SPI**: the separate, stable Maven module publishing outbound ports, shared value types, and the contract test kit.
- **Outbox**: rows written in the same transaction as an object change, holding events and actions to be executed afterwards.
- **Scope**: the tenant and principal on whose behalf an operation runs. Single tenant in this plan.
