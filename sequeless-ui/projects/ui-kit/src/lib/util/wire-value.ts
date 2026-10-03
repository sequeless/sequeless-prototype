/**
 * Coerces between a form control's raw value and the JSON-serializable wire shape
 * expected by `ObjectPropertyMapper` on the backend. Pure functions, no Angular
 * imports.
 *
 * - `toWireValue` maps `null`/`undefined`/`''` to `null` and otherwise produces a
 *   plain `string | number | boolean`, never a `Date` or an object.
 * - `fromWireValue` is the inverse and only reconstitutes the datatypes a Material
 *   control cannot accept as raw strings/numbers (`DATE`, `DATE_TIME`); everything
 *   else passes through unchanged.
 */
import type { Datatype } from '@sequeless/core-api';

/**
 * Coerce a form control's raw value into the JSON-serializable shape the wire
 * expects. Never returns a `Date` or an object.
 */
export function toWireValue(datatype: Datatype, raw: unknown): unknown {
  if (raw === null || raw === undefined || raw === '') {
    return null;
  }

  switch (datatype) {
    case 'STRING':
    case 'TIME':
    case 'DURATION':
    case 'ANY_URI':
      return String(raw);

    case 'BOOLEAN':
      if (typeof raw === 'boolean') {
        return raw;
      }
      return String(raw).toLowerCase() === 'true';

    case 'INTEGER':
    case 'LONG':
      if (typeof raw === 'number') {
        return Math.trunc(raw);
      }
      return Math.trunc(Number(raw));

    case 'DECIMAL':
    case 'DOUBLE':
      if (typeof raw === 'number') {
        return raw;
      }
      return Number(raw);

    case 'DATE':
      // Use the local calendar date, never the UTC/ISO date.
      if (raw instanceof Date) {
        const y = raw.getFullYear();
        const m = String(raw.getMonth() + 1).padStart(2, '0');
        const d = String(raw.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
      }
      return String(raw);

    case 'DATE_TIME':
      if (raw instanceof Date) {
        return raw.toISOString();
      }
      return String(raw);
  }
}

/**
 * Inverse of {@link toWireValue}: seed a form control from a `BusinessObjectResponse`
 * property. Only `DATE` and `DATE_TIME` are reconstituted into `Date` objects;
 * every other datatype passes through unchanged.
 */
export function fromWireValue(datatype: Datatype, wire: unknown): unknown {
  if (wire === null || wire === undefined) {
    return null;
  }

  switch (datatype) {
    case 'DATE':
      if (wire instanceof Date) {
        return wire;
      }
      const value = String(wire);
      const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
      if (!match) {
        return new Date(value);
      }
      const year = Number(match[1]);
      const month = Number(match[2]) - 1;
      const day = Number(match[3]);
      const date = new Date(year, month, day);
      if (
        date.getFullYear() !== year ||
        date.getMonth() !== month ||
        date.getDate() !== day
      ) {
        return new Date(value);
      }
      return date;

    case 'DATE_TIME':
      if (wire instanceof Date) {
        return wire;
      }
      return new Date(String(wire));

    default:
      return wire;
  }
}
