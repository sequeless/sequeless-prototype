/**
 * Contract [OBJ-DETAIL-409] (lib/detail/object-detail.component.ts, `SqObjectDetail`):
 * - 409 stale-object on save opens the conflict dialog exactly once and never re-PUTs on its own.
 * - Dismissing that dialog leaves the user's edited values in the form.
 * - 409 transition-not-available refreshes the transitions list and opens no dialog.
 * - A successful save refetches the object (new ETag) rather than trusting the PUT response.
 */
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { ApiError, BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { provideUiKit } from '@sequeless/ui-kit';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { of, throwError } from 'rxjs';

import { SqObjectDetail } from './object-detail.component';

const OBJECT_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';

const TYPE: TypeDetailResponse = PROJECT_TYPE_DETAIL;

function objectBody(version: number, status = 'Open'): BusinessObjectResponse {
  return {
    id: OBJECT_ID,
    type: 'Project',
    typeIri: TYPE.iri,
    version,
    state: 'Open',
    properties: { title: ['Apollo'], status },
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

const START = { name: 'close', available: true, reason: null };

function setup(dialogResult: unknown = undefined) {
  const objects = {
    get: vi.fn(() => of(objectBody(1))),
    update: vi.fn(() => of(objectBody(2, 'Closed'))),
    transitions: vi.fn(() => of([START])),
    fireTransition: vi.fn(() => of(objectBody(2))),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })) };

  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      { provide: ObjectsClient, useValue: objects },
      { provide: TypesClient, useValue: { describe: vi.fn(() => of(TYPE)) } },
      { provide: MatDialog, useValue: dialog },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ type: 'Project', id: OBJECT_ID })) },
      },
    ],
  });

  const fixture = TestBed.createComponent(SqObjectDetail);
  return { fixture, component: fixture.componentInstance, objects, dialog };
}

async function loaded(setupResult: ReturnType<typeof setup>) {
  setupResult.fixture.detectChanges();
  await setupResult.fixture.whenStable();
  // ngOnInit awaits load() and loadTransitions(); let those chains settle before asserting.
  await new Promise((resolve) => setTimeout(resolve));
  return setupResult;
}

describe('SqObjectDetail', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('on 409 stale-object opens the conflict dialog once and does not re-PUT on its own', async () => {
    const ctx = await loaded(setup());
    ctx.objects.update.mockReturnValueOnce(throwError(() => problem('stale-object')));

    await ctx.component.save();

    expect(ctx.dialog.open).toHaveBeenCalledTimes(1);
    expect(ctx.objects.update).toHaveBeenCalledTimes(1);
  });

  it('keeps the edited values in the form when the conflict dialog is dismissed', async () => {
    const ctx = await loaded(setup(undefined));
    ctx.objects.update.mockReturnValueOnce(throwError(() => problem('stale-object')));
    ctx.component.form().get('status')!.setValue('Closed');

    await ctx.component.save();

    expect(ctx.component.form().get('status')!.value).toBe('Closed');
  });

  it('on 409 transition-not-available refreshes transitions and opens no dialog', async () => {
    const ctx = await loaded(setup());
    ctx.objects.fireTransition.mockReturnValueOnce(
      throwError(() => problem('transition-not-available', { transitionName: 'close' })),
    );

    await ctx.component.fire('close');

    expect(ctx.objects.transitions).toHaveBeenCalledTimes(2);
    expect(ctx.dialog.open).not.toHaveBeenCalled();
  });

  it('on 409 stale-object from a transition refetches the object and transitions and opens no dialog', async () => {
    const ctx = await loaded(setup());
    const getBefore = ctx.objects.get.mock.calls.length;
    ctx.objects.fireTransition.mockReturnValueOnce(throwError(() => problem('stale-object')));

    await ctx.component.fire('close');

    expect(ctx.objects.get.mock.calls.length).toBe(getBefore + 1);
    expect(ctx.objects.transitions).toHaveBeenCalledTimes(2);
    expect(ctx.dialog.open).not.toHaveBeenCalled();
    expect(ctx.component.store.error()?.slug).toBe('stale-object');
  });

  it('refetches the object after a successful save instead of trusting the PUT body', async () => {
    const ctx = await loaded(setup());
    const getCallsBefore = ctx.objects.get.mock.calls.length;

    await ctx.component.save();

    expect(ctx.objects.get.mock.calls.length).toBe(getCallsBefore + 1);
    expect(ctx.dialog.open).not.toHaveBeenCalled();
  });
});
