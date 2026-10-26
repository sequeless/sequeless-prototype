/**
 * Contract [OBJ-DETAIL-409] (lib/detail/object-detail.component.ts, `SqObjectDetail`):
 * - 409 stale-object on save opens the conflict dialog exactly once and never re-PUTs on its own.
 * - Dismissing that dialog leaves the user's edited values in the form.
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
  // The transitions panel is created once the object loads, and its buttons render only after its own async load.
  for (let attempt = 0; attempt < 50; attempt++) {
    if (setupResult.fixture.nativeElement.querySelector('[data-transition]')) {
      return setupResult;
    }
    await new Promise((resolve) => setTimeout(resolve));
    setupResult.fixture.detectChanges();
  }
  throw new Error('transitions panel never rendered a button');
}

function queryButton(ctx: ReturnType<typeof setup>, selector: string): HTMLButtonElement | null {
  ctx.fixture.detectChanges();
  return ctx.fixture.nativeElement.querySelector(selector);
}

async function clickEdit(ctx: ReturnType<typeof setup>): Promise<void> {
  queryButton(ctx, '[data-action="edit"]')!.click();
  ctx.fixture.detectChanges();
  await ctx.fixture.whenStable();
}

describe('SqObjectDetail', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('on 409 stale-object opens the conflict dialog once and does not re-PUT on its own', async () => {
    const ctx = await loaded(setup());
    await clickEdit(ctx);
    ctx.objects.update.mockReturnValueOnce(throwError(() => problem('stale-object')));

    await ctx.component.save();

    expect(ctx.dialog.open).toHaveBeenCalledTimes(1);
    expect(ctx.objects.update).toHaveBeenCalledTimes(1);
  });

  it('keeps the edited values in the form when the conflict dialog is dismissed', async () => {
    const ctx = await loaded(setup(undefined));
    await clickEdit(ctx);
    ctx.objects.update.mockReturnValueOnce(throwError(() => problem('stale-object')));
    ctx.component.form().get('status')!.setValue('Closed');

    await ctx.component.save();

    expect(ctx.component.form().get('status')!.value).toBe('Closed');
  });

  it('refetches the object after a successful save instead of trusting the PUT body', async () => {
    const ctx = await loaded(setup());
    await clickEdit(ctx);
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

  it('opens in view mode with every control disabled and no Save button', async () => {
    const ctx = await loaded(setup());
    ctx.fixture.detectChanges();

    const controls = Object.values(ctx.component.form().controls);
    expect(controls.length).toBeGreaterThan(0);
    expect(controls.every((control) => control.disabled)).toBe(true);
    expect(queryButton(ctx, '[data-action="save"]')).toBeNull();
    expect(queryButton(ctx, '[data-action="cancel"]')).toBeNull();
  });

  it('Edit enables the editable controls and keeps read-only controls disabled', async () => {
    const ctx = await loaded(setup());
    await clickEdit(ctx);

    const form = ctx.component.form();
    expect(form.get('title')!.enabled).toBe(true);
    expect(form.get('status')!.enabled).toBe(true);
    expect(form.get('openTaskCount')!.disabled).toBe(true);
    expect(queryButton(ctx, '[data-action="save"]')).not.toBeNull();
    expect(queryButton(ctx, '[data-action="cancel"]')).not.toBeNull();
  });

  it('Cancel discards the edits and returns to view mode', async () => {
    const ctx = await loaded(setup());
    await clickEdit(ctx);
    ctx.component.form().get('status')!.setValue('Closed');

    queryButton(ctx, '[data-action="cancel"]')!.click();
    ctx.fixture.detectChanges();

    expect(ctx.component.form().get('status')!.value).toBe('Open');
    expect(ctx.component.form().disabled).toBe(true);
    expect(queryButton(ctx, '[data-action="save"]')).toBeNull();
  });

  it('a successful save returns to view mode', async () => {
    const ctx = await loaded(setup());
    await clickEdit(ctx);
    ctx.component.form().get('status')!.setValue('Closed');

    await ctx.component.save();
    ctx.fixture.detectChanges();

    expect(ctx.component.form().disabled).toBe(true);
    expect(queryButton(ctx, '[data-action="save"]')).toBeNull();
  });

  it('renders the transitions panel once loaded', async () => {
    const ctx = await loaded(setup());
    ctx.fixture.detectChanges();

    expect(ctx.fixture.nativeElement.querySelectorAll('sq-transitions-panel')).toHaveLength(1);
  });

  it('Delete and the transition buttons are available in view mode', async () => {
    const ctx = await loaded(setup());
    ctx.fixture.detectChanges();

    expect(queryButton(ctx, '[data-action="delete"]')!.disabled).toBe(false);
    expect(queryButton(ctx, '[data-transition="close"]')!.disabled).toBe(false);
  });
});
