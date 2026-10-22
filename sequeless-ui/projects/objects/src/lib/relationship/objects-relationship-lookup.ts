import { inject, Injectable, type Provider } from '@angular/core';
import {
  BusinessObjectResponse,
  ObjectsClient,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { RELATIONSHIP_LOOKUP, type RelationshipLookup } from '@sequeless/ui-kit';
import { defer, map, shareReplay, type Observable } from 'rxjs';

const PICKER_PAGE_SIZE = 20;

/** Feeds ui-kit's relationship picker from the generic objects endpoints. */
@Injectable({ providedIn: 'root' })
export class ObjectsRelationshipLookup implements RelationshipLookup {
  private readonly objects = inject(ObjectsClient);
  private readonly types = inject(TypesClient);
  private readonly targetTypes = new Map<string, Observable<TypeDetailResponse>>();

  search(targetTypeIri: string, term: string): Observable<readonly BusinessObjectResponse[]> {
    return this.objects
      .browse(targetTypeIri, {
        page: 0,
        size: PICKER_PAGE_SIZE,
        q: term,
        sort: [],
        filters: [],
        facets: [],
      })
      .pipe(map((result) => result.items));
  }

  targetType(targetTypeIri: string): Observable<TypeDetailResponse> {
    let cached = this.targetTypes.get(targetTypeIri);
    if (cached === undefined) {
      cached = defer(() => this.types.describe(targetTypeIri)).pipe(shareReplay(1));
      this.targetTypes.set(targetTypeIri, cached);
    }
    return cached;
  }
}

export function provideObjectsRelationshipLookup(): Provider[] {
  return [
    ObjectsRelationshipLookup,
    { provide: RELATIONSHIP_LOOKUP, useExisting: ObjectsRelationshipLookup },
  ];
}
