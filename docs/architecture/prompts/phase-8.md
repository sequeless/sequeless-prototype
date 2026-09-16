# Follow-up prompt: Phase 8

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 8 of Sequeless: adapter swap proof, OIDC authorisation, external events, SPI 1.0.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The core depends only on
sequeless-spi and consumes ports: OntologyPort and ValidationPort (Jena, SHACL), ObjectStorePort
and QueryPort (PostgreSQL JSONB), ExpressionPort (JEXL), AutomationPort (Temporal, in-process),
AuthorizationPort (permit-all). Adapters are selected by sequeless.<port>.adapter, discovered by
Spring Boot auto-configuration, listed via ServiceLoader descriptors, and must pass the contract
tests in sequeless-spi-testkit. The full runtime exists: BREAD, query and facets, derivations,
state machines with durable automation, meta-model evolution. Read docs/architecture and existing
modules first.

OBJECTIVE
Prove that adapters swap by configuration only, add real authorisation and external event
publishing as adapters, and freeze the SPI at 1.0.0 with an adapter authoring guide.

CONSTRAINTS
- sequeless-adapter-persistence-inmemory implements ObjectStorePort and QueryPort (including
  aggregate, text search as simple contains, facets) and passes every contract; it is selected
  by sequeless.persistence.adapter=inmemory and sequeless.query.adapter=inmemory. No core change
  is permitted to make it work; if one seems needed, stop and report it as an SPI defect.
- sequeless-adapter-authz-oidc: Spring Security resource server validating OIDC tokens (use a
  Testcontainers Keycloak or a mock JWT issuer in tests), mapping token claims to Principal roles;
  AuthorizationPort decisions from sq:permission annotations on types and properties in the
  ontology (read and write permissions per role); the core already calls the port for every
  operation and must apply field-level decisions by omitting hidden properties on read and
  rejecting them on write. Spring Security lives only in this adapter and the app.
- sequeless-adapter-events-webhook (or Kafka; pick one and justify): publishes domain events from
  the outbox to an external endpoint with at-least-once delivery, selected by
  sequeless.events.publisher=webhook|none.
- SPI 1.0.0: review every public type in sequeless-spi for naming and minimality, set the japicmp
  baseline, document deprecation policy.
- docs/architecture/adapter-guide.md: how to write, register, test and ship a compliant adapter,
  including the property naming, descriptor, auto-configuration, and contract test wiring.

DELIVERABLES
- The three adapter modules with auto-configuration, descriptors and contract tests.
- Core field-level authorisation enforcement; permit-all remains the default.
- Reference ontology gains sq:permission examples (Person.email visible to role admin only;
  Project.delete allowed to role manager).
- SPI 1.0.0 release notes and japicmp baseline; CHANGELOG.
- The adapter guide, validated by a dry run: follow it to write a trivial ExpressionPort
  alternative (for example a constant-true evaluator) in a scratch module and delete it.

ACCEPTANCE CRITERIA
- The full end-to-end suite passes unchanged with persistence and query set to inmemory.
- With the OIDC adapter, a request without a token gets 401; a non-admin reading a Person does not
  see email; a non-manager DELETE on a Project returns 403; the same requests succeed for the
  right roles.
- Domain events for a Task create reach the webhook stub at least once and carry the object id,
  type, kind and version.
- A deliberately breaking change to an SPI interface fails the build via japicmp; reverting it
  passes.
- mvn -B verify passes.

OUT OF SCOPE
UI, multi-tenancy behaviour, new business features, changes to the core beyond field-level
authorisation.

PROCESS
Plan before coding: propose the in-memory adapter design, the sq:permission vocabulary, the role
mapping, and the event publisher choice with justification, and confirm with me before
implementing. Ask via AskUserQuestion when ambiguous. Verify current Spring Security and
Keycloak Testcontainers module versions before pinning.
```
