/**
 * `pickLabel(object, targetType)` builds the display label shown to the user for a
 * resolved target object (e.g. in a relationship picker).
 *
 * It walks `targetType.properties` in order and returns the value of the first
 * non-hidden `AttributePropertyResponse` with `datatype: 'STRING'` whose value in
 * `object.properties` (keyed by the property's short *name*, not its IRI) is a
 * non-empty string. Relationship properties and non-STRING attributes are skipped
 * entirely. If no candidate yields a usable value, it falls back to `object.id`.
 */
import type { BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { isAttributeProperty } from '@sequeless/core-api';

export function pickLabel(object: BusinessObjectResponse, targetType: TypeDetailResponse): string {
  for (const property of targetType.properties) {
    if (!isAttributeProperty(property)) continue;
    if (property.hidden) continue;
    if (property.datatype !== 'STRING') continue;

    const value = object.properties[property.name];
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  }
  return object.id;
}
