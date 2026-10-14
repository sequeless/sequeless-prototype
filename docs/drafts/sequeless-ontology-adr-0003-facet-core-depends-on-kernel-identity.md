<!-- Draft for sequeless-ontology docs/adr/. Not yet committed there. Decided in
     "Where the shared kernel lives" (sequeless/sequeless-prototype#12). -->

# 0003. facet-core may depend on sequeless-kernel's kernel-identity, and nothing else

- Status: proposed
- Date: 2026-10-14
- Amends: 0002

`facet-core` defined its own `Principal(id, roles)`, duplicating the one every other Sequeless
module shares. That shared one now lives in `sequeless-kernel`'s `kernel-identity` module
(`Scope`, `TenantId`, `Principal`), which itself has no dependencies outside the JDK. We fold
`facet-core`'s `Principal` into it, so `facet-core` gains exactly one compile dependency:
`kernel-identity`. ADR 0002's aim still holds, which is to keep RDF4J and ontology machinery out of
sequeless-filter. A dependency-free identity module doesn't break that, and one shared `Principal`
avoids two types that would drift apart. `kernel-identity` is kept separate from `kernel-values`
(`ObjectId`, `Value`), so new value types never cause a `facet-core` or sequeless-filter release.

## Consequences

- The `ban-all-runtime-dependencies` Enforcer execution gains a single `<includes>` exception for
  `org.sequeless:kernel-identity`. Anything else is still banned.
- The ArchUnit rule widens from "nothing outside `java..` and its own packages" to also allow the
  `kernel-identity` packages.
- `facet-core`'s `Cardinality` (`SINGLE`/`MANY`) is deliberately **not** folded into the Schema's
  `Cardinality(min, max)`. They are different concepts, and the code that produces Facets derives
  one from the other.
