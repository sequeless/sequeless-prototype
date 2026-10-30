/**
 * `normalizeProblems(input)` flattens either shape the server reports problems in (phase-5 spec,
 * "sq-problem-banner") into a single `NormalizedProblem[]` the banner (and Phase 7's per-field
 * routing) can render uniformly.
 *
 * A bare `readonly OntologyIssue[]` is treated as-is: each issue's `severity` string maps
 * case-insensitively — `'ERROR'` to `'error'`, `'WARNING'`/`'WARN'` to `'warning'`, anything else
 * to `'info'` — `subject` comes from `subjectIri`, and `message` passes through unchanged. Order
 * is preserved and an empty array yields `[]`.
 *
 * An `ApiError` yields its `issues` (same severity mapping and shape) followed by its
 * `violations`, each violation always normalized to severity `'error'` with `subject` taken from the
 * violation's `property` (its field name, not `propertyIri`) and `message` from the violation's
 * `message`. When an `ApiError` has neither issues nor violations, normalization falls back to a
 * single `{ severity: 'error', subject: null, message }` entry, where `message` is `detail` when
 * present and `title` otherwise.
 */
import type { ApiError, OntologyIssue } from '@sequeless/core-api';

export interface NormalizedProblem {
  severity: 'error' | 'warning' | 'info';
  subject: string | null;
  message: string;
}

function mapSeverity(severity: string): NormalizedProblem['severity'] {
  switch (severity.toLowerCase()) {
    case 'error':
      return 'error';
    case 'warning':
    case 'warn':
      return 'warning';
    default:
      return 'info';
  }
}

function normalizeIssue(issue: OntologyIssue): NormalizedProblem {
  return {
    severity: mapSeverity(issue.severity),
    subject: issue.subjectIri,
    message: issue.message,
  };
}

function isApiError(value: ApiError | readonly OntologyIssue[]): value is ApiError {
  return !Array.isArray(value);
}

export function normalizeProblems(
  input: ApiError | readonly OntologyIssue[],
): NormalizedProblem[] {
  if (!isApiError(input)) {
    return input.map((issue) => normalizeIssue(issue));
  }

  const problems: NormalizedProblem[] = [];

  for (const issue of input.issues) {
    problems.push(normalizeIssue(issue));
  }

  for (const violation of input.violations) {
    problems.push({
      severity: 'error',
      subject: violation.property,
      message: violation.message,
    });
  }

  if (problems.length === 0) {
    problems.push({
      severity: 'error',
      subject: null,
      message: input.detail ?? input.title,
    });
  }

  return problems;
}
