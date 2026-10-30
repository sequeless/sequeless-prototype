/**
 * Contract [OBJ-TRANSITIONS] (lib/detail/transitions-panel.ts, `SqTransitionsPanel`):
 * - Lists the object's transitions as buttons; an unavailable one is disabled, its reason the title.
 * - A successful fire refetches the object and reloads the list.
 * - 409 transition-not-available reloads the list and shows the detail inline, with no dialog.
 * - 409 stale-object refetches the object and the list and reports the error, without retrying.
 */
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import type {
  ApiError,
  BusinessObjectResponse,
  TransitionResponse,
} from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { of, throwError } from 'rxjs';

import { ObjectDetailStore } from './object-detail.store';
import { SqTransitionsPanel } from './transitions-panel';

const OBJECT_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';

function objectBody(version: number): BusinessObjectResponse {
  return {
    id: OBJECT_ID,
    type: 'Project',
    typeIri: PROJECT_TYPE_DETAIL.iri,
    version,
    state: 'Open',
    properties: { title: ['Apollo'], status: 'Open' },
    audit: {
      createdAt: '2026-09-01T10:00:00Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-02T10:00:00Z',
      updatedBy: 'anonymous',
    },
  };
}

function problem(slug: string, extra: Partial<ApiError> = {}): ApiError {
  return {
    status: 409,
    kind: 'problem',
    title: 'Conflict',
    detail: null,
    slug,
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: null,
    ...extra,
  };
}

const CLOSE: TransitionResponse = { name: 'close', available: true, reason: null };

async function setup(transitions: readonly TransitionResponse[] = [CLOSE]) {
  const objects = {
    get: vi.fn(() => of(objectBody(1))),
    transitions: vi.fn(() => of(transitions)),
    fireTransition: vi.fn(() => of(objectBody(2))),
  };
  const dialog = { open: vi.fn() };

  TestBed.configureTestingModule({
    providers: [
      ObjectDetailStore,
      { provide: ObjectsClient, useValue: objects },
      { provide: TypesClient, useValue: { describe: vi.fn(() => of(PROJECT_TYPE_DETAIL)) } },
      { provide: MatDialog, useValue: dialog },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ type: 'Project', id: OBJECT_ID })) },
      },
    ],
  });

  const store = TestBed.inject(ObjectDetailStore);
  await store.load('Project', OBJECT_ID);
  const fixture = TestBed.createComponent(SqTransitionsPanel);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, objects, dialog, store };
}

describe('SqTransitionsPanel', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('lists transitions as buttons', async () => {
    const ctx = await setup();

    const button: HTMLButtonElement | null = ctx.fixture.nativeElement.querySelector(
      '[data-transition="close"]',
    );
    expect(button?.disabled).toBe(false);
  });

  it('exposes the reason of an unavailable transition as its title', async () => {
    const ctx = await setup([{ name: 'close', available: false, reason: 'Guard failed' }]);

    const button: HTMLButtonElement | null = ctx.fixture.nativeElement.querySelector(
      '[data-transition="close"]',
    );
    expect(button?.disabled).toBe(true);
    expect(button?.title).toBe('Guard failed');
  });

  it('refetches the object and reloads the list after a successful fire', async () => {
    const ctx = await setup();
    const getBefore = ctx.objects.get.mock.calls.length;

    await ctx.component.fire('close');

    expect(ctx.objects.fireTransition).toHaveBeenCalledWith('Project', OBJECT_ID, 'close');
    expect(ctx.objects.get.mock.calls.length).toBe(getBefore + 1);
    expect(ctx.objects.transitions).toHaveBeenCalledTimes(2);
  });

  it('on 409 transition-not-available reloads the list and opens no dialog', async () => {
    const ctx = await setup();
    ctx.objects.fireTransition.mockReturnValueOnce(
      throwError(() => problem('transition-not-available', { transitionName: 'close' })),
    );

    await ctx.component.fire('close');

    expect(ctx.objects.transitions).toHaveBeenCalledTimes(2);
    expect(ctx.dialog.open).not.toHaveBeenCalled();
  });

  it('shows the 409 transition-not-available detail inline', async () => {
    const ctx = await setup();
    ctx.objects.fireTransition.mockReturnValueOnce(
      throwError(() =>
        problem('transition-not-available', {
          transitionName: 'close',
          detail: 'Cannot close from Draft',
        }),
      ),
    );

    await ctx.component.fire('close');
    ctx.fixture.detectChanges();

    const inline: HTMLElement | null = ctx.fixture.nativeElement.querySelector(
      '[data-transition-error]',
    );
    expect(inline?.textContent).toContain('Cannot close from Draft');
  });

  it('on 409 stale-object refetches the object and the list and reports the error', async () => {
    const ctx = await setup();
    const getBefore = ctx.objects.get.mock.calls.length;
    ctx.objects.fireTransition.mockReturnValueOnce(throwError(() => problem('stale-object')));

    await ctx.component.fire('close');

    expect(ctx.objects.fireTransition).toHaveBeenCalledTimes(1);
    expect(ctx.objects.get.mock.calls.length).toBe(getBefore + 1);
    expect(ctx.objects.transitions).toHaveBeenCalledTimes(2);
    expect(ctx.dialog.open).not.toHaveBeenCalled();
    expect(ctx.store.error()?.slug).toBe('stale-object');
  });
});
