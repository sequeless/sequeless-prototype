import { HttpErrorResponse } from '@angular/common/http';

import { OntologyIssue, ViolationResponse } from '../models/index';

export interface ApiError {
  status: number;
  kind: 'problem' | 'legacy-message' | 'ontology-report' | 'spring-default' | 'network' | 'unknown';
  title: string;
  detail: string | null;
  slug: string | null;
  violations: readonly ViolationResponse[];
  issues: readonly OntologyIssue[];
  expectedVersion: number | null;
  transitionName: string | null;
  typeIri: string | null;
  signalName: string | null;
  raw: unknown;
}

const PROBLEM_PREFIX = 'https://sequeless.dev/problems/';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

function nullableNumber(value: unknown): number | null {
  return typeof value === 'number' ? value : null;
}

function titleFrom(err: HttpErrorResponse): string {
  if (typeof err.statusText === 'string' && err.statusText.length > 0) {
    return err.statusText;
  }
  return 'Request failed';
}

export function toApiError(err: HttpErrorResponse): ApiError {
  if (err.status === 0) {
    return {
      status: 0,
      kind: 'network',
      title: err.statusText.length ? err.statusText : 'Network error',
      detail: null,
      slug: null,
      violations: [],
      issues: [],
      expectedVersion: null,
      transitionName: null,
      typeIri: null,
      signalName: null,
      raw: err.error,
    };
  }

  const body = err.error;

  if (isRecord(body) && typeof body["type"] === 'string' && body["type"].startsWith(PROBLEM_PREFIX)) {
    const type = body["type"];
    const slash = type.lastIndexOf('/');
    const violations = Array.isArray(body["violations"])
      ? (body["violations"] as readonly ViolationResponse[])
      : [];
    return {
      status: err.status,
      kind: 'problem',
      title: typeof body["title"] === 'string' ? body["title"] : '',
      detail: nullableString(body["detail"]),
      slug: slash >= 0 ? type.slice(slash + 1) : type,
      violations,
      issues: [],
      expectedVersion: nullableNumber(body["expectedVersion"]),
      transitionName: nullableString(body["transitionName"]),
      typeIri: nullableString(body["typeIri"]),
      signalName: nullableString(body["signalName"]),
      raw: body,
    };
  }

  if (
    isRecord(body) &&
    'timestamp' in body &&
    'status' in body &&
    'error' in body &&
    'path' in body
  ) {
    return {
      status: err.status,
      kind: 'spring-default',
      title: typeof body["error"] === 'string' ? body["error"] : '',
      detail: nullableString(body["message"]),
      slug: null,
      violations: [],
      issues: [],
      expectedVersion: null,
      transitionName: null,
      typeIri: null,
      signalName: null,
      raw: body,
    };
  }

  if (isRecord(body) && typeof body["consistent"] === 'boolean' && Array.isArray(body["issues"])) {
    return {
      status: err.status,
      kind: 'ontology-report',
      title: 'Ontology import rejected',
      detail: null,
      slug: null,
      violations: [],
      issues: body["issues"] as readonly OntologyIssue[],
      expectedVersion: null,
      transitionName: null,
      typeIri: null,
      signalName: null,
      raw: body,
    };
  }

  if (isRecord(body) && typeof body["message"] === 'string') {
    return {
      status: err.status,
      kind: 'legacy-message',
      title: titleFrom(err),
      detail: body["message"],
      slug: null,
      violations: [],
      issues: [],
      expectedVersion: null,
      transitionName: null,
      typeIri: null,
      signalName: null,
      raw: body,
    };
  }

  return {
    status: err.status,
    kind: 'unknown',
    title: titleFrom(err),
    detail: null,
    slug: null,
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: body,
  };
}
