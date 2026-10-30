/*
 * Contract [CORE-ONTOLOGY]: OntologyClient.load() GETs {base}/ontology as text (Accept text/turtle);
 * OntologyClient.import(turtle) POSTs the text to {base}/ontology with Content-Type text/turtle.
 * A 422 report is returned as `{status: 'rejected', report}`; every other failure is rethrown through
 * rethrowAsApiError using SEQUELESS_ERROR_MAPPER, per [CORE-ERR].
 */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { OntologyClient } from './ontology-client';

const TURTLE = '@prefix ex: <http://ex.org/> . ex:A a ex:B .';

describe('OntologyClient', () => {
  let client: OntologyClient;
  let controller: HttpTestingController;

  function setup(providers: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...(providers as never[])],
    });
    client = TestBed.inject(OntologyClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  it('load() GETs /api/ontology as text asking for text/turtle', async () => {
    setup();
    const result = firstValueFrom(client.load());
    const req = controller.expectOne('/api/ontology');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('text');
    expect(req.request.headers.get('Accept')).toBe('text/turtle');
    req.flush(TURTLE);
    expect(await result).toBe(TURTLE);
  });

  it('uses SEQUELESS_API_BASE_URL', async () => {
    setup([{ provide: SEQUELESS_API_BASE_URL, useValue: '/v1' }]);
    const result = firstValueFrom(client.load());
    controller.expectOne('/v1/ontology').flush(TURTLE);
    await result;
  });

  it('import() POSTs the Turtle as text/turtle and returns an accepted outcome', async () => {
    setup();
    const result = firstValueFrom(client.import(TURTLE));
    const req = controller.expectOne('/api/ontology');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBe(TURTLE);
    expect(req.request.headers.get('Content-Type')).toBe('text/turtle');
    const response = { accepted: true, typeCount: 2, consistent: true, issues: [] };
    req.flush(response);
    expect(await result).toEqual({ status: 'accepted', response });
  });

  it('import() returns a 422 report as a rejected outcome', async () => {
    setup();
    const result = firstValueFrom(client.import(TURTLE));
    const report = {
      consistent: false,
      issues: [{ severity: 'error', subjectIri: 'http://ex.org/A', message: 'bad' }],
    };
    controller.expectOne('/api/ontology').flush(report, { status: 422, statusText: 'Unprocessable' });
    expect(await result).toEqual({ status: 'rejected', report });
  });

  it('import() rethrows other failures as an ApiError through the mapper', async () => {
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: (e: unknown) => ({ mapped: e }) }]);
    const result = firstValueFrom(client.import(TURTLE));
    controller
      .expectOne('/api/ontology')
      .flush({ timestamp: 't', status: 500, error: 'Boom', path: '/api/ontology' }, { status: 500, statusText: 'Err' });
    await expect(result).rejects.toMatchObject({ mapped: { status: 500, kind: 'spring-default' } });
  });

  it('load() rethrows failures through the mapper', async () => {
    setup([{ provide: SEQUELESS_ERROR_MAPPER, useValue: (e: unknown) => ({ mapped: e }) }]);
    const result = firstValueFrom(client.load());
    controller.expectOne('/api/ontology').flush('x', { status: 503, statusText: 'Down' });
    await expect(result).rejects.toHaveProperty('mapped');
  });
});
