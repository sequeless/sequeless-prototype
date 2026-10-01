/*
 * toApiError() normalises every error shape the sequeless backend can emit into one ApiError, so
 * consumers branch on `status` + `kind` + lifted context instead of sniffing bodies. The shapes
 * mirror sequeless-app's ApiExceptionAdvice exactly, and they are deliberately heterogeneous:
 *
 * - ProblemDetail (`application/problem+json`, `type: https://sequeless.dev/problems/<slug>`) for
 *   ObjectsController's handlers: validation, invalid-query, object-not-found, stale-object,
 *   precondition-required, bad-request (PreconditionMismatchException!), transition-not-available,
 *   unknown-signal — each with its own extension properties;
 * - the legacy ErrorResponse `{message}` for TypeNotFoundException (404) and
 *   AuthorizationException (403);
 * - the legacy raw OntologyReportResponse `{consistent, issues}` for OntologyException (422);
 * - Spring Boot's default /error JSON `{timestamp, status, error, path}` for anything no handler
 *   maps (unparseable Turtle -> 500, a malformed UUID path variable -> 400, ...).
 *
 * `raw` always carries the untouched response body, so nothing the mapper does not lift is lost.
 */
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { OntologyIssue, ViolationResponse } from '../models/index';
import { ApiError, toApiError } from './api-error';

const PROBLEM_JSON = 'application/problem+json';
const JSON_TYPE = 'application/json';
const PROJECT_ID = '0b9e8f7a-6c5d-4e3f-8a1b-2c3d4e5f6a7b';
const PROJECT_IRI = 'https://sequeless.dev/ns/ref#Project';

function httpError(
  status: number,
  statusText: string,
  body: unknown,
  contentType: string | null,
  url = `http://localhost/api/objects/Project/${PROJECT_ID}`,
): HttpErrorResponse {
  return new HttpErrorResponse({
    error: body,
    headers: contentType === null ? new HttpHeaders() : new HttpHeaders({ 'Content-Type': contentType }),
    status,
    statusText,
    url,
  });
}

function problem(
  slug: string,
  status: number,
  title: string,
  detail: string,
  extensions: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    type: `https://sequeless.dev/problems/${slug}`,
    title,
    status,
    detail,
    instance: `/api/objects/Project/${PROJECT_ID}`,
    ...extensions,
  };
}

describe('toApiError', () => {
  describe('contract', () => {
    it('takes an HttpErrorResponse and returns an ApiError', () => {
      expectTypeOf(toApiError).parameters.toEqualTypeOf<[HttpErrorResponse]>();
      expectTypeOf(toApiError).returns.toEqualTypeOf<ApiError>();
    });

    it('ApiError has exactly the documented fields, with nullable context and readonly arrays', () => {
      expectTypeOf<keyof ApiError>().toEqualTypeOf<
        | 'status'
        | 'kind'
        | 'title'
        | 'detail'
        | 'slug'
        | 'violations'
        | 'issues'
        | 'expectedVersion'
        | 'transitionName'
        | 'typeIri'
        | 'signalName'
        | 'raw'
      >();
      expectTypeOf<ApiError['kind']>().toEqualTypeOf<
        'problem' | 'legacy-message' | 'ontology-report' | 'spring-default' | 'network' | 'unknown'
      >();
      expectTypeOf<ApiError['status']>().toEqualTypeOf<number>();
      expectTypeOf<ApiError['title']>().toEqualTypeOf<string>();
      expectTypeOf<ApiError['detail']>().toEqualTypeOf<string | null>();
      expectTypeOf<ApiError['slug']>().toEqualTypeOf<string | null>();
      expectTypeOf<ApiError['violations']>().toEqualTypeOf<readonly ViolationResponse[]>();
      expectTypeOf<ApiError['issues']>().toEqualTypeOf<readonly OntologyIssue[]>();
      expectTypeOf<ApiError['expectedVersion']>().toEqualTypeOf<number | null>();
      expectTypeOf<ApiError['transitionName']>().toEqualTypeOf<string | null>();
      expectTypeOf<ApiError['typeIri']>().toEqualTypeOf<string | null>();
      expectTypeOf<ApiError['signalName']>().toEqualTypeOf<string | null>();
      expectTypeOf<ApiError['raw']>().toEqualTypeOf<unknown>();
    });
  });

  describe('ProblemDetail bodies (ObjectsController handlers)', () => {
    it('maps 404 object-not-found to kind "problem" with slug, title, detail and every other context field defaulted', () => {
      const body = problem(
        'object-not-found',
        404,
        'Object not found',
        `Object not found or deleted: ${PROJECT_ID}`,
      );

      const apiError = toApiError(httpError(404, 'Not Found', body, PROBLEM_JSON));

      expect(apiError).toEqual({
        status: 404,
        kind: 'problem',
        title: 'Object not found',
        detail: `Object not found or deleted: ${PROJECT_ID}`,
        slug: 'object-not-found',
        violations: [],
        issues: [],
        expectedVersion: null,
        transitionName: null,
        typeIri: null,
        signalName: null,
        raw: body,
      } satisfies ApiError);
    });

    it('derives slug from the last path segment of the problem type URI', () => {
      const body = problem(
        'precondition-required',
        428,
        'Precondition required',
        "A version is required: supply either a body 'version' or an 'If-Match' header",
      );

      const apiError = toApiError(httpError(428, 'Precondition Required', body, PROBLEM_JSON));

      expect(apiError.kind).toBe('problem');
      expect(apiError.status).toBe(428);
      expect(apiError.slug).toBe('precondition-required');
      expect(apiError.detail).toBe(
        "A version is required: supply either a body 'version' or an 'If-Match' header",
      );
    });

    it('lifts violations from a 400 validation problem, including object-level violations with null property', () => {
      const violations: ViolationResponse[] = [
        {
          property: 'estimatedHours',
          propertyIri: 'https://sequeless.dev/ns/ref#estimatedHours',
          message: 'Value 2000 is greater than the maximum 1000',
        },
        { property: null, propertyIri: null, message: 'Object-level constraint failed' },
      ];
      const body = problem(
        'validation',
        400,
        'Validation failed',
        'Validation failed (SHACL): 2 violation(s): [...]',
        { source: 'shacl', violations },
      );

      const apiError = toApiError(httpError(400, 'Bad Request', body, PROBLEM_JSON));

      expect(apiError.kind).toBe('problem');
      expect(apiError.slug).toBe('validation');
      expect(apiError.title).toBe('Validation failed');
      expect(apiError.violations).toEqual(violations);
      // ApiError has no `source` field: the "structural" | "shacl" discriminator stays reachable
      // through `raw`, which must be the untouched body.
      expect(apiError.raw).toBe(body);
      expect(apiError.raw).toMatchObject({ source: 'shacl' });
    });

    it('lifts violations from a 400 invalid-query problem', () => {
      const violations: ViolationResponse[] = [
        {
          property: 'totalEstimatedHours',
          propertyIri: 'https://sequeless.dev/ns/ref#totalEstimatedHours',
          message: 'Derived property is not materialised and cannot be filtered or sorted',
        },
      ];
      const body = problem('invalid-query', 400, 'Invalid query', 'Invalid query: 1 violation(s)', {
        violations,
      });

      const apiError = toApiError(httpError(400, 'Bad Request', body, PROBLEM_JSON));

      expect(apiError.slug).toBe('invalid-query');
      expect(apiError.violations).toEqual(violations);
    });

    it('lifts expectedVersion from a 409 stale-object problem', () => {
      const body = problem(
        'stale-object',
        409,
        'Stale object version',
        `Stale version for ${PROJECT_ID}: expected 3`,
        { expectedVersion: 3 },
      );

      const apiError = toApiError(httpError(409, 'Conflict', body, PROBLEM_JSON));

      expect(apiError.kind).toBe('problem');
      expect(apiError.status).toBe(409);
      expect(apiError.slug).toBe('stale-object');
      expect(apiError.expectedVersion).toBe(3);
      expect(apiError.transitionName).toBeNull();
    });

    it('lifts transitionName from a 409 transition-not-available problem, with the guard reason as detail', () => {
      const body = problem(
        'transition-not-available',
        409,
        'Transition not available',
        'Project must have an owner before it can be activated',
        { transitionName: 'activate' },
      );

      const apiError = toApiError(httpError(409, 'Conflict', body, PROBLEM_JSON));

      expect(apiError.slug).toBe('transition-not-available');
      expect(apiError.transitionName).toBe('activate');
      expect(apiError.detail).toBe('Project must have an owner before it can be activated');
      expect(apiError.expectedVersion).toBeNull();
    });

    it('lifts typeIri and signalName from a 400 unknown-signal problem', () => {
      const body = problem(
        'unknown-signal',
        400,
        'Unknown signal',
        `No transition on type '${PROJECT_IRI}' declares signal 'explode'`,
        { typeIri: PROJECT_IRI, signalName: 'explode' },
      );

      const apiError = toApiError(
        httpError(
          400,
          'Bad Request',
          body,
          PROBLEM_JSON,
          `http://localhost/api/objects/Project/${PROJECT_ID}/signals/explode`,
        ),
      );

      expect(apiError.slug).toBe('unknown-signal');
      expect(apiError.typeIri).toBe(PROJECT_IRI);
      expect(apiError.signalName).toBe('explode');
    });

    it('normalises PreconditionMismatchException faithfully as a 400 problem with slug "bad-request", never "precondition-mismatch"', () => {
      // ApiExceptionAdvice#handlePreconditionMismatch sets
      // type = https://sequeless.dev/problems/bad-request (title "Conflicting version") for a PUT
      // whose body `version` disagrees with its If-Match header. There is no
      // `precondition-mismatch` slug on the wire: the mapper must report what the server sent,
      // and consumers must identify this case from status 400 + request context (a PUT carrying
      // both a body version and If-Match), not from a slug that does not exist.
      const body = problem(
        'bad-request',
        400,
        'Conflicting version',
        'Body version 3 does not match If-Match version 4',
      );

      const apiError = toApiError(httpError(400, 'Bad Request', body, PROBLEM_JSON));

      expect(apiError.kind).toBe('problem');
      expect(apiError.status).toBe(400);
      expect(apiError.slug).toBe('bad-request');
      expect(apiError.slug).not.toBe('precondition-mismatch');
      expect(apiError.title).toBe('Conflicting version');
      expect(apiError.detail).toBe('Body version 3 does not match If-Match version 4');
      expect(apiError.expectedVersion).toBeNull();
    });

    it('detects a ProblemDetail from its body even when the content type is plain application/json', () => {
      const body = problem(
        'stale-object',
        409,
        'Stale object version',
        `Stale version for ${PROJECT_ID}: expected 7`,
        { expectedVersion: 7 },
      );

      const apiError = toApiError(httpError(409, 'Conflict', body, JSON_TYPE));

      expect(apiError.kind).toBe('problem');
      expect(apiError.slug).toBe('stale-object');
      expect(apiError.expectedVersion).toBe(7);
    });

    it('detects a ProblemDetail from its body even when no content type header is present', () => {
      const body = problem('object-not-found', 404, 'Object not found', 'gone');

      const apiError = toApiError(httpError(404, 'Not Found', body, null));

      expect(apiError.kind).toBe('problem');
      expect(apiError.slug).toBe('object-not-found');
    });
  });

  describe('legacy {message} bodies (ErrorResponse)', () => {
    it('maps 404 TypeNotFoundException to kind "legacy-message" with the message as detail', () => {
      const body = { message: "No type found for 'DoesNotExist'" };

      const apiError = toApiError(
        httpError(404, 'Not Found', body, JSON_TYPE, 'http://localhost/api/types/DoesNotExist'),
      );

      expect(apiError.kind).toBe('legacy-message');
      expect(apiError.status).toBe(404);
      expect(apiError.detail).toBe("No type found for 'DoesNotExist'");
      expect(apiError.slug).toBeNull();
      expect(apiError.title.length).toBeGreaterThan(0);
      expect(apiError.violations).toEqual([]);
      expect(apiError.issues).toEqual([]);
      expect(apiError.raw).toBe(body);
    });

    it('maps 403 AuthorizationException to kind "legacy-message" with the decision reason as detail', () => {
      const body = { message: 'Principal alice lacks role editor for tenant acme' };

      const apiError = toApiError(
        httpError(403, 'Forbidden', body, JSON_TYPE, 'http://localhost/api/whoami'),
      );

      expect(apiError.kind).toBe('legacy-message');
      expect(apiError.status).toBe(403);
      expect(apiError.detail).toBe('Principal alice lacks role editor for tenant acme');
      expect(apiError.slug).toBeNull();
    });
  });

  describe('404 tolerates both body shapes', () => {
    it('yields status 404 and a usable detail for both the legacy {message} and the ProblemDetail shape', () => {
      const legacy = toApiError(
        httpError(
          404,
          'Not Found',
          { message: "No type found for 'Nope'" },
          JSON_TYPE,
          'http://localhost/api/types/Nope',
        ),
      );
      const problemShape = toApiError(
        httpError(
          404,
          'Not Found',
          problem('object-not-found', 404, 'Object not found', `Object not found or deleted: ${PROJECT_ID}`),
          PROBLEM_JSON,
        ),
      );

      for (const apiError of [legacy, problemShape]) {
        expect(apiError.status).toBe(404);
        expect(apiError.detail).not.toBeNull();
        expect(apiError.title.length).toBeGreaterThan(0);
      }
      expect(legacy.kind).toBe('legacy-message');
      expect(legacy.slug).toBeNull();
      expect(problemShape.kind).toBe('problem');
      expect(problemShape.slug).toBe('object-not-found');
    });
  });

  describe('legacy OntologyReportResponse bodies', () => {
    it('maps a 422 {consistent:false, issues} body to kind "ontology-report" and lifts the issues', () => {
      const issues: OntologyIssue[] = [
        {
          severity: 'ERROR',
          subjectIri: 'https://sequeless.dev/ns/ref#Task',
          message: 'Class is unsatisfiable (owl:disjointWith ex:Project)',
        },
        { severity: 'WARNING', subjectIri: null, message: 'Ontology declares no owl:imports' },
      ];
      const body = { consistent: false, issues };

      const apiError = toApiError(
        httpError(422, 'Unprocessable Entity', body, JSON_TYPE, 'http://localhost/api/ontology'),
      );

      expect(apiError.kind).toBe('ontology-report');
      expect(apiError.status).toBe(422);
      expect(apiError.issues).toEqual(issues);
      expect(apiError.slug).toBeNull();
      expect(apiError.violations).toEqual([]);
      expect(apiError.title.length).toBeGreaterThan(0);
      expect(apiError.raw).toBe(body);
    });
  });

  describe("Spring Boot's default error JSON", () => {
    it('maps a 500 {timestamp,status,error,path} body (e.g. unparseable Turtle) to kind "spring-default"', () => {
      const body = {
        timestamp: '2026-09-28T10:15:30.123+00:00',
        status: 500,
        error: 'Internal Server Error',
        path: '/api/ontology',
      };

      const apiError = toApiError(
        httpError(500, 'Internal Server Error', body, JSON_TYPE, 'http://localhost/api/ontology'),
      );

      expect(apiError.kind).toBe('spring-default');
      expect(apiError.status).toBe(500);
      expect(apiError.title).toBe('Internal Server Error');
      expect(apiError.detail).toBeNull();
      expect(apiError.slug).toBeNull();
      expect(apiError.raw).toBe(body);
    });

    it('maps a 400 default error body (e.g. a malformed UUID path variable) to kind "spring-default"', () => {
      const body = {
        timestamp: '2026-09-28T10:15:30.123+00:00',
        status: 400,
        error: 'Bad Request',
        path: '/api/objects/Project/not-a-uuid',
      };

      const apiError = toApiError(
        httpError(400, 'Bad Request', body, JSON_TYPE, 'http://localhost/api/objects/Project/not-a-uuid'),
      );

      expect(apiError.kind).toBe('spring-default');
      expect(apiError.status).toBe(400);
      expect(apiError.title).toBe('Bad Request');
      expect(apiError.slug).toBeNull();
    });

    it('keeps a default error body that also carries "message" as "spring-default" (not "legacy-message"), using message as detail', () => {
      // server.error.include-message=always adds `message` to the default body; it must not be
      // mistaken for the single-field legacy ErrorResponse.
      const body = {
        timestamp: '2026-09-28T10:15:30.123+00:00',
        status: 500,
        error: 'Internal Server Error',
        message: 'Turtle parse error at line 3',
        path: '/api/ontology',
      };

      const apiError = toApiError(
        httpError(500, 'Internal Server Error', body, JSON_TYPE, 'http://localhost/api/ontology'),
      );

      expect(apiError.kind).toBe('spring-default');
      expect(apiError.detail).toBe('Turtle parse error at line 3');
    });
  });

  describe('transport failures and unrecognised bodies', () => {
    it('maps status 0 (no response reached the client) to kind "network"', () => {
      const progress = new ProgressEvent('error');

      const apiError = toApiError(httpError(0, 'Unknown Error', progress, null));

      expect(apiError.kind).toBe('network');
      expect(apiError.status).toBe(0);
      expect(apiError.title.length).toBeGreaterThan(0);
      expect(apiError.slug).toBeNull();
      expect(apiError.violations).toEqual([]);
      expect(apiError.issues).toEqual([]);
    });

    it('maps an unrecognised JSON body to kind "unknown" and preserves it in raw', () => {
      const body = { unexpected: true, nested: { reason: 'proxy said no' } };

      const apiError = toApiError(httpError(502, 'Bad Gateway', body, JSON_TYPE));

      expect(apiError.kind).toBe('unknown');
      expect(apiError.status).toBe(502);
      expect(apiError.raw).toBe(body);
      expect(apiError.title.length).toBeGreaterThan(0);
      expect(apiError.slug).toBeNull();
      expect(apiError.expectedVersion).toBeNull();
    });

    it('maps a non-JSON (HTML string) body to kind "unknown" and preserves it in raw', () => {
      const body = '<html><body><h1>503 Service Unavailable</h1></body></html>';

      const apiError = toApiError(httpError(503, 'Service Unavailable', body, 'text/html'));

      expect(apiError.kind).toBe('unknown');
      expect(apiError.status).toBe(503);
      expect(apiError.raw).toBe(body);
    });

    it('maps a null body with a non-zero status to kind "unknown"', () => {
      const apiError = toApiError(httpError(500, 'Internal Server Error', null, null));

      expect(apiError.kind).toBe('unknown');
      expect(apiError.status).toBe(500);
      expect(apiError.raw).toBeNull();
    });
  });
});
