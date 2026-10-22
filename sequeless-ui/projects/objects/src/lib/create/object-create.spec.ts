/**
 * Contract [OBJ-CREATE] (lib/create/object-create.ts, `SqObjectCreate`): builds the POST body via
 * formToProperties (never a version key), navigates to the detail route on success, maps 400
 * violations onto the offending form controls and stays on the form, and keeps other failures
 * visible without navigating. Create has no If-Match, so no conflict dialog is involved.
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import type { ApiError, BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { provideUiKit } from '@sequeless/ui-kit';
import { of, throwError } from 'rxjs';

import { SqObjectCreate } from './object-create';

const OBJECT_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';
const TYPE: TypeDetailResponse = PROJECT_TYPE_DETAIL;

function created(): BusinessObjectResponse {
  return {
    id: OBJECT_ID,
    type: 'Project',
    typeIri: TYPE.iri,
    version: 0,
    state: 'Open',
    properties: { status: 'Open' },
    audit: {
      createdAt: '2026-09-01T10:00:00Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-01T10:00:00Z',
      updatedBy: 'anonymous',
    },
  };
}

function problem(status: number, extra: Partial<ApiError> = {}): ApiError {
  return {
    status,
    kind: 'problem',
    title: 'Bad request',
    detail: null,
    slug: 'validation',
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

function setup() {
  const objects = { create: vi.fn(() => of(created())) };

  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      provideRouter([]),
      { provide: ObjectsClient, useValue: objects },
      { provide: TypesClient, useValue: { describe: vi.fn(() => of(TYPE)) } },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ type: 'Project' })) },
      },
    ],
  });

  const router = TestBed.inject(Router);
  const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(SqObjectCreate);
  return { fixture, component: fixture.componentInstance, objects, navigate };
}

async function loaded(ctx: ReturnType<typeof setup>) {
  ctx.fixture.detectChanges();
  await ctx.fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve));
  return ctx;
}

describe('SqObjectCreate', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('builds the POST body via formToProperties and omits version', async () => {
    const ctx = await loaded(setup());
    ctx.component.form().get('status')!.setValue('Open');

    await ctx.component.save();

    const [type, body] = ctx.objects.create.mock.calls[0] as unknown as [string, object];
    expect(type).toBe('Project');
    expect(body).toEqual({ properties: expect.objectContaining({ status: 'Open' }) });
    expect(body).not.toHaveProperty('version');
  });

  it('navigates to the detail route with the returned id on success', async () => {
    const ctx = await loaded(setup());

    await ctx.component.save();

    expect(ctx.navigate).toHaveBeenCalledWith(['/objects', 'Project', OBJECT_ID]);
  });

  it('maps 400 violations onto the matching form control and stays on the form', async () => {
    const ctx = await loaded(setup());
    ctx.objects.create.mockReturnValueOnce(
      throwError(() =>
        problem(400, {
          violations: [{ property: 'status', propertyIri: null, message: 'not allowed' }],
        }),
      ),
    );

    await ctx.component.save();

    expect(ctx.navigate).not.toHaveBeenCalled();
    expect(ctx.component.form().get('status')!.errors).toEqual({ server: 'not allowed' });
  });

  it('keeps a non-validation failure visible and does not navigate', async () => {
    const ctx = await loaded(setup());
    ctx.objects.create.mockReturnValueOnce(throwError(() => problem(500, { slug: null })));

    await ctx.component.save();

    expect(ctx.navigate).not.toHaveBeenCalled();
    expect(ctx.component.error()?.status).toBe(500);
  });
});
