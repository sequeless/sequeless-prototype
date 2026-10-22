/**
 * Contract [OBJ-DETAIL-409] (lib/detail/object-detail.component.ts, `SqObjectDetail`):
 * - 409 stale-object on save opens the conflict dialog exactly once and never re-PUTs on its own.
 * - Dismissing that dialog leaves the user's edited values in the form.
 * - 409 transition-not-available refreshes the transitions list and opens no dialog.
 * - A successful save refetches the object (new ETag) rather than trusting the PUT response.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type {
  ApiError,
  BusinessObjectResponse,
  TransitionResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { provideUiKit } from '@sequeless/ui-kit';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { of, throwError } from 'rxjs';

import { OBJECT_SIGNALS_PANEL } from '../signals/object-signals';
import { SqObjectDetail } from './object-detail.component';

@Component({ selector: 'sq-stub-panel', template: 'stub' })
class StubPanel {}

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

const START: TransitionResponse = { name: 'close', available: true, reason: null };

function setup(dialogResult: unknown = undefined) {
  const objects = {
    get: vi.fn(() => of(objectBody(1))),
    update: vi.fn(() => of(objectBody(2, 'Closed'))),
    transitions: vi.fn(() => of([START])),
    fireTransition: vi.fn(() => of(objectBody(2))),
    delete: vi.fn(() => of(undefined)),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })) };

  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      provideRouter([]),
      { provide: ObjectsClient, useValue: objects },
      { provide: TypesClient, useValue: { describe: vi.fn(() => of(TYPE)) } },
      { provide: MatDialog, useValue: dialog },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ type: 'Project', id: OBJECT_ID })) },
      },
    ],
  });

  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(SqObjectDetail);
  return { fixture, component: fixture.componentInstance, objects, dialog, navigate };
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

  it('asks for confirmation before deleting', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const ctx = await loaded(setup());

    await ctx.component.delete();

    expect(confirm).toHaveBeenCalledWith('Delete this object?');
    expect(ctx.objects.delete).toHaveBeenCalledWith('Project', OBJECT_ID);
  });

  it('navigates up after a confirmed delete', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const ctx = await loaded(setup());

    await ctx.component.delete();

    expect(ctx.navigate).toHaveBeenCalledTimes(1);
    expect(ctx.navigate.mock.calls[0][0]).toEqual(['..']);
  });

  it('does nothing when the delete confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const ctx = await loaded(setup());

    await ctx.component.delete();

    expect(ctx.objects.delete).not.toHaveBeenCalled();
    expect(ctx.navigate).not.toHaveBeenCalled();
  });

  it('keeps the user on the page and surfaces the error when delete fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const ctx = await loaded(setup());
    const failure = problem('object-in-use');
    ctx.objects.delete.mockReturnValueOnce(throwError(() => failure));

    await ctx.component.delete();

    expect(ctx.navigate).not.toHaveBeenCalled();
    expect(ctx.component.store.error()).toBe(failure);
  });

  it('renders the signals panel once loaded', async () => {
    const ctx = await loaded(setup());
    ctx.fixture.detectChanges();

    const host: HTMLElement = ctx.fixture.nativeElement;
    expect(host.querySelectorAll('sq-object-signals')).toHaveLength(1);
  });

  it('renders the panel bound to OBJECT_SIGNALS_PANEL instead of the default', async () => {
    TestBed.overrideProvider(OBJECT_SIGNALS_PANEL, { useValue: StubPanel });
    const ctx = await loaded(setup());
    ctx.fixture.detectChanges();

    const host: HTMLElement = ctx.fixture.nativeElement;
    expect(host.querySelectorAll('sq-stub-panel')).toHaveLength(1);
    expect(host.querySelectorAll('sq-object-signals')).toHaveLength(0);
  });

  it('exposes the reason of an unavailable transition as its title', async () => {
    const setupResult = setup();
    setupResult.objects.transitions.mockReturnValue(
      of([{ name: 'close', available: false, reason: 'Guard failed' }]),
    );
    const ctx = await loaded(setupResult);
    ctx.fixture.detectChanges();

    const button: HTMLButtonElement | null = ctx.fixture.nativeElement.querySelector(
      '[data-transition="close"]',
    );
    expect(button?.disabled).toBe(true);
    expect(button?.title).toBe('Guard failed');
  });

  it('shows the 409 transition-not-available detail inline', async () => {
    const ctx = await loaded(setup());
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
});
