/*
 * Contract [CORE-ERR]: rethrowAsApiError(mapper) is a MonoTypeOperatorFunction
 * that leaves success values and completion untouched, and on error rethrows
 * mapper(toApiError(e)) when e is an HttpErrorResponse, or mapper(e) as-is
 * otherwise.
 */
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, lastValueFrom, Observable, of, throwError } from 'rxjs';
import { toArray } from 'rxjs/operators';

import { rethrowAsApiError } from './api-error-operator';

function problemError(): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 409,
    statusText: 'Conflict',
    url: '/api/objects/Task/abc',
    error: {
      type: 'https://sequeless.dev/problems/version-conflict',
      title: 'Version conflict',
      detail: 'The object was modified concurrently.',
    },
  });
}

describe('rethrowAsApiError', () => {
  it('passes success values through unchanged', async () => {
    const values = await lastValueFrom(of(1, 2, 3).pipe(rethrowAsApiError((e) => e), toArray()));

    expect(values).toEqual([1, 2, 3]);
  });

  it('does not invoke the mapper when the source completes without error', async () => {
    const mapper = vi.fn((e: unknown) => e);

    await lastValueFrom(of('ok').pipe(rethrowAsApiError(mapper)));

    expect(mapper).not.toHaveBeenCalled();
  });

  it('maps an HttpErrorResponse with a problem+json body to mapper(toApiError(err))', async () => {
    const err = problemError();
    const mapper = vi.fn((e: unknown) => ({ mapped: e }));

    await expect(
      firstValueFrom(throwError(() => err).pipe(rethrowAsApiError(mapper))),
    ).rejects.toEqual({
      mapped: expect.objectContaining({
        kind: 'problem',
        status: 409,
        title: 'Version conflict',
        slug: 'version-conflict',
      }),
    });
  });

  it('calls the mapper with the converted ApiError, not the raw HttpErrorResponse', async () => {
    const err = problemError();
    const mapper = vi.fn((e: unknown) => e);

    await firstValueFrom(throwError(() => err).pipe(rethrowAsApiError(mapper))).catch(() => undefined);

    expect(mapper).toHaveBeenCalledTimes(1);
    const received = mapper.mock.calls[0][0];
    expect(received).not.toBeInstanceOf(HttpErrorResponse);
    expect(received).toMatchObject({ kind: 'problem', status: 409 });
  });

  it('maps a network HttpErrorResponse (status 0) to an ApiError of kind network', async () => {
    const err = new HttpErrorResponse({ status: 0, statusText: '', url: '/api/objects/Task' });
    const mapper = vi.fn((e: unknown) => e);

    await firstValueFrom(throwError(() => err).pipe(rethrowAsApiError(mapper))).catch(() => undefined);

    expect(mapper).toHaveBeenCalledWith(expect.objectContaining({ kind: 'network', status: 0 }));
  });

  it('passes a non-HTTP error to the mapper unchanged', async () => {
    const original = new Error('boom');
    const mapper = vi.fn((e: unknown) => e);

    await firstValueFrom(throwError(() => original).pipe(rethrowAsApiError(mapper))).catch(
      () => undefined,
    );

    expect(mapper).toHaveBeenCalledWith(original);
  });

  it('does not run a non-HTTP error through toApiError before handing it to the mapper', async () => {
    const original = { some: 'plain-object-error' };
    const mapper = vi.fn((e: unknown) => e);

    await firstValueFrom(throwError(() => original).pipe(rethrowAsApiError(mapper))).catch(
      () => undefined,
    );

    expect(mapper).toHaveBeenCalledWith(original);
  });

  it('rethrows whatever the mapper returns as the resulting error', async () => {
    const sentinel = new Error('mapped-sentinel');
    const mapper = vi.fn(() => sentinel);

    await expect(
      firstValueFrom(throwError(() => problemError()).pipe(rethrowAsApiError(mapper))),
    ).rejects.toBe(sentinel);
  });

  it('emits values that arrive before an error, then rejects with the mapped error', async () => {
    const original = new Error('late failure');
    const mapper = vi.fn(() => 'mapped-late-failure');
    const seen: number[] = [];
    const source = new Observable<number>((subscriber) => {
      subscriber.next(1);
      subscriber.next(2);
      subscriber.error(original);
    });

    const result$ = source.pipe(rethrowAsApiError(mapper));
    await new Promise<void>((resolve) => {
      result$.subscribe({
        next: (v) => seen.push(v),
        error: (e) => {
          expect(e).toBe('mapped-late-failure');
          resolve();
        },
      });
    });

    expect(seen).toEqual([1, 2]);
    expect(mapper).toHaveBeenCalledWith(original);
  });
});
