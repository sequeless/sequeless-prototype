/**
 * Contract under test (lib/projections.ts): pure, read-only projections from a parsed `RdfStore`
 * into form-friendly view shapes, mirroring the OWL -> snapshot mapping table in
 * docs/architecture/sq-vocabulary.md. Every fact asserted below comes straight from
 * sequeless-spi-testkit/src/main/resources/ontology/reference.ttl (via `FIXTURE_TTL`, which only
 * appends a stray `rdfs:comment` outside the `sq:` vocabulary that no projection here touches).
 *
 * `RollupDerivationView.function`/`.filter` are read as the *local name* form of the asserted
 * `sq:function`/`sq:Criterion` terms (e.g. `'count'`, `"status ne 'done'"`), matching the
 * human-readable rollup-summary format `docs/architecture/derived-properties.md` documents for
 * `TypeResponseMapper`'s own summaries (`count(Task via belongsToProject where status ne 'done')`).
 * `overIri`/`viaIri`/`ofIri` stay full IRIs, per their `Iri`-suffixed names in `lib/model.ts`.
 */
import { Parser } from 'n3';
import { N3RdfStore } from './store';
import { readCardinality, readClasses, readProperties, readStateMachine } from './projections';
import type {
  AttributePropertyView,
  ClassView,
  PropertyView,
  RelationshipPropertyView,
  RollupDerivationView,
  StateMachineView,
  TransitionView,
} from './model';
import { FIXTURE_TTL } from '../testing/reference-ttl';

const EX_NS = 'https://sequeless.dev/ns/ref#';
const ex = (local: string) => `${EX_NS}${local}`;

function buildStore(): N3RdfStore {
  const quads = new Parser({ format: 'text/turtle' }).parse(FIXTURE_TTL);
  return new N3RdfStore(quads);
}

function classByIri(classes: ClassView[], iri: string): ClassView {
  const found = classes.find((c) => c.iri === iri);
  if (!found) {
    throw new Error(`no ClassView for ${iri}`);
  }
  return found;
}

function propertyByIri(properties: PropertyView[], iri: string): PropertyView {
  const found = properties.find((p) => p.iri === iri);
  if (!found) {
    throw new Error(`no PropertyView for ${iri}`);
  }
  return found;
}

function transitionNamed(machine: StateMachineView, name: string): TransitionView {
  const found = machine.transitions.find((t) => t.name === name);
  if (!found) {
    throw new Error(`no transition named ${name}`);
  }
  return found;
}

describe('readClasses', () => {
  const classes = readClasses(buildStore());

  it('projects every named owl:Class, sorted by IRI since none set sq:displayOrder', () => {
    expect(classes.map((c) => c.iri)).toEqual([
      ex('Deliverable'),
      ex('Person'),
      ex('Project'),
      ex('Task'),
      ex('WorkItem'),
    ]);
  });

  it('reads sq:label and sq:abstract for an abstract root class with no superclass', () => {
    const deliverable = classByIri(classes, ex('Deliverable'));
    expect(deliverable.label).toBe('Deliverable');
    expect(deliverable.abstract).toBe(true);
    expect(deliverable.superClassIris).toEqual([]);
  });

  it('reads a named superclass from rdfs:subClassOf, ignoring the restriction blank nodes', () => {
    const workItem = classByIri(classes, ex('WorkItem'));
    expect(workItem.superClassIris).toEqual([ex('Deliverable')]);
    expect(workItem.abstract).toBe(true);

    const task = classByIri(classes, ex('Task'));
    expect(task.superClassIris).toEqual([ex('WorkItem')]);
    expect(task.abstract).toBe(false);

    const project = classByIri(classes, ex('Project'));
    expect(project.superClassIris).toEqual([ex('WorkItem')]);
  });

  it('defaults abstract/superClassIris for a class with only restriction blank nodes', () => {
    const person = classByIri(classes, ex('Person'));
    expect(person.label).toBe('Person');
    expect(person.abstract).toBe(false);
    expect(person.superClassIris).toEqual([]);
  });

  it('defaults order, group and hidden since no class in the fixture sets those hints', () => {
    for (const view of classes) {
      expect(view.order).toBeNull();
      expect(view.group).toBeNull();
      expect(view.hidden).toBe(false);
    }
  });
});

describe('readProperties', () => {
  const properties = readProperties(buildStore());

  it('projects every owl:DatatypeProperty and owl:ObjectProperty, no more and no fewer', () => {
    expect(properties).toHaveLength(15);
    expect(properties.filter((p) => p.kind === 'attribute')).toHaveLength(11);
    expect(properties.filter((p) => p.kind === 'relationship')).toHaveLength(4);
  });

  it('projects a plain attribute property with its display hints', () => {
    const title = propertyByIri(properties, ex('title')) as AttributePropertyView;
    expect(title.kind).toBe('attribute');
    expect(title.label).toBe('Title');
    expect(title.domainIri).toBe(ex('Deliverable'));
    expect(title.datatype).toBe('STRING');
    expect(title.order).toBe(1);
    expect(title.group).toBe('General');
    expect(title.searchable).toBe(true);
    expect(title.facet).toBe(false);
    expect(title.indexed).toBe(false);
    expect(title.hidden).toBe(false);
    expect(title.readOnly).toBe(false);
    expect(title.derivation).toBeNull();
  });

  it('reads sq:facet and sq:indexed on status, domained on WorkItem', () => {
    const status = propertyByIri(properties, ex('status')) as AttributePropertyView;
    expect(status.facet).toBe(true);
    expect(status.indexed).toBe(true);
    expect(status.domainIri).toBe(ex('WorkItem'));
    expect(status.datatype).toBe('STRING');
  });

  it('maps rdfs:range xsd:integer to the INTEGER datatype and reads sq:displayGroup', () => {
    const priority = propertyByIri(properties, ex('priority')) as AttributePropertyView;
    expect(priority.datatype).toBe('INTEGER');
    expect(priority.domainIri).toBe(ex('Task'));
    expect(priority.group).toBe('Planning');
  });

  it('maps rdfs:range xsd:dateTime to DATE_TIME and reads sq:hidden/sq:readOnly', () => {
    const createdAt = propertyByIri(properties, ex('createdAt')) as AttributePropertyView;
    expect(createdAt.datatype).toBe('DATE_TIME');
    expect(createdAt.hidden).toBe(true);
    expect(createdAt.readOnly).toBe(true);
    expect(createdAt.derivation).toBeNull();
  });

  it('maps rdfs:range xsd:decimal and xsd:date to DECIMAL and DATE', () => {
    const estimatedHours = propertyByIri(properties, ex('estimatedHours')) as AttributePropertyView;
    const dueDate = propertyByIri(properties, ex('dueDate')) as AttributePropertyView;
    expect(estimatedHours.datatype).toBe('DECIMAL');
    expect(dueDate.datatype).toBe('DATE');
  });

  it('projects an owl:ObjectProperty as a relationship with its target type', () => {
    const assignedTo = propertyByIri(properties, ex('assignedTo')) as RelationshipPropertyView;
    expect(assignedTo.kind).toBe('relationship');
    expect(assignedTo.domainIri).toBe(ex('Task'));
    expect(assignedTo.targetTypeIri).toBe(ex('Person'));
    expect(assignedTo.facet).toBe(true);
    expect(assignedTo.inverseIri).toBeNull();
    expect(assignedTo.transitive).toBe(false);
  });

  it('reads owl:inverseOf as asserted in one direction only, without inferring the reverse', () => {
    const belongsToProject = propertyByIri(
      properties,
      ex('belongsToProject'),
    ) as RelationshipPropertyView;
    expect(belongsToProject.kind).toBe('relationship');
    expect(belongsToProject.targetTypeIri).toBe(ex('Project'));
    expect(belongsToProject.inverseIri).toBe(ex('hasTask'));

    const hasTask = propertyByIri(properties, ex('hasTask')) as RelationshipPropertyView;
    expect(hasTask.targetTypeIri).toBe(ex('Task'));
    expect(hasTask.inverseIri).toBeNull();
  });

  it('defaults transitive to false since no property is an owl:TransitiveProperty', () => {
    for (const view of properties) {
      if (view.kind === 'relationship') {
        expect(view.transitive).toBe(false);
      }
    }
  });

  it('forces readOnly true when sq:derivedBy is present, with no sq:readOnly asserted', () => {
    const openTaskCount = propertyByIri(properties, ex('openTaskCount')) as AttributePropertyView;
    expect(openTaskCount.readOnly).toBe(true);
    expect(openTaskCount.domainIri).toBe(ex('Project'));
    expect(openTaskCount.datatype).toBe('INTEGER');
  });

  it('reads a materialised sq:Rollup derivation with a single filter criterion', () => {
    const openTaskCount = propertyByIri(properties, ex('openTaskCount')) as AttributePropertyView;
    const derivation = openTaskCount.derivation as RollupDerivationView;
    expect(derivation.kind).toBe('rollup');
    expect(derivation.function).toBe('count');
    expect(derivation.overIri).toBe(ex('Task'));
    expect(derivation.viaIri).toBe(ex('belongsToProject'));
    expect(derivation.ofIri).toBeNull();
    expect(derivation.filter).toBe("status ne 'done'");
    expect(derivation.materialised).toBe(true);
  });

  it('reads a non-materialised sq:Rollup derivation with no filter', () => {
    const totalEstimatedHours = propertyByIri(
      properties,
      ex('totalEstimatedHours'),
    ) as AttributePropertyView;
    const derivation = totalEstimatedHours.derivation as RollupDerivationView;
    expect(derivation.kind).toBe('rollup');
    expect(derivation.function).toBe('sum');
    expect(derivation.overIri).toBe(ex('Task'));
    expect(derivation.viaIri).toBe(ex('belongsToProject'));
    expect(derivation.ofIri).toBe(ex('estimatedHours'));
    expect(derivation.filter).toBeNull();
    expect(derivation.materialised).toBe(false);
  });
});

describe('readCardinality', () => {
  it('reads a owl:maxCardinality-only restriction as {min: 0, max: 1}', () => {
    const store = buildStore();
    expect(readCardinality(store, ex('WorkItem'), ex('status'))).toEqual({ min: 0, max: 1 });
  });

  it('reads a owl:minCardinality-only restriction as {min: 1, max: null}', () => {
    const store = buildStore();
    expect(readCardinality(store, ex('Task'), ex('title'))).toEqual({ min: 1, max: null });
  });

  it('combines two separate restriction nodes for the same property into one Cardinality', () => {
    const store = buildStore();
    expect(readCardinality(store, ex('Person'), ex('name'))).toEqual({ min: 1, max: 1 });
  });

  it('defaults to {min: 0, max: null} when the type has no restriction for the property', () => {
    const store = buildStore();
    expect(readCardinality(store, ex('Task'), ex('description'))).toEqual({ min: 0, max: null });
  });

  it('reads the owl:maxCardinality-1 restrictions Project declares for two properties', () => {
    const store = buildStore();
    expect(readCardinality(store, ex('Project'), ex('owner'))).toEqual({ min: 0, max: 1 });
    expect(readCardinality(store, ex('Project'), ex('openTaskCount'))).toEqual({ min: 0, max: 1 });
  });
});

describe('readStateMachine', () => {
  it('returns null for a type with no sq:StateMachine', () => {
    expect(readStateMachine(buildStore(), ex('Task'))).toBeNull();
  });

  it('projects ex:ProjectLifecycle onto ex:Project with its initial state', () => {
    const machine = readStateMachine(buildStore(), ex('Project'));
    expect(machine).not.toBeNull();
    expect(machine?.iri).toBe(ex('ProjectLifecycle'));
    expect(machine?.appliesToIri).toBe(ex('Project'));
    expect(machine?.initialStateIri).toBe(ex('Draft'));
  });

  it("sorts states by sq:displayOrder and reads each one's label", () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    expect(machine.states.map((s) => s.iri)).toEqual([
      ex('Draft'),
      ex('Active'),
      ex('OnHold'),
      ex('Closed'),
    ]);
    expect(machine.states.map((s) => s.label)).toEqual(['Draft', 'Active', 'On Hold', 'Closed']);
    expect(machine.states.map((s) => s.order)).toEqual([1, 2, 3, 4]);
  });

  it('sorts transitions by name', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    expect(machine.transitions.map((t) => t.name)).toEqual([
      'activate',
      'autoClose',
      'close',
      'expireHold',
      'hold',
      'reopen',
      'resume',
    ]);
  });

  it('reads a USER_ACTION transition with a guard and guard message', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    const activate = transitionNamed(machine, 'activate');
    expect(activate.fromIri).toBe(ex('Draft'));
    expect(activate.toIri).toBe(ex('Active'));
    expect(activate.trigger).toEqual({ kind: 'USER_ACTION' });
    expect(activate.guard).toBe('self.owner != null');
    expect(activate.guardMessage).toBe('Project must have an owner before it can be activated');
    expect(activate.node.termType).toBe('BlankNode');
  });

  it('reads a USER_ACTION transition with no guard as null guard and guardMessage', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    const hold = transitionNamed(machine, 'hold');
    expect(hold.fromIri).toBe(ex('Active'));
    expect(hold.toIri).toBe(ex('OnHold'));
    expect(hold.trigger).toEqual({ kind: 'USER_ACTION' });
    expect(hold.guard).toBeNull();
    expect(hold.guardMessage).toBeNull();

    const resume = transitionNamed(machine, 'resume');
    expect(resume.fromIri).toBe(ex('OnHold'));
    expect(resume.toIri).toBe(ex('Active'));
    expect(resume.trigger).toEqual({ kind: 'USER_ACTION' });

    const close = transitionNamed(machine, 'close');
    expect(close.fromIri).toBe(ex('Active'));
    expect(close.toIri).toBe(ex('Closed'));
    expect(close.trigger).toEqual({ kind: 'USER_ACTION' });
  });

  it('reads an ON_CHANGE transition with its watched relationship and guard', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    const autoClose = transitionNamed(machine, 'autoClose');
    expect(autoClose.fromIri).toBe(ex('Active'));
    expect(autoClose.toIri).toBe(ex('Closed'));
    expect(autoClose.trigger).toEqual({ kind: 'ON_CHANGE', watchIris: [ex('belongsToProject')] });
    expect(autoClose.guard).toBe('self.openTaskCount == 0');
    expect(autoClose.guardMessage).toBe('Project still has open tasks');
  });

  it('reads a TIMER transition with its duration and no guard', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    const expireHold = transitionNamed(machine, 'expireHold');
    expect(expireHold.fromIri).toBe(ex('OnHold'));
    expect(expireHold.toIri).toBe(ex('Closed'));
    expect(expireHold.trigger).toEqual({ kind: 'TIMER', after: 'PT72H' });
    expect(expireHold.guard).toBeNull();
    expect(expireHold.guardMessage).toBeNull();
  });

  it('reads an EXTERNAL_SIGNAL transition with its signal name and no guard', () => {
    const machine = readStateMachine(buildStore(), ex('Project')) as StateMachineView;
    const reopen = transitionNamed(machine, 'reopen');
    expect(reopen.fromIri).toBe(ex('Closed'));
    expect(reopen.toIri).toBe(ex('Active'));
    expect(reopen.trigger).toEqual({ kind: 'EXTERNAL_SIGNAL', signalName: 'reopen' });
    expect(reopen.guard).toBeNull();
  });
});
