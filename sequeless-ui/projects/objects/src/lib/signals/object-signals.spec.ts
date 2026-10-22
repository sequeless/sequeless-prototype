/**
 * Contract [OBJ-SIGNALS] (lib/signals/object-signals.ts, `SqObjectSignals`): offers one button per
 * EXTERNAL_SIGNAL transition that applies from the object's current state. Firing posts the signal
 * with no body and only ever reports it as dispatched: the server handles signals asynchronously,
 * so the panel never claims the transition happened.
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type {
  ApiError,
  BusinessObjectResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { ObjectsClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { provideUiKit } from '@sequeless/ui-kit';
import { of, throwError } from 'rxjs';

import { SqObjectSignals } from './object-signals';

const OBJECT_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';

const TYPE: TypeDetailResponse = {
  ...PROJECT_TYPE_DETAIL,
  stateMachine: {
    iri: `${PROJECT_TYPE_DETAIL.iri}#lifecycle`,
    states: [],
    initialState: 'Open',
    transitions: [
      { name: 'approve', from: 'Open', to: 'Closed', hasGuard: false, trigger: 'EXTERNAL_SIGNAL', after: null, signalName: 'approved', watch: [] },
      { name: 'reopen', from: 'Closed', to: 'Open', hasGuard: false, trigger: 'EXTERNAL_SIGNAL', after: null, signalName: 'reopened', watch: [] },
      { name: 'close', from: 'Open', to: 'Closed', hasGuard: false, trigger: 'USER_ACTION', after: null, signalName: null, watch: [] },
    ],
  },
};

const OBJECT: BusinessObjectResponse = {
  id: OBJECT_ID,
  type: 'Project',
  typeIri: TYPE.iri,
  version: 1,
  state: 'Open',
  properties: {},
  audit: {
    createdAt: '2026-09-01T10:00:00Z',
    createdBy: 'anonymous',
    updatedAt: '2026-09-01T10:00:00Z',
    updatedBy: 'anonymous',
  },
};

function setup() {
  const objects = { signal: vi.fn(() => of(undefined)) };

  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      { provide: ObjectsClient, useValue: objects },
    ],
  });

  const fixture = TestBed.createComponent(SqObjectSignals);
  fixture.componentRef.setInput('type', TYPE);
  fixture.componentRef.setInput('object', OBJECT);
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, objects };
}

async function click(ctx: ReturnType<typeof setup>, name: string) {
  (ctx.host.querySelector(`[data-signal="${name}"]`) as HTMLButtonElement).click();
  await ctx.fixture.whenStable();
  ctx.fixture.detectChanges();
}

describe('SqObjectSignals', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders one button per EXTERNAL_SIGNAL that applies from the current state', () => {
    const ctx = setup();

    const names = [...ctx.host.querySelectorAll('[data-signal]')].map((el) =>
      el.getAttribute('data-signal'),
    );

    expect(names).toEqual(['approved']);
  });

  it('on 202 reports the signal as dispatched without claiming the transition completed', async () => {
    const ctx = setup();

    await click(ctx, 'approved');

    const text = ctx.host.textContent ?? '';
    expect(text).toContain('dispatched');
    expect(text).not.toMatch(/completed|transitioned|now closed/i);
  });

  it('posts the signal with no body, addressed by type, id and name', async () => {
    const ctx = setup();

    await click(ctx, 'approved');

    expect(ctx.objects.signal).toHaveBeenCalledWith('Project', OBJECT_ID, 'approved');
  });

  it('on failure reports the error and claims nothing was dispatched', async () => {
    const ctx = setup();
    const failure: ApiError = {
      status: 500,
      kind: 'unknown',
      title: 'Server error',
      detail: null,
      slug: null,
      violations: [],
      issues: [],
      expectedVersion: null,
      transitionName: null,
      typeIri: null,
      signalName: null,
      raw: null,
    };
    ctx.objects.signal.mockReturnValueOnce(throwError(() => failure));

    await click(ctx, 'approved');

    expect(ctx.host.textContent ?? '').not.toContain('dispatched');
    expect(ctx.fixture.componentInstance.error()?.status).toBe(500);
  });
});
