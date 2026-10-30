/**
 * Contract [AUT-ROUTES] (lib/automation.routes.ts, `automationRoutes`): '' lands on the type list
 * (SqAutomationTypeList) and `:type` on the state-machine view (SqStateMachineView) for that type.
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TypesClient } from '@sequeless/core-api';
import { of } from 'rxjs';

import { automationRoutes } from './automation.routes';
import { SqAutomationTypeList } from './state-machine-view/type-list';
import { SqStateMachineView } from './state-machine-view/state-machine-view';

const describeFn = vi.fn((name: string) =>
  of({ iri: `ex:${name}`, name, label: name, abstract: false, superTypes: [], group: null, hidden: false, properties: [], stateMachine: null }),
);

describe('automationRoutes', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter(automationRoutes),
        provideNoopAnimations(),
        { provide: TypesClient, useValue: { list: () => of([]), describe: describeFn } },
      ],
    }),
  );
  afterEach(() => TestBed.resetTestingModule());

  it("lands on the type list at ''", async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', SqAutomationTypeList);
  });

  it('shows the state-machine view for :type', async () => {
    const harness = await RouterTestingHarness.create();
    const view = await harness.navigateByUrl('/Order', SqStateMachineView);
    await harness.fixture.whenStable();
    expect(view).toBeInstanceOf(SqStateMachineView);
    expect(describeFn).toHaveBeenCalledWith('Order');
  });
});
