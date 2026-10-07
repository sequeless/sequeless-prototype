# Design Preferences: Wayfinder Interview Guidelines

## Philosophy

Design decisions in this project should prioritize:

1. **Minimal and modular** — implement only what's needed; avoid over-engineering or speculative design
2. **Maintainable** — favor clarity, loose coupling, and high cohesion
3. **Open to evolve** — design to support future alternatives without major rewrites; avoid locking into single implementations

## Architectural Pattern: Hexagonal Architecture

This project uses **hexagonal (ports-and-adapters) architecture** as its core design pattern.

**Structure:**
- **Core domain** (sequeless-core): Expresses business logic independent of any external technology
- **Ports** (sequeless-spi): Interfaces defining contracts between core and adapters
- **Default adapters**: Sensible implementations for each port (Jena for ontology, PostgreSQL for persistence, JEXL for expressions, Temporal for automation, etc.)
- **Module boundaries**: ArchUnit and Maven dependency rules enforce isolation and prevent leakage

**Reference:** See `docs/architecture/01-overview.md` for the port catalogue, module layout, and hexagon diagram.

## Adapter Strategy

**Default implementations:** Each port has a production-ready default adapter, selected by configuration.

**Swapping:** Alternative implementations (different technology, different strategy) are selected **via Spring configuration properties** (e.g., `sequeless.ontology.adapter=jena`), not code branching or feature flags.

**Contract tests:** The `sequeless-spi-testkit` module provides abstract JUnit 5 contract tests per port, ensuring any new adapter meets the same guarantees as the default.

**Example:** Want to swap Jena for RDF4J as the ontology reasoner?
- Write `sequeless-adapter-ontology-rdf4j` implementing `OntologyPort`
- Set `sequeless.ontology.adapter=rdf4j` in configuration
- No code changes in core or other adapters

## How Wayfinder Should Apply This

When grilling or recommending architecture:

1. **Ask about boundaries** — Should this logic live in core, or does it belong in an adapter behind a new port?
2. **Prefer ports over magic** — If a new cross-cutting concern emerges (auth, logging, external APIs), propose a new port before baking it into core.
3. **Sketch adapters** — Suggest 2–3 plausible adapter implementations before settling on the default. This validates the port contract.
4. **Avoid premature unification** — Don't merge similar concerns into one port until they're proven to share semantics. Three different expression languages ≠ one `ExpressionPort`.
5. **Configuration over code** — Recommend configurable adapter selection, not compile-time choices or constants.

## Reference

- **Architecture overview**: `docs/architecture/01-overview.md`
- **Decision records**: `docs/adr/` — consult these when revisiting design choices (why Jena? Why JSONB? Why Temporal?)
- **Module layout rules**: Enforced by ArchUnit in `sequeless-app` and Maven dependency scopes
