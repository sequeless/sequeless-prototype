# Follow-up prompt: Phase 0

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 0 of Sequeless: walking skeleton and enforceable boundaries.

CONTEXT
Sequeless is a new Spring Boot web application for modelling and managing business objects whose
types are defined at runtime by an OWL ontology. The ontology (plus a custom `sq:` annotation
vocabulary) is the single source of truth for types, properties, relationships, validation shapes,
display hints, facets, index hints, state machines and derivation rules. The domain core never
depends on Jena, Spring, databases or workflow engines; it consumes an immutable meta-model snapshot
through a port. Business object instances are stored in PostgreSQL 18 in one JSONB table. Guards use
JEXL through an expression port. Transition actions go through a transactional outbox and run on
Temporal. Every external concern is a port in a published SPI module with one default adapter,
selected by a `sequeless.<port>.adapter` property and discovered by Spring Boot auto-configuration.
Boundaries are enforced by Maven scopes and ArchUnit. The reference domain is Projects, Tasks and
People. The repository is empty except for docs/architecture/, which you must read first
(01-overview.md and 02-decision-records.md in particular).

Platform: Java 25, Spring Boot 4.1.x, Maven multi-module. Records and no Lombok in sequeless-spi
and sequeless-core; Lombok allowed in adapters and the app. Verify current patch versions of
dependencies before pinning them.

OBJECTIVE
Produce a bootable multi-module build in which the port-and-adapter mechanism is proven end to end
with one real port (AuthorizationPort, permit-all default), boundaries are enforced by tests, and
the local infrastructure (PostgreSQL 18 and Temporal) runs under Docker Compose even though it is
not used yet.

CONSTRAINTS
- Module layout exactly as in docs/architecture/01-overview.md. Create empty shells for modules that
  have no content yet, with correct dependencies declared.
- sequeless-spi imports nothing outside java.*. sequeless-core imports only java.* and the SPI. No
  adapter depends on core or on another adapter. Only sequeless-app references Spring MVC.
- Adapter discovery: each adapter module ships a Spring Boot auto-configuration in
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports, conditional on
  sequeless.<port>.adapter=<name>, plus an AdapterDescriptor registered under META-INF/services.
  sequeless-app fails fast with a clear message when a property names an adapter not on the
  classpath, or when zero or multiple beans exist for a port.
- Do not introduce any ontology, persistence, or business object code.

DELIVERABLES
- sequeless-parent POM with dependency and plugin management, japicmp plugin configured for
  sequeless-spi (no baseline yet), ArchUnit and Testcontainers dependencies managed.
- sequeless-spi: Scope(tenantId, principal), TenantId, Principal, AdapterDescriptor, SpiVersion,
  AuthorizationPort with an AccessDecision result and an Operation enum (BROWSE, READ, EDIT, ADD,
  DELETE, TRANSITION, ADMIN).
- sequeless-spi-testkit: AuthorizationContract abstract test.
- sequeless-adapter-authz-permitall passing the contract, with auto-configuration and descriptor.
- sequeless-core: a WhoAmI use case in package ...core.api that consults AuthorizationPort.
- sequeless-app: Spring Boot application, Actuator health, GET /whoami calling the use case,
  PortRegistry startup check, ArchUnit test class encoding every boundary rule above, an
  end-to-end test that boots the app with sequeless.authz.adapter=permit-all.
- docker-compose.yml with postgres:18 and a Temporal server (auto-setup image with its own
  PostgreSQL database or the Temporal dev server), plus healthchecks.
- README.md at repo root: how to build, run, and swap an adapter by property.
- A CI script or GitHub Actions workflow running mvn -B verify.

ACCEPTANCE CRITERIA
- mvn -B verify passes from a clean checkout.
- Temporarily adding an org.springframework import to sequeless-core makes the ArchUnit test fail
  (demonstrate in the PR description, then remove).
- Starting the app with sequeless.authz.adapter=missing fails with a message naming the property
  and the available adapters.
- docker compose up shows all services healthy; the app started locally reports UP on
  /actuator/health.
- GET /whoami returns the anonymous principal and the default tenant.

OUT OF SCOPE
Ontology loading, Jena, PostgreSQL access, Flyway migrations, any REST resource beyond /whoami,
security configuration, UI.

PROCESS
Plan before coding: read docs/architecture, propose the file and module layout and the ArchUnit
rules, and confirm with me before generating the build. Ask via AskUserQuestion whenever something
is ambiguous (for example the Temporal image to use) rather than guessing. Keep commits small and
scoped per task.
```
