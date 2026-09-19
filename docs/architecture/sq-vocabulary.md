# The `sq:` vocabulary


Namespace: `https://sequeless.dev/ns/meta#`. Document (ontology) IRI: `https://sequeless.dev/ns/meta`
(no trailing `#` — the `#` is the fragment separator used by every term IRI). Conventional prefix:
`sq:`. The canonical Turtle declaration of every term below lives in
`sequeless-spi-testkit/src/main/resources/ontology/sq-meta.ttl` (T4); any ontology document that uses
`sq:` terms imports this document via `owl:imports <https://sequeless.dev/ns/meta>`.

`sq:` is a small annotation vocabulary layered on top of standard OWL. OWL describes what a type
*is* (classes, subclass relationships, properties, domains, ranges, cardinality restrictions,
consistency); `sq:` describes what the application needs to *display and enforce* that OWL has no
term for — labels, ordering, grouping, facets, indexing hints, and two structural flags OWL itself
cannot express (see "Two terms beyond OWL" below). Every `sq:` term maps to exactly one field on a
`org.sequeless.spi.meta` snapshot record (T2); this document is the single source of truth for that
mapping, and the Jena adapter's `SqVocabulary` (T7) is a direct transcription of it.

## Terms in use (phase 1)

| IRI | RDF type | Domain | Range | Default | Snapshot field |
|---|---|---|---|---|---|
| `sq:label` | `owl:AnnotationProperty` | `owl:Class` ∪ `rdf:Property` | `rdfs:Literal` | falls back to `rdfs:label`, then the IRI's local name | `TypeDefinition.label()` / `PropertyDefinition.label()` |
| `sq:displayOrder` | `owl:AnnotationProperty` | `owl:Class` ∪ `rdf:Property` | `xsd:integer` | `Integer.MAX_VALUE` (unordered properties sort last) | `DisplayHints.order()` |
| `sq:displayGroup` | `owl:AnnotationProperty` | `owl:Class` ∪ `rdf:Property` | `xsd:string` | absent (`Optional.empty()`) | `DisplayHints.group()` |
| `sq:hidden` | `owl:AnnotationProperty` | `owl:Class` ∪ `rdf:Property` | `xsd:boolean` | `false` | `DisplayHints.hidden()` |
| `sq:facet` | `owl:AnnotationProperty` | `rdf:Property` | `xsd:boolean` | `false` | `PropertyDefinition.facet()` |
| `sq:indexed` | `owl:AnnotationProperty` | `rdf:Property` | `xsd:boolean` | `false` | `PropertyDefinition.indexed()` |
| `sq:searchable` | `owl:AnnotationProperty` | `rdf:Property` | `xsd:boolean` | `false` | `PropertyDefinition.searchable()` |
| `sq:readOnly` | `owl:AnnotationProperty` | `rdf:Property` | `xsd:boolean` | `false` | `PropertyDefinition.readOnly()` |
| `sq:displayLabel` | `owl:AnnotationProperty` | `rdf:Property` | `xsd:boolean` | `false` | `PropertyDefinition.displayLabel()` |
| `sq:abstract` | `owl:AnnotationProperty` | `owl:Class` | `xsd:boolean` | `false` | `TypeDefinition.isAbstract()` |

Notes:
- `sq:label` **overrides** `rdfs:label` where both are present on the same subject; it exists
  because `rdfs:label` is frequently reused by imported vocabularies for a purpose an application
  cannot rely on, while `sq:label` is unambiguously "the label this application shows."
- `sq:displayOrder`, `sq:displayGroup` and `sq:hidden` share one domain (class *and* property)
  because both `TypeDefinition` and `PropertyDefinition` carry a `DisplayHints` — a type's display
  hints govern its presentation as a whole (e.g. ordering in a type picker), a property's govern its
  presentation within a type's detail view.
- `sq:facet`, `sq:indexed`, `sq:searchable` and `sq:readOnly` are property-only: they describe how a
  *value* of the property behaves in storage and query, which has no meaning at the class level.
- `sq:displayLabel` marks a datatype property as its owning type's display label, used to render a
  reference to an instance of that type as text (e.g. a facet bucket) instead of a raw id.
- `sq:abstract` is class-only: it marks a type that exists purely for other types to specialise
  (`WorkItem`, `Deliverable` in the reference ontology) and that the application should not offer as
  a directly instantiable type.

## Terms in use (phase 4)

Phase 4 (derived properties, DR-08) adds a second layer on top of `sq:`: a property can carry a
derivation rule instead of (never alongside) a stored value. `sq:derivedBy` attaches the rule;
`sq:Rollup` and `sq:Plugin` are the two rule shapes; `sq:Criterion` is a filter condition inside a
rollup's `sq:filter` list.

| IRI | RDF type | Domain | Range | Default | Snapshot field |
|---|---|---|---|---|---|
| `sq:derivedBy` | `owl:AnnotationProperty` | `rdf:Property` | — (conceptually `sq:Rollup` ∪ `sq:Plugin`; deliberately unasserted, see notes) | absent (`Optional.empty()`) | `PropertyDefinition.derivation()` |
| `sq:function` | `owl:AnnotationProperty` | `sq:Rollup` | — (enumeration; see notes) | none, required | `RollupRule.function()` |
| `sq:over` | `owl:AnnotationProperty` | `sq:Rollup` | `owl:Class` | none, required | `RollupRule.sourceTypeIri()` |
| `sq:via` | `owl:AnnotationProperty` | `sq:Rollup` | `rdf:Property` | none, required | `RollupRule.viaIri()` |
| `sq:of` | `owl:AnnotationProperty` | `sq:Rollup` | `rdf:Property` | absent (required for `sum`/`min`/`max`/`avg`; absent for `count`) | `RollupRule.ofPropertyIri()` |
| `sq:filter` | `owl:AnnotationProperty` | `sq:Rollup` | `rdf:List` | absent (`[]`) | `RollupRule.criteria()` |
| `sq:property` | `owl:AnnotationProperty` | `sq:Criterion` | `rdf:Property` | none, required | `Criterion.property()` |
| `sq:operator` | `owl:AnnotationProperty` | `sq:Criterion` | — (enumeration; see notes) | none, required | `Criterion.operator()` |
| `sq:value` | `owl:AnnotationProperty` | `sq:Criterion` | `rdfs:Literal` | absent (required unless operator is `sq:isNull`/`sq:notNull`) | `Criterion.value()` |
| `sq:pluginName` | `owl:AnnotationProperty` | `sq:Plugin` | `xsd:string` | none, required | `PluginRule.pluginName()` |

Notes:
- `sq:Rollup`, `sq:Plugin` and `sq:Criterion` are `owl:Class`, not annotation properties — they
  type the blank nodes `sq:derivedBy` and `sq:filter` point at. They have no snapshot field of
  their own; a node's type selects which SPI record (`RollupRule` vs. `PluginRule`) it maps to.
- `sq:function` and `sq:operator` have no `rdfs:range`: their legal values are a fixed
  enumeration (the five function individuals and eleven operator individuals below), which RDFS
  cannot express without `owl:oneOf`. The set is validated at snapshot activation instead.
- The five function individuals — `sq:count`, `sq:sum`, `sq:min`, `sq:max`, `sq:avg` — are
  `owl:NamedIndividual`s with no snapshot field of their own: they are the *values* `sq:function`
  takes, matched by IRI local name against `org.sequeless.spi.meta.AggregateFunction`'s enum
  constant names (T3's job).
- The eleven operator individuals — `sq:eq`, `sq:ne`, `sq:in`, `sq:lt`, `sq:lte`, `sq:gt`,
  `sq:gte`, `sq:contains`, `sq:startsWith`, `sq:isNull`, `sq:notNull` — are likewise
  `owl:NamedIndividual`s with no snapshot field of their own: they are the values `sq:operator`
  takes, matched by IRI local name against `org.sequeless.spi.query.Operator`'s enum constant
  names (T3's job).
- Declaring `sq:derivedBy` on a property forces `PropertyDefinition.readOnly()` to `true`
  regardless of any `sq:readOnly` assertion — a derived value is never accepted on write.
- `sq:derivedBy`'s range is conceptually the union `sq:Rollup` ∪ `sq:Plugin`, but no `rdfs:range` is
  asserted for either arm, unlike `sq:label`'s inexpressible union *domain* (which is simply omitted
  the same way). Asserting just `sq:Rollup` — as an earlier draft of this vocabulary did — is not
  merely imprecise: under a rule-based reasoner (`ReasonerSetting.OWL`/`RDFS`, which is what the
  running application actually uses), the standard `rdfs:range` entailment rule adds `rdf:type
  sq:Rollup` to *every* `sq:derivedBy` object, including genuine `sq:Plugin` nodes, which silently
  defeats `SnapshotMapper`'s "typed both/neither `sq:Rollup`/`sq:Plugin`" mutual-exclusion check.
  `sq:over`/`sq:via`/`sq:of`/`sq:property`/`sq:value`/`sq:pluginName`, by contrast, each assert a
  genuinely single range and are safe to entail.

## Terms in use (phase 5)

Phase 5 (state machines and automation, DR-09) adds a third layer on top of `sq:`: a type can carry a
state machine — a set of named states, one initial, and transitions between them, each guarded by an
optional condition and followed by an ordered list of actions. `sq:appliesTo` attaches the definition
to a type; `sq:StateMachine`, `sq:State` and `sq:Transition` are the structural shapes; `sq:SetProperty`,
`sq:CreateObject`, `sq:Webhook` and `sq:Log` are the four action kinds a transition can carry out, each
built from `sq:PropertyAssignment` nodes where property values are being set.

| IRI | RDF type | Domain | Range | Default | Snapshot field |
|---|---|---|---|---|---|
| `sq:appliesTo` | `owl:AnnotationProperty` | `sq:StateMachine` | `owl:Class` | none, required | attaches the definition to that type's `TypeDefinition.stateMachine()` |
| `sq:initialState` | `owl:AnnotationProperty` | `sq:StateMachine` | `sq:State` | none, required | `StateMachineDefinition.initialState()` |
| `sq:state` | `owl:AnnotationProperty` | `sq:StateMachine` | `sq:State` | none, required (≥1) | `StateMachineDefinition.states()` |
| `sq:transition` | `owl:AnnotationProperty` | `sq:StateMachine` | `sq:Transition` | absent (`[]`) | `StateMachineDefinition.transitions()` |
| `sq:name` | `owl:AnnotationProperty` | `sq:Transition` | `xsd:string` | none, required | `Transition.name()` |
| `sq:from` | `owl:AnnotationProperty` | `sq:Transition` | `sq:State` | none, required | `Transition.fromStateIri()` |
| `sq:to` | `owl:AnnotationProperty` | `sq:Transition` | `sq:State` | none, required | `Transition.toStateIri()` |
| `sq:trigger` | `owl:AnnotationProperty` | `sq:Transition` | — (enumeration; see notes) | none, required | validated only — this phase supports exactly one legal value, `sq:UserAction`; no snapshot field |
| `sq:guard` | `owl:AnnotationProperty` | `sq:Transition` | `xsd:string` | absent (always available) | `Transition.guard()` |
| `sq:guardMessage` | `owl:AnnotationProperty` | `sq:Transition` | `xsd:string` | absent (generic fallback message) | `Transition.guardMessage()` |
| `sq:action` | `owl:AnnotationProperty` | `sq:Transition` | `rdf:List` | absent (`[]`) | `Transition.actions()` |
| `sq:property` *(reused)* | `owl:AnnotationProperty` | `sq:Criterion` ∪ `sq:SetProperty` ∪ `sq:PropertyAssignment` | `rdf:Property` | none, required | `Criterion.property()` / `SetPropertyAction.propertyIri()` / `PropertyAssignment.propertyIri()` |
| `sq:value` *(reused)* | `owl:AnnotationProperty` | `sq:Criterion` ∪ `sq:SetProperty` ∪ `sq:PropertyAssignment` | `rdfs:Literal` | absent | `Criterion.value()` / `SetPropertyAction.value()` / `PropertyAssignment.value()` |
| `sq:expression` *(new)* | `owl:AnnotationProperty` | `sq:SetProperty` ∪ `sq:PropertyAssignment` | `xsd:string` | absent | `SetPropertyAction.expression()` / `PropertyAssignment.expression()` |
| `sq:type` | `owl:AnnotationProperty` | `sq:CreateObject` | `owl:Class` | none, required | `CreateObjectAction.typeIri()` |
| `sq:properties` | `owl:AnnotationProperty` | `sq:CreateObject` | `rdf:List` (of `sq:PropertyAssignment`) | absent (`[]`) | `CreateObjectAction.properties()` |
| `sq:url` | `owl:AnnotationProperty` | `sq:Webhook` | `xsd:string` | none, required | `WebhookAction.url()` |
| `sq:method` | `owl:AnnotationProperty` | `sq:Webhook` | `xsd:string` | `"POST"` | `WebhookAction.method()` |
| `sq:body` | `owl:AnnotationProperty` | `sq:Webhook` | `xsd:string` | absent | `WebhookAction.body()` |
| `sq:message` | `owl:AnnotationProperty` | `sq:Log` | `xsd:string` | none, required | `LogAction.message()` |

Notes:
- `sq:StateMachine`/`sq:State`/`sq:Transition`/`sq:SetProperty`/`sq:CreateObject`/`sq:PropertyAssignment`/`sq:Webhook`/`sq:Log`
  are `owl:Class`, exactly like `sq:Rollup`/`sq:Plugin`/`sq:Criterion` — they type the nodes the
  properties above point at and have no snapshot field of their own.
- `sq:State` individuals reuse the existing `sq:label`/`sq:displayOrder` terms, per those terms'
  already-documented shared `owl:Class ∪ rdf:Property` domain.
- `sq:state` is plain multi-valued (order doesn't matter); `sq:transition` and `sq:action` are
  `rdf:List`s (order matters), mirroring the `sq:filter` precedent.
- `sq:property`/`sq:value` now have a **three-way union domain** (`sq:Criterion` ∪ `sq:SetProperty` ∪
  `sq:PropertyAssignment`). No `rdfs:domain` is asserted for either — exactly the
  `sq:label`/`sq:derivedBy` pattern — because RDFS `rdfs:domain` triples combine by **intersection**,
  not union: asserting `sq:Criterion` and `sq:SetProperty` both as domains would make a rule-based
  reasoner (`ReasonerSetting.OWL`/`RDFS`) infer every subject of an `sq:property`/`sq:value` triple is
  simultaneously typed as *all* of them. This is the same hazard the doc's phase-4 notes already call
  out for `sq:derivedBy`; this edit **removes** the existing `rdfs:domain sq:Criterion` from
  `sq:property`/`sq:value`'s declarations in the `.ttl` files, not just adds more domains.
- `sq:trigger` has no `rdfs:range` asserted, mirroring `sq:function`/`sq:operator`: a fixed
  enumeration (currently just `sq:UserAction`) that RDFS can't express without `owl:oneOf`.

## Two terms added beyond the brief

The phase-1 brief lists `sq:label`, `sq:displayOrder`, `sq:displayGroup`, `sq:facet`, `sq:indexed`,
`sq:searchable` and `sq:hidden`. Two more are needed and are declared here for the same reason as the
rest: OWL has no native way to express them.

- **`sq:abstract`** — OWL's `owl:Class` has no notion of "not directly instantiable"; every class is
  instantiable unless something external says otherwise. `TypeDefinition.isAbstract()` needs a
  source, so `sq:abstract` supplies it.
- **`sq:readOnly`** — OWL's `rdf:Property` has no notion of "computed, not writable through the
  generic API"; that is an application-level access concern, not an ontological one.
  `PropertyDefinition.readOnly()` needs a source, so `sq:readOnly` supplies it.

## Reserved terms (rejected if used now)

These terms are named and scoped so that using them early fails loudly and clearly rather than being
silently ignored. Each is implemented by a later phase; the Jena adapter's `SqVocabulary` (T7)
rejects any ontology document that declares or uses one of these terms with a message following the
pattern below, naming the term and the phase that will support it:

> `sq:<term> is reserved for Phase <N> (<topic>) and is not supported yet.`

| Term | RDF type (planned) | Reserved for |
|---|---|---|
| `sq:permission` | `owl:AnnotationProperty` | Phase 8, authorisation (DR-11): attaches a permission requirement to a type, property or transition, evaluated by `AuthorizationPort` once Spring Security/OIDC replaces permit-all. |
| `sq:materialised` | `owl:AnnotationProperty` | Phase 4, derived properties (DR-08): switches a `sq:derivedBy` rollup from on-read evaluation to event-driven materialisation. |

## OWL → snapshot mapping table

This table is the contract the Jena adapter's `SnapshotMapper` (T7) implements: for each OWL/`sq:`
construct on the left, the snapshot field it produces on the right.

| OWL / `sq:` construct | Snapshot field |
|---|---|
| `rdfs:subClassOf` (named superclass) | `TypeDefinition.superTypes()` |
| Most-specific named `rdfs:domain` of a property | the owning `TypeDefinition`'s `properties()` list (property attributed to its most specific declared domain; inherited by subtypes) |
| Most-specific named `rdfs:range` of a datatype property | `AttributeDefinition.datatype()` |
| Most-specific named `rdfs:range` of an object property | `RelationshipDefinition.targetTypeIri()` |
| Cardinality restriction (`owl:cardinality`, `owl:minCardinality`, `owl:maxCardinality`, and their qualified variants) on the type's `rdfs:subClassOf` axioms | `PropertyDefinition.cardinality()` |
| `owl:someValuesFrom` restriction (no explicit min) | treated as `min 1` for `Cardinality` purposes |
| `owl:FunctionalProperty` | `Cardinality` `max 1` (combined with any explicit min from a restriction; absent a restriction, `min 0, max 1`) |
| No restriction and not functional | default `Cardinality` — `min 0, max unbounded` (`Cardinality.optional()` with `max` empty) |
| `owl:inverseOf` | `RelationshipDefinition.inverseIri()` |
| `owl:TransitiveProperty` | `RelationshipDefinition.transitive()` |
| `sq:label` / `rdfs:label` / IRI local name (in that priority order) | `label()` |
| `sq:displayOrder` / `sq:displayGroup` / `sq:hidden` | `DisplayHints` |
| `sq:facet` / `sq:indexed` / `sq:searchable` / `sq:readOnly` / `sq:displayLabel` | matching `PropertyDefinition` boolean flags |
| `sq:abstract` | `TypeDefinition.isAbstract()` |
| Ontology IRI (`owl:Ontology` subject) | `MetaModelSnapshot.ontologyIri()` |
| `owl:versionIRI` | `MetaModelSnapshot.versionIri()` |
| Declared namespace prefixes | `MetaModelSnapshot.prefixes()` (export/display only — never used to derive a short name; see `MetaModelSnapshot`'s short-name rule) |
| Unmapped/unrecognised XSD datatype in `rdfs:range` | falls back to `Datatype.STRING`, plus a `WARNING` `OntologyIssue` naming the property and the unrecognised IRI |
| `sq:derivedBy` pointing at a `sq:Rollup`-typed blank node | `PropertyDefinition.derivation()` as a `RollupRule` |
| `sq:derivedBy` pointing at a `sq:Plugin`-typed blank node | `PropertyDefinition.derivation()` as a `PluginRule` |
| `sq:function` / `sq:over` / `sq:via` / `sq:of` / `sq:filter` (on a `sq:Rollup` node) | the matching `RollupRule.function()` / `sourceTypeIri()` / `viaIri()` / `ofPropertyIri()` / `criteria()` |
| `sq:pluginName` (on a `sq:Plugin` node) | `PluginRule.pluginName()` |
| `sq:property` / `sq:operator` / `sq:value` (on a `sq:Criterion` node) | the matching `Criterion.property()` / `operator()` / `value()` |
| Presence of `sq:derivedBy` on a property | forces `PropertyDefinition.readOnly()` to `true`, regardless of `sq:readOnly` |
| `sq:appliesTo` (on a `sq:StateMachine` node) | attaches `StateMachineDefinition` to that type's `TypeDefinition.stateMachine()` |
| `sq:initialState` / `sq:state` / `sq:transition` (on a `sq:StateMachine` node) | the matching `StateMachineDefinition.initialState()` / `states()` / `transitions()` |
| `sq:name` / `sq:from` / `sq:to` / `sq:guard` / `sq:guardMessage` / `sq:action` (on a `sq:Transition` node) | the matching `Transition.name()` / `fromStateIri()` / `toStateIri()` / `guard()` / `guardMessage()` / `actions()` |
| `sq:trigger` (on a `sq:Transition` node) | validated only against the single legal value `sq:UserAction`; no snapshot field |
| Type of a `sq:action` list element (`sq:SetProperty` / `sq:CreateObject` / `sq:Webhook` / `sq:Log`) | selects the matching `Transition.actions()` entry type (`SetPropertyAction` / `CreateObjectAction` / `WebhookAction` / `LogAction`) |
| `sq:property` / `sq:value` / `sq:expression` (on a `sq:SetProperty` node) | the matching `SetPropertyAction.propertyIri()` / `value()` / `expression()` |
| `sq:type` / `sq:properties` (on a `sq:CreateObject` node) | the matching `CreateObjectAction.typeIri()` / `properties()` |
| `sq:property` / `sq:value` / `sq:expression` (on a `sq:PropertyAssignment` node) | the matching `PropertyAssignment.propertyIri()` / `value()` / `expression()` |
| `sq:url` / `sq:method` / `sq:body` (on a `sq:Webhook` node) | the matching `WebhookAction.url()` / `method()` / `body()` |
| `sq:message` (on a `sq:Log` node) | `LogAction.message()` |

## What the reasoner setting changes

`sequeless.ontology.reasoner` selects one of three Jena `OntSpecification`s (`owl`, `rdfs`, `none`,
mapped by the adapter's `ReasonerSetting`). Reasoning applies only to the ontology (type level), never
to business object instances. Verified empirically against `jena-ontapi:6.1.0` against the reference
ontology's three-level hierarchy (`Deliverable ⊐ WorkItem ⊐ {Task, Project}`), exactly three effects
are in scope for phase 1:

1. **Superclass-closure depth.** Under `none`, a type's `superClasses(false)` includes only its
   directly asserted `rdfs:subClassOf` targets — for `Task`, just `WorkItem`. Under `rdfs` and `owl`,
   the closure is transitive — `Task` also picks up `Deliverable`. This is the basis of the
   `sequeless.ontology.reasoner=none` acceptance criterion: with reasoning off, `Task.superTypes()`
   omits `Deliverable`.
2. **Inverse-property visibility.** Under `owl`, `inverseProperties()` returns the asserted
   `owl:inverseOf` partner in both directions even when only one direction is asserted in the
   source document (e.g. `hasTask` becomes visibly the inverse of `belongsToProject`, and vice
   versa). Under `rdfs` and `none`, only the explicitly asserted direction resolves; the inferred
   reverse direction is empty.
3. **`owl:Thing` / `rdfs:Resource` leakage.** Under `owl`, `superClasses()` also yields `owl:Thing`
   and `rdfs:Resource` at the root of every hierarchy, and `domains()`/`ranges()` return the full
   superclass closure rather than just the most specific declared class. The `SnapshotMapper` (T7)
   is required to filter out the builtin `rdf:`/`rdfs:`/`owl:`/`xsd:` namespaces when computing
   `superTypes()`, and to compute domain/range ownership itself from most-specific declared
   `rdfs:domain`/`rdfs:range` rather than from Jena's closure-inclusive accessors — so that
   **property attribution (which type owns which property) is identical under all three reasoner
   settings, and only `superTypes()` and inverse/transitive visibility vary.** This is why the
   phase-1 decision record separates "type → properties" (computed by the adapter, reasoner-
   independent) from "type → superTypes" (delegated to Jena, reasoner-dependent).

Consistency checking (`asInferenceModel().validate()`) is available under all three settings but is
only meaningful for `owl`/`rdfs`; an inconsistent ontology causes `OntologyPort.snapshot()` and
`reload()` to throw `OntologyException` carrying an `OntologyReport` that names the offending
resource (for example `ex:Cyborg` in the `inconsistent.ttl` fixture), regardless of which reasoner
setting is active.
