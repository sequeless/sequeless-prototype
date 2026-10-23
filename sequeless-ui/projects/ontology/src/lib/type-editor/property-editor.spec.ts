/*
 * Contract [ONT-PROPFORM] (component): SqPropertyEditor (`sq-property-editor`, input `property`) edits one
 * property of the session's store. Changing a field and firing `change` applies one merged delta to the
 * session (a single undo step); an invalid form applies nothing. Its label control is registered with the
 * ControlRegistry under the property IRI, so a routed 422 issue shows as a server error; it is
 * unregistered on destroy. The form follows the store when it changes underneath it (undo).
 */
import { Component, computed, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { OntologyClient } from '@sequeless/core-api';
import { readProperties } from '@sequeless/rdf';
import { of } from 'rxjs';

import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { FIXTURE_TTL, SCORE, TITLE } from '../testing/fixture';
import { SqPropertyEditor } from './property-editor';

@Component({
  imports: [SqPropertyEditor],
  template: `@if (property(); as p) { <sq-property-editor [property]="p" /> }`,
})
class Host {
  iri = TITLE;
  private readonly session = inject(OntologySession);
  readonly property = computed(() => {
    this.session.revision();
    const store = this.session.store();
    return store ? readProperties(store).find((p) => p.iri === this.iri) : undefined;
  });
}

async function setup(iri = TITLE) {
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      OntologySession,
      ControlRegistry,
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  const fixture = TestBed.createComponent(Host);
  fixture.componentInstance.iri = iri;
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const field = (name: string) => el.querySelector(`[data-field="${name}"]`) as HTMLInputElement;
  const edit = (name: string, value: string) => {
    const input = field(name);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
  };
  return { fixture, el, session, registry: TestBed.inject(ControlRegistry), field, edit };
}

const labelOf = (session: OntologySession, iri: string) =>
  readProperties(session.store()!).find((p) => p.iri === iri)?.label;

describe('SqPropertyEditor', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the current values and the read-only facts', async () => {
    const { field, el } = await setup();
    expect(field('label').value).toBe('Title');
    expect(field('min').value).toBe('1');
    expect(field('max').value).toBe('');
    expect(el.textContent).toContain('STRING');
  });

  it('applies a label change as one delta', async () => {
    const { edit, session } = await setup();
    edit('label', 'Heading');
    expect(labelOf(session, TITLE)).toBe('Heading');
    expect(session.revision()).toBe(1);
  });

  it('journals each committed edit as its own undo step', async () => {
    const { edit, session } = await setup();
    edit('label', 'Heading');
    edit('max', '3');
    expect(session.revision()).toBe(2);
    session.undo();
    expect(labelOf(session, TITLE)).toBe('Heading');
    session.undo();
    expect(labelOf(session, TITLE)).toBe('Title');
  });

  it('applies nothing while the form is invalid', async () => {
    const { edit, session } = await setup();
    edit('label', '');
    expect(session.revision()).toBe(0);
  });

  it('follows the store on undo', async () => {
    const { edit, session, fixture, field } = await setup();
    edit('label', 'Heading');
    session.undo();
    fixture.detectChanges();
    expect(field('label').value).toBe('Title');
  });

  it('shows a routed server error on the label', async () => {
    const { registry, fixture, el } = await setup();
    registry.routeIssues([{ severity: 'error', subjectIri: TITLE, message: 'duplicate label' }]);
    fixture.detectChanges();
    expect(el.querySelector('.sq-server-error')?.textContent).toContain('duplicate label');
  });

  it('unregisters its controls on destroy', async () => {
    const { registry, fixture } = await setup();
    fixture.destroy();
    expect(registry.routeIssues([{ severity: 'error', subjectIri: TITLE, message: 'x' }]).matchedFirst).toBeNull();
  });

  it('offers a plugin name for plugin-derived properties and lets it be cleared', async () => {
    const { field, edit, session } = await setup(SCORE);
    expect(field('pluginName').value).toBe('scorer');
    edit('derivationKind', 'none');
    expect(readProperties(session.store()!).find((p) => p.iri === SCORE)?.derivation).toBeNull();
  });
});
