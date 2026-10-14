<!-- Draft for sequeless-ontology docs/adr/. Not yet committed there. Decided in
     "Moving the prototype's ontology into sequeless-ontology" (sequeless/sequeless-prototype#13). -->

# 0004. A thin ontology-core, a Jena adapter, and a version per module

- Status: proposed
- Date: 2026-10-14
- Amends: 0001

ADR 0001 pictured `ontology-core` holding a library-neutral domain model of the Ontology, with
`ontology-owl-adapter` translating OWL into and out of it. The prototype's ontology code moving in
has no such model. Its compiler maps the authored OWL/SHACL straight into the Schema and Behaviour,
and those two contracts (`schema-contract`, `behaviour-contract`) already are the library-neutral
model. A third representation would have no consumer. So `ontology-core` stays thin: it holds the
`OntologyAdministration` api (import, export, reload, activate), the validation api over
`sequeless-kernel` types, and the `OntologyDocumentStore` spi. The adapter is Apache Jena
(`jena-ontapi` + `jena-shacl`), not the OWL API, because the OWL API has no SHACL or RDF store. It
is renamed `ontology-jena-adapter`. It stays Spring-free; deployables do the wiring.

ADR 0001 also releases the whole reactor at one version. With `schema-contract` and
`behaviour-contract` in the reactor, that would turn every Behaviour major into a Schema major,
which the Query side depends on and nothing else. Each module is versioned and released on its own
instead.

## Consequences

- `SchemaSource` lives in `schema-contract` and `BehaviourSource` lives in `behaviour-contract`,
  not in `ontology-core`, so the Query side depends on `schema-contract` alone.
- SHACL validation is implemented in `ontology-jena-adapter`, next to the loader, because the
  shapes are read from the loaded Ontology rather than from the Schema.
- Child modules declare their own `<version>`. The `.releaserc.json` setup that ADR 0001 relies on
  (`-DprocessAllModules=true` and the `*/pom.xml` asset glob) is replaced with per-module release
  tooling.
- `ontology-testfixtures` publishes the contract kits for these ports, following the pattern set by
  `facet-core-testfixtures`.
