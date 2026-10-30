/*
 * Contract [CORE-TYPES]: TypesClient.list() GETs {base}/types and
 * TypesClient.describe(nameOrIri) GETs {base}/types/{encodeURIComponent(nameOrIri)}.
 * Both resolve `base` from SEQUELESS_API_BASE_URL (default '/api') and route
 * errors through rethrowAsApiError using SEQUELESS_ERROR_MAPPER (default
 * identity), per [CORE-ERR]: an HttpErrorResponse becomes mapper(toApiError(e)).
 */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { TypesClient } from './types-client';

const TASK_TYPE_SUMMARY = {
  iri: 'https://example.org/ontology#Task',
  name: 'Task',
  label: 'Task',
  abstract: false,
  superTypes: [],
};

const PROJECT_TYPE_DETAIL = {
  iri: 'http://ex.org/Project',
  name: 'Project',
  label: 'Project',
  abstract: false,
  superTypes: [],
  group: null,
  hidden: false,
  properties: [],
  stateMachine: null,
};

describe('TypesClient', () => {
  let client: TypesClient;
  let controller: HttpTestingController;

  function setup(providers: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...providers],
    });
    client = TestBed.inject(TypesClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  it('is provided in root', () => {
    setup();
    expect(client).toBeInstanceOf(TypesClient);
  });

  it('list() sends a GET to /api/types', () => {
    setup();

    client.list().subscribe();

    const req = controller.expectOne('/api/types');
    expect(req.request.method).toBe('GET');
    req.flush([TASK_TYPE_SUMMARY]);
  });

  it('list() resolves with the response body', async () => {
    setup();

    const result = firstValueFrom(client.list());
    controller.expectOne('/api/types').flush([TASK_TYPE_SUMMARY]);

    await expect(result).resolves.toEqual([TASK_TYPE_SUMMARY]);
  });

  it('describe(nameOrIri) sends a GET to /api/types/{encodeURIComponent(nameOrIri)}', () => {
    setup();

    client.describe('http://ex.org/Project').subscribe();

    const req = controller.expectOne('/api/types/http%3A%2F%2Fex.org%2FProject');
    expect(req.request.method).toBe('GET');
    req.flush(PROJECT_TYPE_DETAIL);
  });

  it('describe(nameOrIri) leaves a plain short name unencoded', () => {
    setup();

    client.describe('Task').subscribe();

    controller.expectOne('/api/types/Task').flush(PROJECT_TYPE_DETAIL);
  });

  it('honours a custom SEQUELESS_API_BASE_URL for list()', () => {
    setup([{ provide: SEQUELESS_API_BASE_URL, useValue: '/x' }]);

    client.list().subscribe();

    controller.expectOne('/x/types').flush([TASK_TYPE_SUMMARY]);
  });

  it('honours a custom SEQUELESS_API_BASE_URL for describe()', () => {
    setup([{ provide: SEQUELESS_API_BASE_URL, useValue: '/x' }]);

    client.describe('Task').subscribe();

    controller.expectOne('/x/types/Task').flush(PROJECT_TYPE_DETAIL);
  });

  it('defaults to /api when no SEQUELESS_API_BASE_URL is provided', () => {
    setup();

    client.list().subscribe();

    controller.expectOne('/api/types').flush([TASK_TYPE_SUMMARY]);
  });

  it('turns a 404 {message} body into an ApiError of kind legacy-message', async () => {
    setup();

    const result = firstValueFrom(client.describe('Missing'));
    controller.expectOne('/api/types/Missing').flush(
      { message: 'Type not found' },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(result).rejects.toMatchObject({
      kind: 'legacy-message',
      status: 404,
      detail: 'Type not found',
    });
  });

  it('applies a provided SEQUELESS_ERROR_MAPPER to a failed list() request', async () => {
    const mapper = vi.fn((e: unknown) => ({ mapped: e }));
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: mapper }]);

    const result = firstValueFrom(client.list());
    controller
      .expectOne('/api/types')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    await expect(result).rejects.toMatchObject({ mapped: expect.objectContaining({ kind: 'legacy-message' }) });
    expect(mapper).toHaveBeenCalledTimes(1);
  });

  it('applies a provided SEQUELESS_ERROR_MAPPER to a failed describe() request', async () => {
    const mapper = vi.fn((e: unknown) => ({ mapped: e }));
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: mapper }]);

    const result = firstValueFrom(client.describe('Missing'));
    controller
      .expectOne('/api/types/Missing')
      .flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });

    await expect(result).rejects.toMatchObject({ mapped: expect.objectContaining({ kind: 'legacy-message' }) });
    expect(mapper).toHaveBeenCalledTimes(1);
  });

  it('leaves a successful describe() untouched by the error mapper', async () => {
    const mapper = vi.fn((e: unknown) => ({ mapped: e }));
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: mapper }]);

    const result = firstValueFrom(client.describe('Project'));
    controller.expectOne('/api/types/Project').flush(PROJECT_TYPE_DETAIL);

    await expect(result).resolves.toEqual(PROJECT_TYPE_DETAIL);
    expect(mapper).not.toHaveBeenCalled();
  });
});
