/**
 * Contract [SHELL-AUTOMATION] (app.routes.ts, `routes`): the shell composes automation into the other features
 * lazily, so none of it is in the initial bundle. Loading the `ontology` route contributes automation's trigger and
 * guard editors to ONTOLOGY_EDITOR_PANELS; loading the `objects` route binds OBJECT_SIGNALS_PANEL to automation's
 * signals panel. Each is provided on that route only; `automation` still loads `automationRoutes`.
 */
import { EnvironmentInjector, Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Route, Routes } from '@angular/router';
import { SqAutomationSignalsPanel, SqGuardEditor, SqTriggerEditor, automationRoutes } from '@sequeless/automation';
import { OBJECT_SIGNALS_PANEL } from '@sequeless/objects';
import { ONTOLOGY_EDITOR_PANELS, panelsFor } from '@sequeless/ontology';
import { createEnvironmentInjector } from '@angular/core';

import { routes } from './app.routes';

async function load(path: string): Promise<Routes> {
  const route = routes.find((r) => r.path === path) as Route;
  return (await runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
    (route.loadChildren as () => Promise<Routes>)(),
  )) as Routes;
}

function routeInjector(loaded: Routes): Injector {
  return createEnvironmentInjector(loaded[0].providers ?? [], TestBed.inject(EnvironmentInjector));
}

describe('routes [SHELL-AUTOMATION]', () => {
  it('contributes the automation editors on the ontology route', async () => {
    const injector = routeInjector(await load('ontology'));
    const all = injector.get(ONTOLOGY_EDITOR_PANELS);
    expect(panelsFor(all, 'transition-trigger').map((p) => p.component)).toEqual([SqTriggerEditor]);
    expect(panelsFor(all, 'transition-guard').map((p) => p.component)).toEqual([SqGuardEditor]);
    expect(TestBed.inject(ONTOLOGY_EDITOR_PANELS)).toEqual([]);
  });

  it('binds the automation signals panel on the objects route', async () => {
    const injector = routeInjector(await load('objects'));
    expect(injector.get(OBJECT_SIGNALS_PANEL)).toBe(SqAutomationSignalsPanel);
    expect(TestBed.inject(OBJECT_SIGNALS_PANEL)).not.toBe(SqAutomationSignalsPanel);
  });

  it('still loads the automation routes', async () => {
    expect(await load('automation')).toBe(automationRoutes);
  });
});
