/*
 * Contract [ONT-PROPLIST]: SqPropertyList (`sq-property-list`, input `typeIri`) renders one sq-property-editor
 * per property whose domain is the type, ordered as readProperties orders them. "Add attribute" and "Add
 * relationship" open the add dialog and apply addAttributeProperty / addRelationshipProperty to the session.
 * Cancelling the dialog changes nothing.
 */
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { OntologyClient } from '@sequeless/core-api';
import { readProperties } from '@sequeless/rdf';
import { of } from 'rxjs';

import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { EX, FIXTURE_TTL, TASK } from '../testing/fixture';
import { SqPropertyList } from './property-list';

@Component({ imports: [SqPropertyList], template: `<sq-property-list [typeIri]="iri()" />` })
class Host {
  readonly iri = signal(TASK);
}

async function setup(dialogResult: unknown = undefined) {
  const open = vi.fn(() => ({ afterClosed: () => of(dialogResult) }));
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      OntologySession,
      ControlRegistry,
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
      { provide: MatDialog, useValue: { open } },
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const click = async (action: string) => {
    (el.querySelector(`[data-action="${action}"]`) as HTMLButtonElement).click();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  };
  return { fixture, el, session, open, click };
}

describe('SqPropertyList', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders an editor per property of the type, in order', async () => {
    const { el } = await setup();
    const labels = [...el.querySelectorAll('sq-property-editor [data-field="label"]')].map(
      (i) => (i as HTMLInputElement).value,
    );
    expect(labels).toEqual(['Title', 'Score', 'Total', 'Parent']);
  });

  it('renders nothing for a type with no properties', async () => {
    const { fixture, el } = await setup();
    fixture.componentInstance.iri.set(`${EX}Nothing`);
    fixture.detectChanges();
    expect(el.querySelectorAll('sq-property-editor')).toHaveLength(0);
  });

  it('adds an attribute through the dialog', async () => {
    const spec = { iri: `${EX}due`, domainIri: TASK, datatype: 'DATE', label: 'Due' };
    const { click, session, open, fixture, el } = await setup(spec);
    await click('add-attribute');
    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        data: expect.objectContaining({ kind: 'attribute', typeIri: TASK }),
      }),
    );
    const added = readProperties(session.store()!).find((p) => p.iri === spec.iri);
    expect(added).toMatchObject({ kind: 'attribute', label: 'Due', datatype: 'DATE' });
    fixture.detectChanges();
    expect(el.querySelectorAll('sq-property-editor')).toHaveLength(5);
  });

  it('adds a relationship through the dialog, offering the classes as targets', async () => {
    const spec = { iri: `${EX}owner`, domainIri: TASK, targetTypeIri: TASK };
    const { click, session, open } = await setup(spec);
    await click('add-relationship');
    const data = (open.mock.calls[0] as unknown as [unknown, { data: { targets: { iri: string }[]; existingIris: string[] } }])[1].data;
    expect(data.targets.map((t) => t.iri)).toContain(TASK);
    expect(data.existingIris).toContain(`${EX}title`);
    expect(readProperties(session.store()!).find((p) => p.iri === spec.iri)).toMatchObject({
      kind: 'relationship',
      targetTypeIri: TASK,
    });
  });

  it('changes nothing when the dialog is cancelled', async () => {
    const { click, session } = await setup(undefined);
    await click('add-attribute');
    expect(session.revision()).toBe(0);
  });
});
