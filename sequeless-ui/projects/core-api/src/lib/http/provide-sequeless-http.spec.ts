/*
 * Contract [CORE-HTTP]: provideSequelessHttp() wires HttpClient with
 * [etagInterceptor, sequelessInterceptors]. sequelessInterceptors itself
 * delegates, in order, to every fn injected from SEQUELESS_HTTP_INTERCEPTORS
 * (optional, defaulting to []), so extra interceptors can be registered
 * without touching provideSequelessHttp, and the etag behaviour (If-Match
 * sent once a GET has recorded a version for that id) stays intact whether
 * or not any extra interceptors are configured.
 */
import { HttpClient, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideSequelessHttp, sequelessInterceptors } from './provide-sequeless-http';
import { SEQUELESS_HTTP_INTERCEPTORS } from '../tokens';

const TASK_ID = '0b6f5c1e-7a0d-4d3e-9a51-2f8e4c6b9d10';
const TASK_URL = `/api/objects/Task/${TASK_ID}`;

function taskBody(version: number) {
  return { id: TASK_ID, type: 'Task', version, properties: { title: 'Write the report' } };
}

describe('provideSequelessHttp', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('allows HttpClient to be injected', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });

    expect(TestBed.inject(HttpClient)).toBeInstanceOf(HttpClient);
  });

  it('wires etagInterceptor so a PUT carries If-Match after a GET recorded the version', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.get(TASK_URL).subscribe();
    controller.expectOne(TASK_URL).flush(taskBody(3), { headers: new HttpHeaders({ ETag: '"3"' }) });

    http.put(TASK_URL, { properties: { title: 'Renamed' } }).subscribe();
    const req = controller.expectOne(TASK_URL);

    expect(req.request.headers.get('If-Match')).toBe('"3"');
    req.flush(taskBody(4), { headers: new HttpHeaders({ ETag: '"4"' }) });
    controller.verify();
  });

  it('still allows a GET to pass through and complete when no version is yet known', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    let response: unknown;
    http.get(TASK_URL).subscribe((body) => (response = body));
    controller.expectOne(TASK_URL).flush(taskBody(1), { headers: new HttpHeaders({ ETag: '"1"' }) });

    expect(response).toEqual(taskBody(1));
    controller.verify();
  });

  it('runs extra interceptors provided under SEQUELESS_HTTP_INTERCEPTORS, each adding its own header', () => {
    const first: HttpInterceptorFn = (req, next) => next(req.clone({ setHeaders: { 'X-First': '1' } }));
    const second: HttpInterceptorFn = (req, next) => next(req.clone({ setHeaders: { 'X-Second': '2' } }));
    TestBed.configureTestingModule({
      providers: [
        provideSequelessHttp(),
        provideHttpClientTesting(),
        { provide: SEQUELESS_HTTP_INTERCEPTORS, useValue: [first, second] },
      ],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.get(TASK_URL).subscribe();
    const req = controller.expectOne(TASK_URL);

    expect(req.request.headers.get('X-First')).toBe('1');
    expect(req.request.headers.get('X-Second')).toBe('2');
    req.flush(taskBody(1));
    controller.verify();
  });

  it('runs extra interceptors in the order they appear in SEQUELESS_HTTP_INTERCEPTORS', () => {
    const order: string[] = [];
    const first: HttpInterceptorFn = (req, next) => {
      order.push('first');
      return next(req);
    };
    const second: HttpInterceptorFn = (req, next) => {
      order.push('second');
      return next(req);
    };
    TestBed.configureTestingModule({
      providers: [
        provideSequelessHttp(),
        provideHttpClientTesting(),
        { provide: SEQUELESS_HTTP_INTERCEPTORS, useValue: [first, second] },
      ],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.get(TASK_URL).subscribe();
    controller.expectOne(TASK_URL).flush(taskBody(1));

    expect(order).toEqual(['first', 'second']);
    controller.verify();
  });

  it('still lets requests pass through when no SEQUELESS_HTTP_INTERCEPTORS provider is registered', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    let response: unknown;
    http.get(TASK_URL).subscribe((body) => (response = body));
    controller.expectOne(TASK_URL).flush(taskBody(1));

    expect(response).toEqual(taskBody(1));
    controller.verify();
  });

  it('does not add If-Match to the PUT when no prior GET has recorded a version for that id', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.put(TASK_URL, { properties: {} }).subscribe();
    const req = controller.expectOne(TASK_URL);

    expect(req.request.headers.has('If-Match')).toBe(false);
    req.flush(taskBody(1), { headers: new HttpHeaders({ ETag: '"1"' }) });
    controller.verify();
  });
});

describe('sequelessInterceptors', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('is a function usable as an HttpInterceptorFn', () => {
    expect(typeof sequelessInterceptors).toBe('function');
  });

  it('delegates to every fn in SEQUELESS_HTTP_INTERCEPTORS, in order, when provided', () => {
    const order: string[] = [];
    const first: HttpInterceptorFn = (req, next) => {
      order.push('first');
      return next(req);
    };
    const second: HttpInterceptorFn = (req, next) => {
      order.push('second');
      return next(req);
    };
    TestBed.configureTestingModule({
      providers: [
        provideSequelessHttp(),
        provideHttpClientTesting(),
        { provide: SEQUELESS_HTTP_INTERCEPTORS, useValue: [first, second] },
      ],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    http.get(TASK_URL).subscribe();
    controller.expectOne(TASK_URL).flush(taskBody(1));

    expect(order).toEqual(['first', 'second']);
    controller.verify();
  });

  it('passes the request through unmodified when SEQUELESS_HTTP_INTERCEPTORS is not provided (defaults to [])', () => {
    TestBed.configureTestingModule({
      providers: [provideSequelessHttp(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);

    let response: unknown;
    http.get(TASK_URL).subscribe((body) => (response = body));
    controller.expectOne(TASK_URL).flush(taskBody(1));

    expect(response).toEqual(taskBody(1));
    controller.verify();
  });
});
