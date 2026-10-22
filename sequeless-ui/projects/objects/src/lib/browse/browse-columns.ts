import {
  isRelationshipProperty,
  queryCapabilities,
  type Datatype,
  type FilterOperator,
  type PropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100, 200] as const;

const STRING_OPERATORS: readonly FilterOperator[] = [
  'eq',
  'ne',
  'contains',
  'startswith',
  'in',
  'isnull',
  'notnull',
];

const ORDERED_OPERATORS: readonly FilterOperator[] = [
  'eq',
  'ne',
  'gt',
  'gte',
  'lt',
  'lte',
  'isnull',
  'notnull',
];

const BOOLEAN_OPERATORS: readonly FilterOperator[] = ['eq', 'isnull', 'notnull'];

const RELATIONSHIP_OPERATORS: readonly FilterOperator[] = ['eq', 'in', 'isnull', 'notnull'];

const OPERATORS_BY_DATATYPE: Record<Datatype, readonly FilterOperator[]> = {
  STRING: STRING_OPERATORS,
  ANY_URI: STRING_OPERATORS,
  BOOLEAN: BOOLEAN_OPERATORS,
  INTEGER: ORDERED_OPERATORS,
  LONG: ORDERED_OPERATORS,
  DECIMAL: ORDERED_OPERATORS,
  DOUBLE: ORDERED_OPERATORS,
  DATE: ORDERED_OPERATORS,
  TIME: ORDERED_OPERATORS,
  DATE_TIME: ORDERED_OPERATORS,
  DURATION: ORDERED_OPERATORS,
};

/** Non-hidden properties, by `order` ascending (null last), ties broken by name. */
export function visibleColumns(type: TypeDetailResponse): PropertyResponse[] {
  return type.properties
    .filter((p) => !p.hidden)
    .slice()
    .sort((a, b) => {
      const aOrder = a.order ?? Number.POSITIVE_INFINITY;
      const bOrder = b.order ?? Number.POSITIVE_INFINITY;
      if (aOrder !== bOrder) {
        return aOrder < bOrder ? -1 : 1;
      }
      return a.name.localeCompare(b.name);
    });
}

/** Names of the properties queryCapabilities reports as facetable. */
export function facetableProperties(type: TypeDetailResponse): string[] {
  return type.properties.filter((p) => queryCapabilities(p).facetable).map((p) => p.name);
}

/** Filter operators applicable to a property, chosen by its datatype or relationship kind. */
export function operatorsFor(property: PropertyResponse): readonly FilterOperator[] {
  if (isRelationshipProperty(property)) {
    return RELATIONSHIP_OPERATORS;
  }
  return OPERATORS_BY_DATATYPE[property.datatype];
}
