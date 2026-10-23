import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, throwError } from 'rxjs';

import type { OntologyImportResponse, OntologyReportResponse } from '../models';
import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { rethrowAsApiError } from './api-error-operator';

export type ImportOutcome =
  | { status: 'accepted'; response: OntologyImportResponse }
  | { status: 'rejected'; report: OntologyReportResponse };

function isReport(body: unknown): body is OntologyReportResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Record<string, unknown>)['consistent'] === 'boolean' &&
    Array.isArray((body as Record<string, unknown>)['issues'])
  );
}

@Injectable({ providedIn: 'root' })
export class OntologyClient {
  private readonly http = inject(HttpClient);
  private readonly base = inject(SEQUELESS_API_BASE_URL, { optional: true }) ?? '/api';
  private readonly mapError = inject(SEQUELESS_ERROR_MAPPER, { optional: true }) ?? ((e: unknown) => e);

  load(): Observable<string> {
    return this.http
      .get(`${this.base}/ontology`, {
        responseType: 'text',
        headers: new HttpHeaders({ Accept: 'text/turtle' }),
      })
      .pipe(rethrowAsApiError(this.mapError));
  }

  import(turtle: string): Observable<ImportOutcome> {
    return this.http
      .post<OntologyImportResponse>(`${this.base}/ontology`, turtle, {
        headers: new HttpHeaders({ 'Content-Type': 'text/turtle' }),
      })
      .pipe(
        map((response): ImportOutcome => ({ status: 'accepted', response })),
        catchError((error: unknown) =>
          error instanceof HttpErrorResponse && error.status === 422 && isReport(error.error)
            ? of<ImportOutcome>({ status: 'rejected', report: error.error })
            : throwError(() => error),
        ),
        rethrowAsApiError(this.mapError),
      );
  }
}
