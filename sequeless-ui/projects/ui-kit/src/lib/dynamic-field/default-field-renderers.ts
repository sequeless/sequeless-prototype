import type { FieldRendererMap } from '@sequeless/core-api';

import { SqRelationshipPicker } from '../relationship-picker/relationship-picker';
import { SqFieldAnyUri } from './fields/field-any-uri';
import { SqFieldBoolean } from './fields/field-boolean';
import { SqFieldDate } from './fields/field-date';
import { SqFieldDateTime } from './fields/field-date-time';
import { SqFieldDecimal } from './fields/field-decimal';
import { SqFieldDuration } from './fields/field-duration';
import { SqFieldInteger } from './fields/field-integer';
import { SqFieldString } from './fields/field-string';
import { SqFieldTime } from './fields/field-time';

/** Default datatype -> renderer map behind FIELD_RENDERERS (per-key overridable). */
export const DEFAULT_FIELD_RENDERERS: FieldRendererMap = Object.freeze({
  STRING: SqFieldString,
  BOOLEAN: SqFieldBoolean,
  INTEGER: SqFieldInteger,
  LONG: SqFieldInteger,
  DECIMAL: SqFieldDecimal,
  DOUBLE: SqFieldDecimal,
  DATE: SqFieldDate,
  TIME: SqFieldTime,
  DATE_TIME: SqFieldDateTime,
  DURATION: SqFieldDuration,
  ANY_URI: SqFieldAnyUri,
  RELATIONSHIP: SqRelationshipPicker,
});
