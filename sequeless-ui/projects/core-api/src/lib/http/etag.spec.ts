/*
 * Optimistic locking over HTTP (ObjectsController):
 *   - GET/POST/PUT /api/objects/{type}/{id} and POST .../transitions/{name}
 *     answer with `ETag: "<version>"`;
 *   - PUT, DELETE and POST .../transitions/{name} accept `If-Match: "<version>"`
 *     (resolveExpectedVersion / stripQuotes). PUT also accepts a body
 *     `version`, and the server answers 428 when neither is sent and 400 when
 *     both are sent but disagree.
 *
 * Client design: ETagRegistry remembers the latest version per object (keyed
 * by the response body's id only, stored WITHOUT quotes), and
 * etagInterceptor re-sends it as a quoted If-Match. The interceptor never
 * injects `version` into a PUT body, so the 400 body/header mismatch is
 * unreachable, and the 428 is only reachable for an object never seen.
 *
 * Keyed by id only: ids are globally unique UUIDs, so the {type} path segment
 * (short name, encoded IRI, or a supertype) is irrelevant to the lookup.
 */
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { etagInterceptor } from './etag.interceptor';
import { ETagRegistry } from './etag-registry';

const TASK_IRI = 'https://example.org/ontology#Task';
const TASK_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';
const OTHER_ID = '5d2a9e77-31c4-4b8f-8e0a-6c1f3b2d7e45';
const TASK_URL = `/api/objects/Task/${TASK_ID}`;
const CREATED_WITH_VERSION_1 = {
  status: 201,
  statusText: 'Created',
  headers: new HttpHeaders({ ETag: '"1"' }),
};

interface BusinessObjectBody {
  id: string;
  type: string;
  typeIri: string;
  version: number;
  state: string | null;
  properties: Record<string, unknown>;
  audit: { createdAt: string; createdBy: string; updatedAt: string; updatedBy: string };
}

function taskBody(version: number, id = TASK_ID): BusinessObjectBody {
  return {
    id,
    type: 'Task',
    typeIri: TASK_IRI,
    version,
    state: 'Open',
    properties: { title: 'Write the quarterly report' },
    audit: {
      createdAt: '2026-09-01T10:00:00Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-02T10:00:00Z',
      updatedBy: 'anonymous',
    },
  };
}

describe('ETagRegistry', () => {
  let registry: ETagRegistry;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    registry = TestBed.inject(ETagRegistry);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('is provided in root', () => {
    expect(registry).toBeInstanceOf(ETagRegistry);
  });

  it('returns undefined for an object it has never seen', () => {
    expect(registry.get(TASK_ID)).toBeUndefined();
  });

  it('returns the last version set for an object id', () => {
    registry.set(TASK_ID, '3');
    registry.set(TASK_ID, '4');

    expect(registry.get(TASK_ID)).toBe('4');
  });

  it('keeps versions for different objects independent', () => {
    registry.set(TASK_ID, '3');
    registry.set(OTHER_ID, '9');

    expect(registry.get(TASK_ID)).toBe('3');
    expect(registry.get(OTHER_ID)).toBe('9');
  });

  it('strips surrounding double quotes when a quoted ETag is set', () => {
    registry.set(TASK_ID, '"7"');

    expect(registry.get(TASK_ID)).toBe('7');
  });

  it('forgets an object after clear', () => {
    registry.set(TASK_ID, '3');
    registry.clear(TASK_ID);

    expect(registry.get(TASK_ID)).toBeUndefined();
  });
});

describe('etagInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let registry: ETagRegistry;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([etagInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    registry = TestBed.inject(ETagRegistry);
  });

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  describe('recording ETags from responses', () => {
    it('stores the unquoted version from a GET /api/objects/{type}/{id} ETag, keyed by the body id', () => {
      http.get(TASK_URL).subscribe();
      controller
        .expectOne(TASK_URL)
        .flush(taskBody(3), { headers: new HttpHeaders({ ETag: '"3"' }) });

      expect(registry.get(TASK_ID)).toBe('3');
    });

    it('strips the quotes from ETag "7" and stores the string 7', () => {
      http.get(TASK_URL).subscribe();
      controller
        .expectOne(TASK_URL)
        .flush(taskBody(7), { headers: new HttpHeaders({ ETag: '"7"' }) });

      const stored = registry.get(TASK_ID);
      expect(stored).toBe('7');
      expect(typeof stored).toBe('string');
    });

    it('stores the version from a POST /api/objects/{type} create response ETag', () => {
      http.post('/api/objects/Task', { properties: { title: 'New' } }).subscribe();
      controller
        .expectOne('/api/objects/Task')
        .flush(taskBody(1), CREATED_WITH_VERSION_1);

      expect(registry.get(TASK_ID)).toBe('1');
    });

    it('updates the stored version from a PUT response ETag', () => {
      registry.set(TASK_ID, '3');

      http.put(TASK_URL, { properties: { title: 'Renamed' } }).subscribe();
      controller
        .expectOne(TASK_URL)
        .flush(taskBody(4), { headers: new HttpHeaders({ ETag: '"4"' }) });

      expect(registry.get(TASK_ID)).toBe('4');
    });

    it('updates the stored version from a POST .../transitions/{name} response ETag', () => {
      registry.set(TASK_ID, '4');
      const url = `${TASK_URL}/transitions/start`;

      http.post(url, null).subscribe();
      controller.expectOne(url).flush(taskBody(5), { headers: new HttpHeaders({ ETag: '"5"' }) });

      expect(registry.get(TASK_ID)).toBe('5');
    });

    it('leaves the registry untouched when a response carries no ETag', () => {
      registry.set(TASK_ID, '3');

      http.get(TASK_URL).subscribe();
      controller.expectOne(TASK_URL).flush(taskBody(99));

      expect(registry.get(TASK_ID)).toBe('3');
    });
  });

  describe('adding If-Match to outgoing requests', () => {
    it('adds a quoted If-Match to a PUT when the object version is known', () => {
      registry.set(TASK_ID, '3');

      http.put(TASK_URL, { properties: { title: 'Renamed' } }).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.headers.get('If-Match')).toBe('"3"');
      req.flush(taskBody(4), { headers: new HttpHeaders({ ETag: '"4"' }) });
    });

    it('adds a quoted If-Match to a DELETE when the object version is known', () => {
      registry.set(TASK_ID, '3');

      http.delete(TASK_URL).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.method).toBe('DELETE');
      expect(req.request.headers.get('If-Match')).toBe('"3"');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('adds a quoted If-Match to POST .../transitions/{name} when the object version is known', () => {
      registry.set(TASK_ID, '4');
      const url = `${TASK_URL}/transitions/start`;

      http.post(url, null).subscribe();
      const req = controller.expectOne(url);

      expect(req.request.headers.get('If-Match')).toBe('"4"');
      req.flush(taskBody(5), { headers: new HttpHeaders({ ETag: '"5"' }) });
    });

    it('re-adds quotes around a version that was stored from a quoted ETag', () => {
      http.get(TASK_URL).subscribe();
      controller
        .expectOne(TASK_URL)
        .flush(taskBody(7), { headers: new HttpHeaders({ ETag: '"7"' }) });

      http.delete(TASK_URL).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.headers.get('If-Match')).toBe('"7"');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('resolves the version by the {id} segment whether {type} is a short name, an encoded IRI or a supertype', () => {
      registry.set(TASK_ID, '3');
      const urls = [
        `/api/objects/Task/${TASK_ID}`,
        `/api/objects/${encodeURIComponent(TASK_IRI)}/${TASK_ID}`,
        `/api/objects/WorkItem/${TASK_ID}`,
      ];

      for (const url of urls) {
        http.delete(url).subscribe();
        const req = controller.expectOne(url);

        expect(req.request.headers.get('If-Match')).toBe('"3"');
        req.flush(null, { status: 204, statusText: 'No Content' });
      }
    });

    it('does not overwrite an If-Match header the caller set explicitly', () => {
      registry.set(TASK_ID, '3');

      http
        .put(TASK_URL, { properties: {} }, { headers: new HttpHeaders({ 'If-Match': '"2"' }) })
        .subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.headers.get('If-Match')).toBe('"2"');
      req.flush(taskBody(3), { headers: new HttpHeaders({ ETag: '"3"' }) });
    });

    it('does not add If-Match to a GET even when the version is known', () => {
      registry.set(TASK_ID, '3');

      http.get(TASK_URL).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.headers.has('If-Match')).toBe(false);
      req.flush(taskBody(3), { headers: new HttpHeaders({ ETag: '"3"' }) });
    });

    it('does not add If-Match to a PUT for an object whose version is unknown', () => {
      registry.set(OTHER_ID, '9');

      http.put(TASK_URL, { properties: {} }).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.headers.has('If-Match')).toBe(false);
      req.flush(taskBody(2), { headers: new HttpHeaders({ ETag: '"2"' }) });
    });

    it('does not add If-Match to a create POST or to a signal POST', () => {
      registry.set(TASK_ID, '3');
      const signalUrl = `${TASK_URL}/signals/approved`;

      http.post('/api/objects/Task', { properties: {} }).subscribe();
      http.post(signalUrl, {}).subscribe();
      const create = controller.expectOne('/api/objects/Task');
      const signal = controller.expectOne(signalUrl);

      expect(create.request.headers.has('If-Match')).toBe(false);
      expect(signal.request.headers.has('If-Match')).toBe(false);
      create.flush(taskBody(1, OTHER_ID), CREATED_WITH_VERSION_1);
      signal.flush(null, { status: 202, statusText: 'Accepted' });
    });

    it('does not inject a version into the PUT body, leaving it byte-for-byte as the caller sent it', () => {
      registry.set(TASK_ID, '3');
      const body = { properties: { title: 'Renamed', priority: 2 } };

      http.put(TASK_URL, body).subscribe();
      const req = controller.expectOne(TASK_URL);

      expect(req.request.body).toEqual({ properties: { title: 'Renamed', priority: 2 } });
      expect(req.request.body).not.toHaveProperty('version');
      expect(req.request.headers.get('If-Match')).toBe('"3"');
      req.flush(taskBody(4), { headers: new HttpHeaders({ ETag: '"4"' }) });
    });
  });
});
