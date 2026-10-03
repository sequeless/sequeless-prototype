/**
 * Contract under test (lib/util/wire-value.ts): `toWireValue(datatype, raw)` coerces a form
 * control's raw value into the JSON-serializable shape `ObjectPropertyMapper.toWireValue`
 * expects on the wire (see `sequeless-app/.../rest/ObjectPropertyMapper.java` and the phase-5 spec's
 * "Dynamic fields" table) — a plain string/number/boolean/null, never a `Date` or an object.
 * `fromWireValue(datatype, wire)` is the inverse used to seed a form control from a
 * `BusinessObjectResponse` property: it only needs to reconstitute the datatypes a Material
 * control can't accept as raw strings/numbers (`DATE`, `DATE_TIME`), passing everything else
 * through unchanged.
 *
 * `INTEGER`/`LONG` truncate a non-integral number toward zero (`Math.trunc`, matching a Java
 * `(long)` narrowing cast) rather than rounding — pinned explicitly below since the Java mapper
 * itself never constructs an out-of-range `IntegerValue`, so the coercion is this module's own
 * choice, not a transcription of existing backend behaviour.
 */
import type { Datatype } from '@sequeless/core-api';
import { fromWireValue, toWireValue } from './wire-value';

const ALL_DATATYPES: readonly Datatype[] = [
  'STRING',
  'BOOLEAN',
  'INTEGER',
  'LONG',
  'DECIMAL',
  'DOUBLE',
  'DATE',
  'TIME',
  'DATE_TIME',
  'DURATION',
  'ANY_URI',
];

describe('toWireValue', () => {
  describe('empty input', () => {
    it.each(ALL_DATATYPES)('maps null to null for %s', (datatype) => {
      expect(toWireValue(datatype, null)).toBeNull();
    });

    it.each(ALL_DATATYPES)('maps undefined to null for %s', (datatype) => {
      expect(toWireValue(datatype, undefined)).toBeNull();
    });

    it.each(ALL_DATATYPES)('maps the empty string to null for %s', (datatype) => {
      expect(toWireValue(datatype, '')).toBeNull();
    });
  });

  describe('STRING', () => {
    it('passes a string through as-is', () => {
      expect(toWireValue('STRING', 'hello world')).toBe('hello world');
    });
  });

  describe('BOOLEAN', () => {
    it('passes a boolean through as-is', () => {
      expect(toWireValue('BOOLEAN', true)).toBe(true);
      expect(toWireValue('BOOLEAN', false)).toBe(false);
    });

    it('parses the strings "true" and "false"', () => {
      expect(toWireValue('BOOLEAN', 'true')).toBe(true);
      expect(toWireValue('BOOLEAN', 'false')).toBe(false);
    });
  });

  describe('INTEGER / LONG', () => {
    it('parses a numeric string into an integer', () => {
      expect(toWireValue('INTEGER', '42')).toBe(42);
      expect(toWireValue('LONG', '9007199254740')).toBe(9007199254740);
    });

    it('passes an already-integral number through unchanged', () => {
      expect(toWireValue('INTEGER', 7)).toBe(7);
    });

    it('truncates a non-integer number toward zero', () => {
      expect(toWireValue('INTEGER', 3.7)).toBe(3);
      expect(toWireValue('LONG', -3.7)).toBe(-3);
    });
  });

  describe('DECIMAL / DOUBLE', () => {
    it('parses a numeric string into a number', () => {
      expect(toWireValue('DECIMAL', '12.5')).toBe(12.5);
      expect(toWireValue('DOUBLE', '0.1')).toBe(0.1);
    });

    it('passes an already-numeric value through unchanged', () => {
      expect(toWireValue('DECIMAL', 12.5)).toBe(12.5);
    });
  });

  describe('DATE', () => {
    it('formats a Date using its local calendar date, not its UTC/ISO date', () => {
      const date = new Date(2026, 0, 2, 23, 30);
      expect(toWireValue('DATE', date)).toBe('2026-01-02');
    });

    it('passes a YYYY-MM-DD string through unchanged', () => {
      expect(toWireValue('DATE', '2026-01-02')).toBe('2026-01-02');
    });
  });

  describe('TIME', () => {
    it('passes an HH:mm string through unchanged', () => {
      expect(toWireValue('TIME', '23:30')).toBe('23:30');
    });

    it('passes an HH:mm:ss string through unchanged', () => {
      expect(toWireValue('TIME', '23:30:15')).toBe('23:30:15');
    });
  });

  describe('DATE_TIME', () => {
    it('formats a Date as its ISO-8601 instant string', () => {
      const date = new Date(Date.UTC(2026, 0, 2, 23, 30, 0));
      expect(toWireValue('DATE_TIME', date)).toBe(date.toISOString());
    });

    it('passes an ISO instant string through unchanged', () => {
      expect(toWireValue('DATE_TIME', '2026-01-02T23:30:00.000Z')).toBe('2026-01-02T23:30:00.000Z');
    });
  });

  describe('DURATION', () => {
    it('passes an ISO-8601 duration string through unchanged', () => {
      expect(toWireValue('DURATION', 'P1DT2H3M4.5S')).toBe('P1DT2H3M4.5S');
    });
  });

  describe('ANY_URI', () => {
    it('passes a URI string through unchanged', () => {
      expect(toWireValue('ANY_URI', 'https://example.test/widget/1')).toBe(
        'https://example.test/widget/1',
      );
    });
  });

  describe('return shape', () => {
    it('never returns a Date instance', () => {
      expect(toWireValue('DATE', new Date(2026, 0, 2)) instanceof Date).toBe(false);
      expect(toWireValue('DATE_TIME', new Date(2026, 0, 2)) instanceof Date).toBe(false);
    });

    it('never returns an object', () => {
      for (const datatype of ALL_DATATYPES) {
        expect(typeof toWireValue(datatype, 'x')).not.toBe('object');
      }
    });
  });
});

describe('fromWireValue', () => {
  describe('null', () => {
    it.each(ALL_DATATYPES)('maps null to null for %s', (datatype) => {
      expect(fromWireValue(datatype, null)).toBeNull();
    });
  });

  describe('DATE', () => {
    it('parses a YYYY-MM-DD wire value into a local Date with matching calendar fields', () => {
      const result = fromWireValue('DATE', '2026-01-02') as Date;
      expect(result instanceof Date).toBe(true);
      expect(result.getFullYear()).toBe(2026);
      expect(result.getMonth()).toBe(0);
      expect(result.getDate()).toBe(2);
    });
  });

  describe('DATE_TIME', () => {
    it('parses an ISO instant wire value into a Date with the same instant', () => {
      const iso = '2026-01-02T23:30:00.000Z';
      const result = fromWireValue('DATE_TIME', iso) as Date;
      expect(result instanceof Date).toBe(true);
      expect(result.getTime()).toBe(new Date(iso).getTime());
    });
  });

  describe('identity datatypes', () => {
    it('passes STRING, BOOLEAN, numeric, TIME, DURATION and ANY_URI wire values through unchanged', () => {
      expect(fromWireValue('STRING', 'hello')).toBe('hello');
      expect(fromWireValue('BOOLEAN', true)).toBe(true);
      expect(fromWireValue('INTEGER', 42)).toBe(42);
      expect(fromWireValue('LONG', 42)).toBe(42);
      expect(fromWireValue('DECIMAL', 12.5)).toBe(12.5);
      expect(fromWireValue('DOUBLE', 0.1)).toBe(0.1);
      expect(fromWireValue('TIME', '23:30:15')).toBe('23:30:15');
      expect(fromWireValue('DURATION', 'P1DT2H3M4.5S')).toBe('P1DT2H3M4.5S');
      expect(fromWireValue('ANY_URI', 'https://example.test/widget/1')).toBe(
        'https://example.test/widget/1',
      );
    });
  });
});
