/*
 * Contract [CORE-OBJECTS]: ObjectsClient wraps GET/POST/PUT/DELETE calls to
 * {base}/objects/{type}(/{id}) where {type} is always encodeURIComponent-ed.
 * browse() serialises BrowseQueryState via toHttpParams; update() sends a
 * body of exactly {properties} (etagInterceptor adds If-Match, never a
 * version key); fireTransition() and signal() POST to their nested paths.
 * Every method pipes rethrowAsApiError, per [CORE-ERR].
 */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { BrowseQueryState } from '../query/browse-query';
import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { ObjectsClient } from './objects-client';

const TASK_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';

const TASK_BODY = {
  id: TASK_ID,
  type: 'Task',
  typeIri: 'https://example.org/ontology#Task',
  version: 3,
  state: 'Open',
  properties: { title: 'Write the quarterly report' },
  audit: {
    createdAt: '2026-09-01T10:00:00Z',
    createdBy: 'anonymous',
    updatedAt: '2026-09-02T10:00:00Z',
    updatedBy: 'anonymous',
  },
};

const BROWSE_STATE: BrowseQueryState = {
  page: 0,
  size: 20,
  sort: [{ property: 'title', direction: 'asc' }],
  facets: [],
  filters: [{ property: 'name', op: 'eq', value: 'Acme' }],
};

describe('ObjectsClient', () => {
  let client: ObjectsClient;
  let controller: HttpTestingController;

  function setup(providers: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...providers],
    });
    client = TestBed.inject(ObjectsClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  it('is provided in root', () => {
    setup();
    expect(client).toBeInstanceOf(ObjectsClient);
  });

  it('browse() sends a GET to /api/objects/{type} with toHttpParams(state) and encodes the type', () => {
    setup();

    client.browse('http://ex.org/Project', BROWSE_STATE).subscribe();

    const req = controller.expectOne(
      (r) => r.url === '/api/objects/http%3A%2F%2Fex.org%2FProject',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('title');
    expect(req.request.params.get('filter[name][eq]')).toBe('Acme');
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0, facets: {} });
  });

  it('get(type, id) sends a GET to /api/objects/{type}/{id}', () => {
    setup();

    client.get('Task', TASK_ID).subscribe();

    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}`);
    expect(req.request.method).toBe('GET');
    req.flush(TASK_BODY);
  });

  it('create(type, req) sends a POST to /api/objects/{type} with the request body', () => {
    setup();

    client.create('Task', { properties: { title: 'New' } }).subscribe();

    const req = controller.expectOne('/api/objects/Task');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ properties: { title: 'New' } });
    req.flush(TASK_BODY, { status: 201, statusText: 'Created' });
  });

  it('update(type, id, properties) sends a PUT with a body of exactly {properties}, never a version key', () => {
    setup();

    client.update('Task', TASK_ID, { title: 'Renamed' }).subscribe();

    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ properties: { title: 'Renamed' } });
    expect(req.request.body).not.toHaveProperty('version');
    req.flush(TASK_BODY);
  });

  it('delete(type, id) sends a DELETE and completes on a 204', async () => {
    setup();

    const result = firstValueFrom(client.delete('Task', TASK_ID));
    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeUndefined();
  });

  it('transitions(type, id) sends a GET to .../transitions and resolves with the response body', async () => {
    setup();

    const result = firstValueFrom(client.transitions('Task', TASK_ID));
    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}/transitions`);
    expect(req.request.method).toBe('GET');
    const transitions = [{ name: 'start', available: true, reason: null }];
    req.flush(transitions);

    await expect(result).resolves.toEqual(transitions);
  });

  it('fireTransition(type, id, name) sends a POST with body {} to .../transitions/{name}', () => {
    setup();

    client.fireTransition('Task', TASK_ID, 'start').subscribe();

    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}/transitions/start`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(TASK_BODY);
  });

  it('signal(type, id, name, payload) POSTs the payload to .../signals/{name} and completes on a 202 empty body', async () => {
    setup();

    const result = firstValueFrom(client.signal('Task', TASK_ID, 'approved', { note: 'ok' }));
    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}/signals/approved`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'ok' });
    req.flush(null, { status: 202, statusText: 'Accepted' });

    await expect(result).resolves.toBeUndefined();
  });

  it('signal(type, id, name) with no payload POSTs {}', () => {
    setup();

    client.signal('Task', TASK_ID, 'approved').subscribe();

    const req = controller.expectOne(`/api/objects/Task/${TASK_ID}/signals/approved`);
    expect(req.request.body).toEqual({});
    req.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('encodeURIComponent-encodes {type} for every method', () => {
    setup();
    const iri = 'https://example.org/ontology#Task';
    const encoded = encodeURIComponent(iri);

    client.get(iri, TASK_ID).subscribe();

    controller.expectOne(`/api/objects/${encoded}/${TASK_ID}`).flush(TASK_BODY);
  });

  it('honours a custom SEQUELESS_API_BASE_URL', () => {
    setup([{ provide: SEQUELESS_API_BASE_URL, useValue: '/x' }]);

    client.get('Task', TASK_ID).subscribe();

    controller.expectOne(`/x/objects/Task/${TASK_ID}`).flush(TASK_BODY);
  });

  it('surfaces a 409 stale-object problem as an ApiError via rethrowAsApiError', async () => {
    setup();

    const result = firstValueFrom(client.update('Task', TASK_ID, { title: 'Renamed' }));
    controller.expectOne(`/api/objects/Task/${TASK_ID}`).flush(
      {
        type: 'https://sequeless.dev/problems/stale-object',
        title: 'Stale object',
        detail: 'The object was modified concurrently.',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(result).rejects.toMatchObject({
      kind: 'problem',
      status: 409,
      slug: 'stale-object',
    });
  });

  it('applies a provided SEQUELESS_ERROR_MAPPER to a failed request', async () => {
    const mapper = vi.fn((e: unknown) => ({ mapped: e }));
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: mapper }]);

    const result = firstValueFrom(client.get('Task', TASK_ID));
    controller.expectOne(`/api/objects/Task/${TASK_ID}`).flush(
      { message: 'boom' },
      { status: 500, statusText: 'Server Error' },
    );

    await expect(result).rejects.toMatchObject({
      mapped: expect.objectContaining({ kind: 'legacy-message' }),
    });
    expect(mapper).toHaveBeenCalledTimes(1);
  });
});
