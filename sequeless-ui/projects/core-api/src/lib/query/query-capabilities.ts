import { PropertyResponse } from '../models';

export interface QueryCapabilities {
  filterable: boolean;
  sortable: boolean;
  facetable: boolean;
}

function none(): QueryCapabilities {
  return { filterable: false, sortable: false, facetable: false };
}

export function queryCapabilities(property: PropertyResponse): QueryCapabilities {
  if (property.cardinalityMax !== 1) {
    return none();
  }

  const derivation = property.derivation;
  if (derivation !== null && derivation.materialised === false) {
    return none();
  }

  return { filterable: true, sortable: true, facetable: property.facet };
}
