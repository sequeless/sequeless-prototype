/*
 * Contract [ONT-CLASS]: SqClassForm (`sq-class-form`, input `typeIri`) edits a class's label through
 * relabel (applied on `change`, ignored while empty), shows abstract and super-types read-only (D10), and
 * registers its label control with the ControlRegistry under the class IRI (unregistered on destroy).
 */
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { OntologyClient } from '@sequeless/core-api';
import { readClasses } from '@sequeless/rdf';
import { of } from 'rxjs';

import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { FIXTURE_TTL, TASK } from '../testing/fixture';
import { SqClassForm } from './class-form';

@Component({ imports: [SqClassForm], template: `<sq-class-form [typeIri]="iri()" />` })
class Host {
  readonly iri = input(TASK);
}

async function setup(ttl = FIXTURE_TTL) {
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      OntologySession,
      ControlRegistry,
      { provide: OntologyClient, useValue: { load: () => of(ttl) } },
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const input = el.querySelector('[data-field="label"]') as HTMLInputElement;
  const edit = (value: string) => {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
  };
  return { fixture, el, session, input, edit, registry: TestBed.inject(ControlRegistry) };
}

const labelOf = (session: OntologySession) => readClasses(session.store()!).find((c) => c.iri === TASK)?.label;

describe('SqClassForm', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the label', async () => {
    const { input } = await setup();
    expect(input.value).toBe('Task');
  });

  it('relabels on change', async () => {
    const { edit, session } = await setup();
    edit('Ticket');
    expect(labelOf(session)).toBe('Ticket');
    expect(session.revision()).toBe(1);
  });

  it('ignores an empty label', async () => {
    const { edit, session } = await setup();
    edit('');
    expect(session.revision()).toBe(0);
  });

  it('ignores an unchanged label', async () => {
    const { edit, session } = await setup();
    edit('Task');
    expect(session.revision()).toBe(0);
  });

  it('shows abstract and super-types read-only', async () => {
    const ttl = `${FIXTURE_TTL}
@prefix sq2: <https://sequeless.dev/ns/meta#> .
<http://ex.org/Base> a <http://www.w3.org/2002/07/owl#Class> ; sq2:abstract true .
<http://ex.org/Task> <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://ex.org/Base> .`;
    const { el } = await setup(ttl);
    expect(el.textContent).toContain('http://ex.org/Base');
    expect(el.querySelector('[data-field="abstract"]')?.textContent).toContain('No');
    expect(el.querySelectorAll('input')).toHaveLength(1);
  });

  it('follows the store on undo', async () => {
    const { edit, session, fixture, input } = await setup();
    edit('Ticket');
    session.undo();
    fixture.detectChanges();
    expect(input.value).toBe('Task');
  });

  it('registers the label control and unregisters it on destroy', async () => {
    const { registry, fixture, el } = await setup();
    const issue = { severity: 'error', subjectIri: TASK, message: 'taken' };
    registry.routeIssues([issue]);
    fixture.detectChanges();
    expect(el.querySelector('.sq-server-error')?.textContent).toContain('taken');
    fixture.destroy();
    expect(registry.routeIssues([issue]).matchedFirst).toBeNull();
  });
});
