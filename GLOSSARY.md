# Sequeless

Sequeless keeps the state of ontology-typed objects, answers queries over that state, and reacts to data and events through automation that turns them into commands and side effects.

## Language

### Ontology and what is compiled from it

**Ontology**:
The authored OWL and SHACL source that defines a tenant's types, their rules and their automation.
_Avoid_: schema, model (when meaning the authored source)

**Ontology version**:
One numbered activation of a tenant's Ontology. The Schema and Behaviour compiled from it carry its number.
_Avoid_: revision, snapshot version

**Schema**:
The object types compiled from the active Ontology, with their properties, relationships and their query and display hints.
_Avoid_: meta-model, snapshot, compiled ontology

**Behaviour**:
The state machines, derivations and automation definitions compiled from the active Ontology.
_Avoid_: rules, workflow, meta-model

### Reading and changing state

**Browse**:
Asking for the set of objects of a type that match a filter, along with their facets, sort order and page.
_Avoid_: list, search (when meaning the whole operation)

**Read**:
Asking for one object by its identity.
_Avoid_: get, fetch

**Field**:
A property reached from an object type by a path, together with what the Query side lets you do with it: filter on it, facet it, search it or sort by it.
_Avoid_: facet (when meaning something you can query), queryable property

**Facet**:
The breakdown of one Field's values, with a count for each, across the objects that match a Browse.
_Avoid_: aggregation, bucket list

**Facet bucket**:
One value of a Facet and the number of matching objects that have it.
_Avoid_: facet value, count

**Object view**:
What a Browse or a Read returns for one object: its identity, type, property values and version, as the Query side presents them.
_Avoid_: business object (when meaning what a Read returns), record, row

**Store**:
A place where Sequeless keeps the state of objects. Only Store Commands change it. The Query side reads it but never changes it.
_Avoid_: database, persistence

**Query side**:
Everything that answers a Browse or a Read over a Store. It never changes a Store.
_Avoid_: read model (unless meaning a stored projection), BR

**Command**:
A request sent to one target, asking it to do something; the target may accept or reject it. Each kind of Command is named after its target, such as a Store Command or a Command to a tasking service to mark a task done.
_Avoid_: message, request (when meaning a Command)

**Store Command**:
A Command to a Store: add, edit or delete an object, or fire a transition. It is the only way a Store changes.
_Avoid_: mutation, write, update request

**Command side**:
Everything that accepts Store Commands and changes a Store. A Store has a Command side and a Query side.
_Avoid_: Store Command side, write model, EAD

### Automation

**Observable**:
A named stream of Domain events that all share one Event type, produced by an Integration flow, by a Store (its Store events), or by combining other Observables.
_Avoid_: stream, feed, topic

**Domain event**:
A record of something that happened, shaped by an Event type. Every item in an Observable is a Domain event.
_Avoid_: message

**Store event**:
The kind of Domain event a Store produces when it accepts a Store Command: an object created, edited or deleted, or a transition fired. It carries the Ontology version it was committed under.
_Avoid_: fact, change event

**Event type**:
The ontology-defined shape that every event in an Observable conforms to.
_Avoid_: event schema, message type

**Source**:
Something outside Sequeless that an Integration flow connects to, such as a webhook sender, a polled API or another database.
_Avoid_: event source, data source

**Integration flow**:
A definition that connects to something outside Sequeless and turns what it receives into an Observable.
_Avoid_: connector, pipeline, sync

**Side effect**:
An action automation performs in reaction to an event: sending a Command to a Store or to something outside Sequeless, such as a webhook or a remote service. Store events are not Side effects; they feed Observables.
_Avoid_: action, external call
