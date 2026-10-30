/*
 * Contract under test ([OBJ-STORE], lib/detail/object-detail.store.ts): ObjectDetailStore is an
 * `@Injectable()` provided at the `SqObjectDetail` component level, wrapping TypesClient.describe
 * and ObjectsClient.get behind three signals: `type`, `object` and `error`.
 *
 * `load(typeName, id)` calls `TypesClient.describe(typeName)` and `ObjectsClient.get(typeName,
 * id)`, setting `type` and `object` from the results. `refetch()` calls `ObjectsClient.get` again
 * for the type/id passed to the most recent `load()` and updates `object`, without touching
 * `type`. `set(object)` replaces `object` directly, bypassing the client. A failing `get` (either
 * from `load` or `refetch`) sets `error` to the rejected `ApiError` and leaves `object` as it was
 * before the call.
 */
import { TestBed } from '@angular/core/testing';
import type { ApiError, BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { of, throwError } from 'rxjs';

import { ObjectDetailStore } from './object-detail.store';

const TYPE_NAME = 'Project';
const OBJECT_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';

const TYPE_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;

function objectBody(version: number, title = 'Apollo'): BusinessObjectResponse {
  return {
    id: OBJECT_ID,
    type: TYPE_NAME,
    typeIri: TYPE_DETAIL.iri,
    version,
    state: null,
    properties: { title },
    audit: {
      createdAt: '2026-09-01T10:00:00Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-02T10:00:00Z',
      updatedBy: 'anonymous',
    },
  };
}

const API_ERROR: ApiError = {
  status: 404,
  kind: 'legacy-message',
  title: 'Not Found',
  detail: 'Object not found',
  slug: null,
  violations: [],
  issues: [],
  expectedVersion: null,
  transitionName: null,
  typeIri: null,
  signalName: null,
  raw: null,
};

function setup() {
  const describe_ = vi.fn(() => of(TYPE_DETAIL));
  const get = vi.fn(() => of(objectBody(1)));

  TestBed.configureTestingModule({
    providers: [
      ObjectDetailStore,
      { provide: TypesClient, useValue: { describe: describe_ } },
      { provide: ObjectsClient, useValue: { get } },
    ],
  });

  const store = TestBed.inject(ObjectDetailStore);
  return { store, describe_, get };
}

describe('ObjectDetailStore', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('starts with null type, object and error', () => {
    const { store } = setup();

    expect(store.type()).toBeNull();
    expect(store.object()).toBeNull();
    expect(store.error()).toBeNull();
  });

  describe('load', () => {
    it('calls TypesClient.describe and ObjectsClient.get with the given typeName and id', async () => {
      const { store, describe_, get } = setup();

      await store.load(TYPE_NAME, OBJECT_ID);

      expect(describe_).toHaveBeenCalledWith(TYPE_NAME);
      expect(get).toHaveBeenCalledWith(TYPE_NAME, OBJECT_ID);
    });

    it('sets type from TypesClient.describe', async () => {
      const { store } = setup();

      await store.load(TYPE_NAME, OBJECT_ID);

      expect(store.type()).toEqual(TYPE_DETAIL);
    });

    it('sets object from ObjectsClient.get', async () => {
      const { store } = setup();

      await store.load(TYPE_NAME, OBJECT_ID);

      expect(store.object()).toEqual(objectBody(1));
    });

    it('sets error and leaves object null when get fails', async () => {
      const get = vi.fn(() => throwError(() => API_ERROR));
      TestBed.configureTestingModule({
        providers: [
          ObjectDetailStore,
          { provide: TypesClient, useValue: { describe: vi.fn(() => of(TYPE_DETAIL)) } },
          { provide: ObjectsClient, useValue: { get } },
        ],
      });
      const store = TestBed.inject(ObjectDetailStore);

      await store.load(TYPE_NAME, OBJECT_ID);

      expect(store.error()).toEqual(API_ERROR);
      expect(store.object()).toBeNull();
    });
  });

  describe('refetch', () => {
    it('calls ObjectsClient.get again for the type/id passed to load', async () => {
      const { store, get } = setup();
      await store.load(TYPE_NAME, OBJECT_ID);
      get.mockReturnValue(of(objectBody(2)));

      await store.refetch();

      expect(get).toHaveBeenCalledTimes(2);
      expect(get).toHaveBeenLastCalledWith(TYPE_NAME, OBJECT_ID);
    });

    it('updates object with the refetched value', async () => {
      const { store, get } = setup();
      await store.load(TYPE_NAME, OBJECT_ID);
      get.mockReturnValue(of(objectBody(2, 'Renamed')));

      const result = await store.refetch();

      expect(store.object()).toEqual(objectBody(2, 'Renamed'));
      expect(result).toEqual(objectBody(2, 'Renamed'));
    });

    it('does not change type', async () => {
      const { store, get } = setup();
      await store.load(TYPE_NAME, OBJECT_ID);
      get.mockReturnValue(of(objectBody(2)));

      await store.refetch();

      expect(store.type()).toEqual(TYPE_DETAIL);
    });

    it('sets error and leaves the previous object when the refetch get fails', async () => {
      const { store, get } = setup();
      await store.load(TYPE_NAME, OBJECT_ID);
      get.mockReturnValue(throwError(() => API_ERROR));

      await store.refetch();

      expect(store.error()).toEqual(API_ERROR);
      expect(store.object()).toEqual(objectBody(1));
    });
  });

  describe('set', () => {
    it('replaces object directly without calling any client', async () => {
      const { store, get } = setup();
      await store.load(TYPE_NAME, OBJECT_ID);
      get.mockClear();

      store.set(objectBody(5, 'Direct'));

      expect(store.object()).toEqual(objectBody(5, 'Direct'));
      expect(get).not.toHaveBeenCalled();
    });
  });
});
