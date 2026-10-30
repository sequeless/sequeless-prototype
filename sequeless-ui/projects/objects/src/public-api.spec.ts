/*
 * Contract [OBJ-PUBLIC]: public-api.ts re-exports every runtime symbol of the objects library,
 * including the pure helpers (browse-columns, violation-mapping, object-form, known-signals) and
 * the SqObjectForm component, and nothing else, so a consumer imports only from '@sequeless/objects'.
 */
import * as api from './public-api';

const EXPECTED_EXPORTS: Record<string, 'function' | 'object'> = {
  // lib/objects.routes.ts
  objectsRoutes: 'object',
  // lib/browse/browse-query-state.service.ts
  BrowseQueryStateService: 'function',
  // lib/detail/object-detail.store.ts
  ObjectDetailStore: 'function',
  // lib/detail/object-detail.component.ts
  SqObjectDetail: 'function',
  // lib/create/object-create.ts
  SqObjectCreate: 'function',
  // lib/signals/object-signals.ts
  OBJECTS_SIGNAL_REFETCH_DELAY_MS: 'object',
  OBJECT_SIGNALS_PANEL: 'object',
  SqObjectSignals: 'function',
  // lib/browse/object-browse.ts
  SqObjectBrowse: 'function',
  // lib/relationship/objects-relationship-lookup.ts
  ObjectsRelationshipLookup: 'function',
  provideObjectsRelationshipLookup: 'function',
  // lib/type-list/type-list.ts
  SqTypeList: 'function',
  // lib/browse/browse-columns.ts
  PAGE_SIZE_OPTIONS: 'object',
  visibleColumns: 'function',
  facetableProperties: 'function',
  operatorsFor: 'function',
  // lib/browse/violation-mapping.ts
  mapViolationsToFilters: 'function',
  // lib/detail/object-form.ts
  editableProperties: 'function',
  groupProperties: 'function',
  buildObjectForm: 'function',
  formToProperties: 'function',
  SqObjectForm: 'function',
  // lib/signals/known-signals.ts
  knownSignals: 'function',
};

describe('public-api [OBJ-PUBLIC]', () => {
  it('exports exactly the expected runtime symbols', () => {
    expect(Object.keys(api).sort()).toEqual(Object.keys(EXPECTED_EXPORTS).sort());
  });

  it('exports each symbol with the expected runtime type', () => {
    const actual = Object.fromEntries(
      Object.entries(api).map(([name, value]) => [name, typeof value]),
    );
    expect(actual).toEqual(EXPECTED_EXPORTS);
  });

  it('exports a non-empty objectsRoutes array', () => {
    expect(Array.isArray(api.objectsRoutes)).toBe(true);
    expect(api.objectsRoutes.length).toBeGreaterThan(0);
  });

  it('exports the signals tokens', () => {
    expect(api.OBJECTS_SIGNAL_REFETCH_DELAY_MS).toBeDefined();
    expect(api.OBJECT_SIGNALS_PANEL).toBeDefined();
  });

  it('exports PAGE_SIZE_OPTIONS as [10, 20, 50, 100, 200]', () => {
    expect([...api.PAGE_SIZE_OPTIONS]).toEqual([10, 20, 50, 100, 200]);
  });

  it('exports knownSignals and mapViolationsToFilters as functions', () => {
    expect(typeof api.knownSignals).toBe('function');
    expect(typeof api.mapViolationsToFilters).toBe('function');
  });
});
