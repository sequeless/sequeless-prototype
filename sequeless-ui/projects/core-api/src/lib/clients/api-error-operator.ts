import { HttpErrorResponse } from '@angular/common/http';
import { catchError, MonoTypeOperatorFunction, throwError } from 'rxjs';

import { toApiError } from '../errors/api-error';

/**
 * Rethrows a failed request as `mapper(error)`. HTTP failures are converted to an ApiError first;
 * any other error reaches the mapper untouched.
 */
export function rethrowAsApiError<T>(
  mapper: (error: unknown) => unknown,
): MonoTypeOperatorFunction<T> {
  return catchError((error: unknown) =>
    throwError(() => mapper(error instanceof HttpErrorResponse ? toApiError(error) : error)),
  );
}
