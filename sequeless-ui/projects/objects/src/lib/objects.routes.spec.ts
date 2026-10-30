/**
 * Contract [OBJ-ROUTES] (lib/objects.routes.ts, `objectsRoutes`): '' lands on the type list,
 * `:type` on browse, `:type/new` on create (never captured by `:type/:id`), `:type/:id` on
 * detail, and a wrapper route provides RELATIONSHIP_LOOKUP so ui-kit's relationship picker gets
 * the objects-backed lookup inside the feature.
 */
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import type { BusinessObjectResponse, TypeSummaryResponse } from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { RELATIONSHIP_LOOKUP } from '@sequeless/ui-kit';
import { of } from 'rxjs';

import { SqObjectBrowse } from './browse/object-browse';
import { SqObjectCreate } from './create/object-create';
import { SqObjectDetail } from './detail/object-detail.component';
import { objectsRoutes } from './objects.routes';
import { ObjectsRelationshipLookup } from './relationship/objects-relationship-lookup';
import { SqTypeList } from './type-list/type-list';

const OBJECT_ID = '123e4567-e89b-12d3-a456-426614174000';

const TYPES: TypeSummaryResponse[] = [
  {
    iri: PROJECT_TYPE_DETAIL.iri,
    name: 'Project',
    label: 'Project',
    abstract: false,
    superTypes: [],
  },
];

function object(): BusinessObjectResponse {
  return {
    id: OBJECT_ID,
    type: 'Project',
    typeIri: PROJECT_TYPE_DETAIL.iri,
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

async function navigate(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter(objectsRoutes),
      provideNoopAnimations(),
      {
        provide: TypesClient,
        useValue: {
          list: vi.fn(() => of(TYPES)),
          describe: vi.fn(() => of(PROJECT_TYPE_DETAIL)),
        },
      },
      {
        provide: ObjectsClient,
        useValue: {
          browse: vi.fn(() =>
            of({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0, facets: {} }),
          ),
          get: vi.fn(() => of(object())),
          transitions: vi.fn(() => of([])),
          create: vi.fn(() => of(object())),
        },
      },
      { provide: MatDialog, useValue: { open: vi.fn() } },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  await harness.fixture.whenStable();
  return harness;
}

describe('Contract [OBJ-ROUTES] objectsRoutes', () => {
  it("renders the type list at ''", async () => {
    const harness = await navigate('/');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(SqTypeList);
  });

  it('renders browse at :type', async () => {
    const harness = await navigate('/Project');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(SqObjectBrowse);
  });

  it('renders create at :type/new, not detail', async () => {
    const harness = await navigate('/Project/new');
    const instance = harness.routeDebugElement?.componentInstance;
    expect(instance).toBeInstanceOf(SqObjectCreate);
    expect(instance).not.toBeInstanceOf(SqObjectDetail);
  });

  it('renders detail at :type/:id', async () => {
    const harness = await navigate(`/Project/${OBJECT_ID}`);
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(SqObjectDetail);
  });

  it('provides RELATIONSHIP_LOOKUP from the wrapper route', async () => {
    const harness = await navigate('/');
    const lookup = harness.routeDebugElement!.injector.get(RELATIONSHIP_LOOKUP);
    expect(lookup).toBeInstanceOf(ObjectsRelationshipLookup);
  });
});
