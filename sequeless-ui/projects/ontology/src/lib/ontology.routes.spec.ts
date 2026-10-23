/**
 * Contract [ONT-ROUTES] (lib/ontology.routes.ts, `ontologyRoutes`): '' lands on the type list, `raw` on the raw
 * Turtle page (never captured by `:type`), `:type` on the type editor. One OntologySession, ControlRegistry and
 * ImportFlow are shared by all children. The ONTOLOGY_EDITOR_PANELS extension point accepts a stub panel.
 */
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OntologyClient, TypesClient, TURTLE_EDITOR } from '@sequeless/core-api';
import { of } from 'rxjs';

import { provideOntologyEditorPanel } from './extension/editor-panels';
import type { OntologyEditorPanelContext } from './extension/editor-panels';
import { ControlRegistry } from './issues/control-registry';
import { ontologyRoutes } from './ontology.routes';
import { SqRawTurtlePage } from './raw/raw-turtle-page';
import { OntologySession } from './session/ontology-session';
import { FIXTURE_TTL, TASK } from './testing/fixture';
import { SqTypeEditor } from './type-editor/type-editor';
import { SqOntologyTypeList } from './type-list/type-list';

const noopEditor = () => ({
  mount: () => Promise.resolve(),
  getValue: () => '',
  setValue: () => undefined,
  setDiagnostics: () => undefined,
  dispose: () => undefined,
});

@Component({ selector: 'sq-stub-extra', template: `<p class="stub-extra">{{ context().typeIri }}</p>` })
class StubExtra {
  readonly context = input.required<OntologyEditorPanelContext>();
}

function configure(extra: unknown[] = []) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter(ontologyRoutes),
      provideNoopAnimations(),
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
      {
        provide: TypesClient,
        useValue: { list: () => of([{ iri: TASK, name: 'Task', label: 'Task', abstract: false, superTypes: [] }]) },
      },
      { provide: TURTLE_EDITOR, useValue: noopEditor },
      { provide: MatDialog, useValue: { open: vi.fn() } },
      ...(extra as never[]),
    ],
  });
}

describe('ontologyRoutes', () => {
  afterEach(() => TestBed.resetTestingModule());

  it("lands '' on the type list", async () => {
    configure();
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/', SqOntologyTypeList)).toBeInstanceOf(SqOntologyTypeList);
  });

  it('lands raw on the raw Turtle page, not the type editor', async () => {
    configure();
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/raw', SqRawTurtlePage)).toBeInstanceOf(SqRawTurtlePage);
  });

  it('lands :type on the type editor', async () => {
    configure();
    const harness = await RouterTestingHarness.create();
    const editor = await harness.navigateByUrl('/Task', SqTypeEditor);
    expect(editor).toBeInstanceOf(SqTypeEditor);
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('sq-class-form')).not.toBeNull();
  });

  it('shares one session, registry and import flow across children', async () => {
    configure();
    const harness = await RouterTestingHarness.create();
    const list = await harness.navigateByUrl('/', SqOntologyTypeList);
    await harness.fixture.whenStable();
    const session = (list as unknown as { session: OntologySession }).session;
    expect(session.store()).not.toBeNull();
    const editor = await harness.navigateByUrl('/Task', SqTypeEditor);
    const injector = (editor as unknown as { registry: ControlRegistry; session: OntologySession });
    expect(injector.session).toBe(session);
    expect(injector.registry).toBeInstanceOf(ControlRegistry);
  });

  it('renders panels contributed through the extension point', async () => {
    configure([provideOntologyEditorPanel({ id: 'extra', slot: 'type-extra', component: StubExtra })]);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/Task', SqTypeEditor);
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('.stub-extra')?.textContent).toBe(TASK);
  });
});
