/**
 * Contract under test (lib/util/iso-duration.ts): `parseIsoDuration(iso)` parses an ISO-8601
 * duration string of the restricted form `PnDTnHnMn.nS` (the only form `sq-duration-field` and the
 * `DURATION` dynamic field ever emit or need to read back) into a `DurationParts` of
 * days/hours/minutes/seconds. Years and months are rejected, not merely unsupported, because they
 * are not fixed-length (a month is not always the same number of days), so there is no sound way
 * to fold them into this fixed-length representation. `formatIsoDuration(parts)` is the inverse:
 * it emits only the components present in the table above, in that fixed order, and performs no
 * normalisation (25 hours stays `PT25H`, it is not folded into a day) since the composite
 * days/hours/minutes/seconds control owns its own display split and this module must not
 * second-guess it.
 */
import { formatIsoDuration, parseIsoDuration } from './iso-duration';

describe('parseIsoDuration', () => {
  it('parses days, hours, minutes and fractional seconds together', () => {
    expect(parseIsoDuration('P1DT2H3M4.5S')).toEqual({ days: 1, hours: 2, minutes: 3, seconds: 4.5 });
  });

  it('parses a days-only duration, defaulting the rest to zero', () => {
    expect(parseIsoDuration('P2D')).toEqual({ days: 2, hours: 0, minutes: 0, seconds: 0 });
  });

  it('parses a minutes-only duration', () => {
    expect(parseIsoDuration('PT30M')).toEqual({ days: 0, hours: 0, minutes: 30, seconds: 0 });
  });

  it('parses the zero duration', () => {
    expect(parseIsoDuration('PT0S')).toEqual({ days: 0, hours: 0, minutes: 0, seconds: 0 });
  });

  it('parses a week designator as 7 days', () => {
    expect(parseIsoDuration('P1W')).toEqual({ days: 7, hours: 0, minutes: 0, seconds: 0 });
  });

  it('accepts lowercase input', () => {
    expect(parseIsoDuration('p1dt2h3m4.5s')).toEqual({ days: 1, hours: 2, minutes: 3, seconds: 4.5 });
  });

  it.each(['', 'P', 'PT', '1D', 'P1Y', 'P1M', 'garbage'])(
    'throws an Error for %j',
    (input) => {
      expect(() => parseIsoDuration(input)).toThrow(Error);
    },
  );
});

describe('formatIsoDuration', () => {
  it('omits zero components, keeping the time designator only when a time component is present', () => {
    expect(formatIsoDuration({ days: 1, hours: 2, minutes: 0, seconds: 0 })).toBe('P1DT2H');
  });

  it('omits the date designator entirely when days is zero', () => {
    expect(formatIsoDuration({ days: 0, hours: 0, minutes: 5, seconds: 0 })).toBe('PT5M');
  });

  it('renders fractional seconds with a decimal point', () => {
    expect(formatIsoDuration({ days: 0, hours: 0, minutes: 0, seconds: 1.25 })).toBe('PT1.25S');
  });

  it('renders the zero duration as PT0S rather than an empty string', () => {
    expect(formatIsoDuration({ days: 0, hours: 0, minutes: 0, seconds: 0 })).toBe('PT0S');
  });

  it('renders a days-only duration without a time designator', () => {
    expect(formatIsoDuration({ days: 2, hours: 0, minutes: 0, seconds: 0 })).toBe('P2D');
  });

  it('does not normalise an hours value of 25 or more into days', () => {
    expect(formatIsoDuration({ days: 0, hours: 25, minutes: 0, seconds: 0 })).toBe('PT25H');
  });

  it('keeps at most 3 decimal places on seconds, with no trailing zeros', () => {
    expect(formatIsoDuration({ days: 0, hours: 0, minutes: 0, seconds: 1.5 })).toBe('PT1.5S');
    expect(formatIsoDuration({ days: 0, hours: 0, minutes: 0, seconds: 1.1 })).toBe('PT1.1S');
  });

  it.each([
    { parts: { days: -1, hours: 0, minutes: 0, seconds: 0 }, label: 'negative days' },
    { parts: { days: 0, hours: -2, minutes: 0, seconds: 0 }, label: 'negative hours' },
    { parts: { days: 0, hours: 0, minutes: 0, seconds: Number.NaN }, label: 'NaN seconds' },
    { parts: { days: 0, hours: 0, minutes: 0, seconds: Number.POSITIVE_INFINITY }, label: 'infinite seconds' },
  ])('throws an Error for $label', ({ parts }) => {
    expect(() => formatIsoDuration(parts)).toThrow(Error);
  });

  it.each(['P1DT2H3M4.5S', 'P2D', 'PT30M', 'PT0S', 'PT1.25S', 'PT25H'])(
    'round-trips %j through parseIsoDuration',
    (iso) => {
      expect(formatIsoDuration(parseIsoDuration(iso))).toBe(iso);
    },
  );
});
