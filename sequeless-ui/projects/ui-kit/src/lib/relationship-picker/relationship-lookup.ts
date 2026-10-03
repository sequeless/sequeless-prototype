import { InjectionToken } from '@angular/core';
import type { BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import type { Observable } from 'rxjs';

/** Data source for sq-relationship-picker; supplied by the feature library (ui-kit makes no HTTP calls). */
export interface RelationshipLookup {
  /** GET /api/objects/{targetTypeIri}?q=<term>&size=20 */
  search(targetTypeIri: string, term: string): Observable<readonly BusinessObjectResponse[]>;
  /** Type detail of the relationship target, used to pick option/chip labels. */
  targetType(targetTypeIri: string): Observable<TypeDetailResponse>;
}

export const RELATIONSHIP_LOOKUP = new InjectionToken<RelationshipLookup>('Sequeless relationship lookup');
