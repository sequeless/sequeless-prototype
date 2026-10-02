import { HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { ETagRegistry } from './etag-registry';

const UUID_SEGMENT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const etagInterceptor: HttpInterceptorFn = (request, next) => {
  const registry = inject(ETagRegistry);

  const outgoing = withIfMatch(request, registry);

  return next(outgoing).pipe(
    tap((event) => recordEtag(event, registry)),
  );
};

function withIfMatch(request: HttpRequest<unknown>, registry: ETagRegistry): HttpRequest<unknown> {
  const id = objectIdFromUrl(request.url);
  if (id === null) {
    return request;
  }
  if (!isEligibleForIfMatch(request)) {
    return request;
  }
  if (request.headers.has('If-Match')) {
    return request;
  }
  const version = registry.get(id);
  if (version === undefined) {
    return request;
  }
  return request.clone({ setHeaders: { 'If-Match': `"${version}"` } });
}

function objectIdFromUrl(url: string): string | null {
  const segments = url.split('/');
  for (const segment of segments) {
    if (UUID_SEGMENT.test(segment)) {
      return segment;
    }
  }
  return null;
}

function isEligibleForIfMatch(request: HttpRequest<unknown>): boolean {
  if (request.method === 'PUT' || request.method === 'DELETE') {
    return true;
  }
  return request.method === 'POST' && request.url.includes('/transitions/');
}

function recordEtag(event: HttpEvent<unknown>, registry: ETagRegistry): void {
  if (!(event instanceof HttpResponse)) {
    return;
  }
  if (!isRecordWithStringId(event.body)) {
    return;
  }
  const etag = event.headers.get('ETag');
  if (etag === null) {
    return;
  }
  registry.set(event.body.id, etag);
}

function isRecordWithStringId(body: unknown): body is { id: string } {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const id = (body as Record<string, unknown>)['id'];
  return typeof id === 'string';
}
