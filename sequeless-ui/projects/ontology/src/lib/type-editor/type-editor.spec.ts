/*
 * Contract [ONT-TYPEEDITOR]: SqTypeEditor (`sq-type-editor`, route `:type`) resolves the route's type name (or IRI)
 * to a class IRI through TypesClient, ensures the session is loaded, and shows the import bar plus tabs
 * "Class" (SqClassForm, SqPropertyList and the 'type-extra' panels), "State machine" (SqStateMachineEditor) and
 * "Turtle" (SqRawTurtleTab). It uses the route-level ControlRegistry and clears it whenever the type changes or
 * the editor is destroyed. An unknown type and a failed lookup are reported in a banner.
 */
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { OntologyClient, TypesClient } from '@sequeless/core-api';
import type { TypeSummaryResponse } from '@sequeless/core-api';
import { ReplaySubject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { provideOntologyEditorPanel } from '../extension/editor-panels';
import type { OntologyEditorPanelContext } from '../extension/editor-panels';
import { ImportFlow } from '../import-result/import-flow';
import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { EX, FIXTURE_TTL, TASK } from '../testing/fixture';
import { SqTypeEditor } from './type-editor';

@Component({ selector: 'sq-extra', template: `<p class="extra">{{ context().typeIri }}</p>` })
class Extra {
  readonly context = input.required<OntologyEditorPanelContext>();
}

const TYPES: TypeSummaryResponse[] = [{ iri: TASK, name: 'Task', label: 'Task', abstract: false, superTypes: [] }];

async function setup(options: { type?: string; list?: () => ReturnType<TypesClient['list']>; panels?: boolean } = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      OntologySession,
      ControlRegistry,
      { provide: ImportFlow, useValue: { problems: signal(null), run: vi.fn() } },
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
      { provide: TypesClient, useValue: { list: options.list ?? (() => of(TYPES)) } },
      ...(options.panels ? [provideOntologyEditorPanel({ id: 'x', slot: 'type-extra', component: Extra })] : []),
    ],
  });
  const fixture = TestBed.createComponent(SqTypeEditor);
  fixture.componentRef.setInput('type', options.type ?? 'Task');
  fixture.detectChanges();
  await fixture.whenStable();
  await new Promise((r) => setTimeout(r));
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, registry: TestBed.inject(ControlRegistry) };
}

const tabLabels = (el: HTMLElement) => [...el.querySelectorAll('[role="tab"]')].map((t) => t.textContent?.trim());

describe('SqTypeEditor', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the three tabs and the import bar', async () => {
    const { el } = await setup();
    expect(tabLabels(el)).toEqual(['Class', 'State machine', 'Turtle']);
    expect(el.querySelector('sq-import-bar')).not.toBeNull();
  });

  it('shows the class form and property list on the first tab', async () => {
    const { el } = await setup();
    expect(el.querySelector('sq-class-form')).not.toBeNull();
    expect(el.querySelector('sq-property-list')).not.toBeNull();
  });

  it('resolves an IRI as well as a name', async () => {
    const { el } = await setup({ type: TASK });
    expect(el.querySelector('sq-class-form')).not.toBeNull();
  });

  it('renders type-extra panels with the type in their context', async () => {
    const { el } = await setup({ panels: true });
    expect(el.querySelector('.extra')?.textContent).toBe(TASK);
  });

  it('reports an unknown type', async () => {
    const { el } = await setup({ type: 'Nope' });
    expect(el.textContent).toContain('Nope');
    expect(el.querySelector('sq-class-form')).toBeNull();
  });

  it('reports a failed type lookup in the banner', async () => {
    const error = { status: 500, kind: 'problem', title: 'Lookup failed', detail: null, violations: [], issues: [] };
    const { el } = await setup({ list: () => throwError(() => error) });
    expect(el.querySelector('sq-problem-banner')?.textContent).toContain('Lookup failed');
  });

  it('clears the control registry when the type input changes and on destroy', async () => {
    const { fixture, registry } = await setup();
    const clear = vi.spyOn(registry, 'clear');
    fixture.componentRef.setInput('type', `${EX}Other`);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(clear).toHaveBeenCalled();
    clear.mockClear();
    fixture.destroy();
    expect(clear).toHaveBeenCalled();
  });

  it('waits for the lookup before rendering the forms', async () => {
    const pending = new ReplaySubject<TypeSummaryResponse[]>(1);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        OntologySession,
        ControlRegistry,
        { provide: ImportFlow, useValue: { problems: signal(null), run: vi.fn() } },
        { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
        { provide: TypesClient, useValue: { list: () => pending } },
      ],
    });
    const fixture = TestBed.createComponent(SqTypeEditor);
    fixture.componentRef.setInput('type', 'Task');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('sq-class-form')).toBeNull();
    pending.next(TYPES);
    pending.complete();
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('sq-class-form')).not.toBeNull();
  });
});
