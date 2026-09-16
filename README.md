# Sequeless

Sequeless is a hexagonal, ontology-driven business-object platform: an OWL ontology (stored as
Turtle, loaded into Apache Jena) describes which business object types exist, their properties,
relationships, validation shapes and state machines, and the platform serves generic Browse,
Read, Edit, Add and Delete behaviour for every type it describes — no per-type code. This
repository is **phase 0**: a walking skeleton that proves the module boundaries, the port/adapter
wiring, and the build/CI shape. It contains no ontology loading, no persistence, and no business
object code — only a `/whoami` endpoint, Actuator health, and the port-registry machinery that
later phases will hang real adapters off.

See [docs/architecture/](docs/architecture/) for the full design; this file only covers building,
running, and swapping adapters.

## Prerequisites

- JDK 25
- Maven 3.9+
- Docker (only needed for the local infrastructure in `docker-compose.yml`; the app does not talk
  to it yet)

## Build

```
mvn -B verify
```

## Run

```
mvn -pl sequeless-app spring-boot:run
```

```
$ curl localhost:8080/whoami
{"tenant":"default","principal":"anonymous","displayName":"Anonymous","roles":[],"decision":{"allowed":true,"reason":"permit-all adapter: all requests are allowed"}}

$ curl localhost:8080/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
```

Phase 0 has no inbound identity provider, so every request is reported against the default tenant
and an anonymous principal — that's expected, not a bug.

## Swapping an adapter by property

Every outbound port is bound to exactly one adapter at startup by a `sequeless.<port>.adapter`
property. There is currently one port wired this way: `AuthorizationPort`, via
`sequeless.authz.adapter`, with `permit-all` as its only shipped adapter. The same convention will
apply to every other port as its adapters land in later phases.

The property can be set the same way as any Spring Boot property — pick one:

**`application.yaml`** (this is what ships in `sequeless-app/src/main/resources/application.yaml`
today):

```yaml
sequeless:
  authz:
    adapter: permit-all
```

**Command line:**

```
mvn -pl sequeless-app spring-boot:run -Dspring-boot.run.arguments=--sequeless.authz.adapter=permit-all
```

**Environment variable** (Spring's relaxed binding maps `SEQUELESS_AUTHZ_ADAPTER` to
`sequeless.authz.adapter`):

```
SEQUELESS_AUTHZ_ADAPTER=permit-all java -jar sequeless-app/target/sequeless-app-0.1.0-SNAPSHOT.jar
```

Naming an adapter that isn't on the classpath fails startup fast, with a Spring Boot failure
analysis report instead of a raw stack trace. Actual output from
`java -jar sequeless-app/target/sequeless-app-0.1.0-SNAPSHOT.jar --sequeless.authz.adapter=missing`:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Property 'sequeless.authz.adapter' is set to 'missing', which is not a known AuthorizationPort adapter. Available adapters: [permit-all]

Action:

Set 'sequeless.authz.adapter' to one of: permit-all
```

### Writing an adapter

1. Implement the port interface from `sequeless-spi`.
2. Ship a Spring `@AutoConfiguration` class, guarded by
   `@ConditionalOnProperty(name = "sequeless.<port>.adapter", havingValue = "<name>")` and with no
   `matchIfMissing` — the port registry, not Spring, decides what happens when the property is
   absent or ambiguous.
3. Register that class in
   `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
4. Register an `AdapterDescriptorProvider` in
   `src/main/resources/META-INF/services/org.sequeless.spi.AdapterDescriptorProvider`, so the
   adapter is discoverable via `ServiceLoader` outside of Spring too.
5. Extend the matching abstract contract test in `sequeless-spi-testkit` — an adapter is
   considered compliant once it passes the kit.

`sequeless-adapter-authz-permitall` is a complete, minimal example of all five steps.

## Module map

| Module | Responsibility | May depend on |
|---|---|---|
| `sequeless-parent` | Root POM: versions, plugin management, module list, ArchUnit/japicmp config | none |
| `sequeless-spi` | Outbound port interfaces, immutable value types (records), `AdapterDescriptor` for ServiceLoader, `SpiVersion` | nothing outside the JDK |
| `sequeless-spi-testkit` | Abstract JUnit 5 contract tests per port, plus the reference-domain ontology fixture | `sequeless-spi`, JUnit 5, AssertJ |
| `sequeless-core` | Use cases (inbound API in `…core.api`), BREAD orchestration, structural validation, state machine interpreter, derivation planner, event emission. Pure Java, records, no Lombok | `sequeless-spi` |
| `sequeless-adapter-ontology-jena` | `OntologyPort` and `ValidationPort` via Jena 6 ontapi, built-in OWL reasoner, jena-shacl; Turtle import/export | `sequeless-spi`, Jena, Spring Boot autoconfigure (optional scope), Lombok |
| `sequeless-adapter-persistence-postgres` | `ObjectStorePort` and `QueryPort` on PostgreSQL 18 JSONB; Flyway migrations; outbox table; hint-driven indexes | `sequeless-spi`, JDBC/Spring JDBC, Flyway, Lombok |
| `sequeless-adapter-expression-jexl` | `ExpressionPort` via Commons JEXL 3 in a sandboxed, side-effect-free configuration | `sequeless-spi`, JEXL |
| `sequeless-adapter-automation-temporal` | `AutomationPort` via Temporal Java SDK: outbox relay, action workflows, timers, signals | `sequeless-spi`, Temporal SDK, Spring Boot starter |
| `sequeless-adapter-automation-inprocess` | `AutomationPort` test/dev adapter: synchronous executor over the outbox | `sequeless-spi` |
| `sequeless-adapter-authz-permitall` | `AuthorizationPort` that allows everything | `sequeless-spi` |
| `sequeless-app` | Spring Boot application: REST inbound adapter, wiring, Actuator, OpenAPI, ArchUnit tests, end-to-end tests with Testcontainers | everything above |

In phase 0, `sequeless-spi`, `sequeless-spi-testkit`, `sequeless-core`,
`sequeless-adapter-authz-permitall` and `sequeless-app` carry real code (the `WhoAmI` use case,
its `AuthorizationPort` contract test, the permit-all adapter, and the REST/wiring layer that
serves it). The other five adapter modules are empty shells — a POM and a `package-info.java` —
reserving their place in the module graph for the phases that give them content. See
[docs/architecture/01-overview.md](docs/architecture/01-overview.md) for the full port catalogue
and the boundary rules ArchUnit enforces.

## Local infrastructure

```
docker compose up -d
```

Starts PostgreSQL 18 (port 5432) and a Temporal dev server (gRPC on 7233, UI on 8233). Nothing in
the application talks to either service yet — they exist so the persistence and automation phases
have infrastructure ready to build against. `docker compose down -v` tears both down along with
their volumes.
