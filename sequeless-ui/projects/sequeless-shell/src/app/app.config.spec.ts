/**
 * Contract [SHELL-CONFIG] (app.config.ts, `appConfig`):
 * Guards that the shell registers the etag interceptor (via provideSequelessHttp) so
 * PUT/DELETE/transition requests carry `If-Match` after an object's ETag was seen, and that
 * the default API base URL is '/api'.
 * provideUiKit() is deliberately NOT applied at the root: SqDynamicField spreads
 * DEFAULT_FIELD_RENDERERS itself and treats FIELD_RENDERERS as optional overrides, and
 * providing the kit here would add ~418 kB to the eager bundle.
 * [SHELL-AUTOMATION]: the shell contributes automation's trigger/guard editors to the ontology editor and its
 * signals panel to the object detail view.
 */
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SqAutomationSignalsPanel, SqGuardEditor, SqTriggerEditor } from '@sequeless/automation';
import { SEQUELESS_API_BASE_URL } from '@sequeless/core-api';
import { OBJECT_SIGNALS_PANEL } from '@sequeless/objects';
import { ONTOLOGY_EDITOR_PANELS } from '@sequeless/ontology';

import { appConfig } from './app.config';

const ID = '123e4567-e89b-12d3-a456-426614174000';
const URL = `/api/objects/Project/${ID}`;

describe('appConfig [SHELL-CONFIG]', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers!, provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it("sends If-Match on a PUT after a GET recorded the object's ETag", () => {
    http.get(URL).subscribe();
    controller.expectOne(URL).flush({ id: ID, version: 42 }, { headers: { ETag: '"42"' } });

    http.put(URL, { properties: {} }).subscribe();
    const request = controller.expectOne(URL);

    expect(request.request.headers.get('If-Match')).toBe('"42"');
    request.flush({});
  });

  it('does not send If-Match on a PUT when no ETag was recorded', () => {
    http.put(URL, { properties: {} }).subscribe();
    const request = controller.expectOne(URL);

    expect(request.request.headers.has('If-Match')).toBe(false);
    request.flush({});
  });

  it('uses /api as the default API base URL', () => {
    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('/api');
  });

  it('contributes the automation editors and the signals panel [SHELL-AUTOMATION]', () => {
    const components = TestBed.inject(ONTOLOGY_EDITOR_PANELS).map((p) => p.component);
    expect(components).toEqual(expect.arrayContaining([SqTriggerEditor, SqGuardEditor]));
    expect(TestBed.inject(OBJECT_SIGNALS_PANEL)).toBe(SqAutomationSignalsPanel);
  });
});
