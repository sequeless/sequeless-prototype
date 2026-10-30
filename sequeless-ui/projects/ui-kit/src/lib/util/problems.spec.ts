/**
 * Contract under test (lib/util/problems.ts): `normalizeProblems(input)` flattens either shape the
 * server reports problems in (phase-5 spec, "sq-problem-banner") into a single
 * `NormalizedProblem[]` the banner (and Phase 7's per-field routing) can render uniformly.
 *
 * A bare `readonly OntologyIssue[]` is treated as-is: each issue's `severity` string maps
 * case-insensitively — `'ERROR'` to `'error'`, `'WARNING'`/`'WARN'` to `'warning'`, anything else
 * to `'info'` — `subject` comes from `subjectIri`, and `message` passes through unchanged. Order is
 * preserved and an empty array yields `[]`.
 *
 * An `ApiError` (lib/errors/api-error.ts) yields its `issues` (same severity mapping and shape)
 * followed by its `violations`, each violation always normalized to severity `'error'` with
 * `subject` taken from the violation's `property` (its field name, not `propertyIri`) and
 * `message` from the violation's `message`. When an `ApiError` has neither issues nor violations,
 * normalization falls back to a single `{ severity: 'error', subject: null, message }` entry,
 * where `message` is `detail` when present and `title` otherwise.
 */
import type { ApiError, OntologyIssue, ViolationResponse } from '@sequeless/core-api';
import { normalizeProblems } from './problems';

function makeIssue(severity: string, subjectIri: string | null, message: string): OntologyIssue {
  return { severity, subjectIri, message };
}

function makeViolation(property: string | null, message: string): ViolationResponse {
  return { property, propertyIri: `${property ?? 'unknown'}-iri`, message };
}

function makeApiError(overrides: Partial<ApiError> = {}): ApiError {
  return {
    status: 422,
    kind: 'problem',
    title: 'Request failed',
    detail: null,
    slug: 'validation-error',
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: null,
    ...overrides,
  };
}

describe('normalizeProblems', () => {
  describe('given an array of OntologyIssue', () => {
    it('maps an upper-case ERROR severity to "error" and copies subjectIri/message', () => {
      const issues = [makeIssue('ERROR', 'https://example.test/x', 'inconsistent axiom')];
      expect(normalizeProblems(issues)).toEqual([
        { severity: 'error', subject: 'https://example.test/x', message: 'inconsistent axiom' },
      ]);
    });

    it('maps "warning" and "WARN" both to "warning"', () => {
      const issues = [
        makeIssue('warning', 'https://example.test/a', 'first'),
        makeIssue('WARN', 'https://example.test/b', 'second'),
      ];
      expect(normalizeProblems(issues)).toEqual([
        { severity: 'warning', subject: 'https://example.test/a', message: 'first' },
        { severity: 'warning', subject: 'https://example.test/b', message: 'second' },
      ]);
    });

    it('maps any other severity string to "info"', () => {
      const issues = [makeIssue('notice', null, 'fyi')];
      expect(normalizeProblems(issues)).toEqual([
        { severity: 'info', subject: null, message: 'fyi' },
      ]);
    });

    it('preserves the input order', () => {
      const issues = [
        makeIssue('error', null, 'one'),
        makeIssue('info', null, 'two'),
        makeIssue('warning', null, 'three'),
      ];
      expect(normalizeProblems(issues).map((p) => p.message)).toEqual(['one', 'two', 'three']);
    });

    it('returns [] for an empty array', () => {
      expect(normalizeProblems([])).toEqual([]);
    });
  });

  describe('given an ApiError', () => {
    it('maps issues using the same severity rules as a bare array', () => {
      const error = makeApiError({
        issues: [makeIssue('WARN', 'https://example.test/x', 'needs review')],
      });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'warning', subject: 'https://example.test/x', message: 'needs review' },
      ]);
    });

    it('appends violations after issues, each forced to severity "error"', () => {
      const error = makeApiError({
        issues: [makeIssue('info', 'https://example.test/x', 'informational')],
        violations: [makeViolation('title', 'must not be blank')],
      });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'info', subject: 'https://example.test/x', message: 'informational' },
        { severity: 'error', subject: 'title', message: 'must not be blank' },
      ]);
    });

    it("uses a violation's property (field name) as subject, not propertyIri", () => {
      const error = makeApiError({ violations: [makeViolation('status', 'invalid value')] });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'error', subject: 'status', message: 'invalid value' },
      ]);
    });

    it('preserves violation order when there are multiple', () => {
      const error = makeApiError({
        violations: [makeViolation('title', 'first'), makeViolation('status', 'second')],
      });
      expect(normalizeProblems(error).map((p) => p.subject)).toEqual(['title', 'status']);
    });

    it('falls back to a single error entry using detail when issues and violations are both empty', () => {
      const error = makeApiError({ detail: 'Validation failed', title: 'Unprocessable Entity' });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'error', subject: null, message: 'Validation failed' },
      ]);
    });

    it('falls back to title when detail is null and issues and violations are both empty', () => {
      const error = makeApiError({ detail: null, title: 'Unprocessable Entity' });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'error', subject: null, message: 'Unprocessable Entity' },
      ]);
    });

    it('does not use the fallback entry when violations are present but issues are empty', () => {
      const error = makeApiError({
        detail: 'Validation failed',
        violations: [makeViolation('title', 'must not be blank')],
      });
      expect(normalizeProblems(error)).toEqual([
        { severity: 'error', subject: 'title', message: 'must not be blank' },
      ]);
    });
  });
});
