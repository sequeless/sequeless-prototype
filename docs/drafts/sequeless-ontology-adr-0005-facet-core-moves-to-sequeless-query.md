<!-- Draft for sequeless-ontology docs/adr/. Not yet committed there. Decided in
     "Moving the prototype's ontology into sequeless-ontology" (sequeless/sequeless-prototype#13). -->

# 0005. facet-core moves to sequeless-query

- Status: proposed
- Date: 2026-10-14
- Supersedes: 0002

ADR 0002 put `facet-core` here as neutral ground, so that sequeless-ontology and sequeless-filter
could share a vocabulary for "what can be filtered" without either depending on the other.
Neither ever used it. sequeless-filter defines its own `FieldContributor` and `OperatorContributor`
SPI, and the RDF-backed `FacetSource` never landed here. Everything `facet-core` describes (what can
be filtered, which Ontology versions are in scope, traversal, concept values) is part of answering
a Browse. So `facet-core` and `facet-core-testfixtures` move to the new `sequeless-query` repo.
There, Facets are produced from `schema-contract` and bridged into sequeless-filter's
`FieldContributor`. sequeless-ontology and sequeless-filter still don't depend on each other. They
now don't depend on `facet-core` either.

## Consequences

- The zero-dependency rule, its Enforcer execution and its ArchUnit rule all move with the module.
- The `Principal` folding drafted as ADR 0003 travels too, and becomes a sequeless-query ADR.
- The facet-contract spec (`docs/specs/facet-contract/design.md`) moves to sequeless-query and stops
  being a mirrored copy.
