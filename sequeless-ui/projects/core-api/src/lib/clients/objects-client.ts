import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, type Observable } from 'rxjs';

import type {
  BusinessObjectResponse,
  CreateObjectRequest,
  QueryResultResponse,
  TransitionResponse,
} from '../models';
import { toHttpParams, type BrowseQueryState } from '../query/browse-query';
import { SEQUELESS_API_BASE_URL, SEQUELESS_ERROR_MAPPER } from '../tokens';
import { rethrowAsApiError } from './api-error-operator';

/**
 * Thin wrapper over the four generic object endpoints. Concurrency (If-Match) is added by
 * etagInterceptor, so update() never sends a version in the body.
 */
@Injectable({ providedIn: 'root' })
export class ObjectsClient {
  private readonly http = inject(HttpClient);
  private readonly base = inject(SEQUELESS_API_BASE_URL, { optional: true }) ?? '/api';
  private readonly mapError = inject(SEQUELESS_ERROR_MAPPER, { optional: true }) ?? ((e: unknown) => e);

  browse(type: string, state: BrowseQueryState): Observable<QueryResultResponse> {
    return this.http
      .get<QueryResultResponse>(this.typeUrl(type), { params: toHttpParams(state) })
      .pipe(rethrowAsApiError(this.mapError));
  }

  get(type: string, id: string): Observable<BusinessObjectResponse> {
    return this.http
      .get<BusinessObjectResponse>(this.objectUrl(type, id))
      .pipe(rethrowAsApiError(this.mapError));
  }

  create(type: string, request: CreateObjectRequest): Observable<BusinessObjectResponse> {
    return this.http
      .post<BusinessObjectResponse>(this.typeUrl(type), request)
      .pipe(rethrowAsApiError(this.mapError));
  }

  update(
    type: string,
    id: string,
    properties: Record<string, unknown>,
  ): Observable<BusinessObjectResponse> {
    return this.http
      .put<BusinessObjectResponse>(this.objectUrl(type, id), { properties })
      .pipe(rethrowAsApiError(this.mapError));
  }

  delete(type: string, id: string): Observable<void> {
    return this.http
      .delete(this.objectUrl(type, id))
      .pipe(map(() => undefined), rethrowAsApiError(this.mapError));
  }

  transitions(type: string, id: string): Observable<TransitionResponse[]> {
    return this.http
      .get<TransitionResponse[]>(`${this.objectUrl(type, id)}/transitions`)
      .pipe(rethrowAsApiError(this.mapError));
  }

  fireTransition(type: string, id: string, name: string): Observable<BusinessObjectResponse> {
    return this.http
      .post<BusinessObjectResponse>(`${this.objectUrl(type, id)}/transitions/${encodeURIComponent(name)}`, {})
      .pipe(rethrowAsApiError(this.mapError));
  }

  signal(
    type: string,
    id: string,
    name: string,
    payload: Record<string, unknown> = {},
  ): Observable<void> {
    return this.http
      .post(`${this.objectUrl(type, id)}/signals/${encodeURIComponent(name)}`, payload)
      .pipe(map(() => undefined), rethrowAsApiError(this.mapError));
  }

  private typeUrl(type: string): string {
    return `${this.base}/objects/${encodeURIComponent(type)}`;
  }

  private objectUrl(type: string, id: string): string {
    return `${this.typeUrl(type)}/${encodeURIComponent(id)}`;
  }
}
