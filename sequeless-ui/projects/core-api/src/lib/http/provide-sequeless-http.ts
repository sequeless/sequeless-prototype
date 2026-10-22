import {
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { EnvironmentProviders, inject, makeEnvironmentProviders } from '@angular/core';
import type { Observable } from 'rxjs';

import { SEQUELESS_HTTP_INTERCEPTORS } from '../tokens';
import { etagInterceptor } from './etag.interceptor';

/**
 * Runs the extra interceptors registered under SEQUELESS_HTTP_INTERCEPTORS, in array order, so
 * consumers can add interceptors without touching provideSequelessHttp.
 */
export const sequelessInterceptors: HttpInterceptorFn = (req, next) => {
  const interceptors = inject(SEQUELESS_HTTP_INTERCEPTORS, { optional: true }) ?? [];
  const chain = interceptors.reduceRight<HttpHandlerFn>(
    (handler, interceptor) => (request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> =>
      interceptor(request, handler),
    next,
  );
  return chain(req);
};

export function provideSequelessHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(withInterceptors([etagInterceptor, sequelessInterceptors])),
  ]);
}
