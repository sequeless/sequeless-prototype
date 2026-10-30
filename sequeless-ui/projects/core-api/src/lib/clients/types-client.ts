import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';

import type { TypeDetailResponse, TypeSummaryResponse } from '../models';
import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { rethrowAsApiError } from './api-error-operator';

@Injectable({ providedIn: 'root' })
export class TypesClient {
  private readonly http = inject(HttpClient);
  private readonly base = inject(SEQUELESS_API_BASE_URL, { optional: true }) ?? '/api';
  private readonly mapError = inject(SEQUELESS_ERROR_MAPPER, { optional: true }) ?? ((e: unknown) => e);

  list(): Observable<TypeSummaryResponse[]> {
    return this.http
      .get<TypeSummaryResponse[]>(`${this.base}/types`)
      .pipe(rethrowAsApiError(this.mapError));
  }

  describe(nameOrIri: string): Observable<TypeDetailResponse> {
    return this.http
      .get<TypeDetailResponse>(`${this.base}/types/${encodeURIComponent(nameOrIri)}`)
      .pipe(rethrowAsApiError(this.mapError));
  }
}
