/**
 * Restricted ISO-8601 duration helpers for the `PnDTnHnMn.nS` form (plus the `W` designator,
 * folded into days). Years and months are rejected outright: they are not fixed-length, so they
 * cannot be represented soundly as days/hours/minutes/seconds.
 *
 * `formatIsoDuration` performs no normalisation — 25 hours stays `PT25H` — because the composite
 * control owns its own display split.
 */

export interface DurationParts {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
}

const ISO_DURATION = /^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i;

export function parseIsoDuration(iso: string): DurationParts {
  const match = ISO_DURATION.exec(iso);
  if (!match || (match[1] == null && match[2] == null && match[3] == null && match[4] == null && match[5] == null)) {
    throw new Error(`Invalid ISO-8601 duration: ${JSON.stringify(iso)}`);
  }
  return {
    days: 7 * (match[1] != null ? Number(match[1]) : 0) + (match[2] != null ? Number(match[2]) : 0),
    hours: match[3] != null ? Number(match[3]) : 0,
    minutes: match[4] != null ? Number(match[4]) : 0,
    seconds: match[5] != null ? Number(match[5]) : 0,
  };
}

function formatSeconds(value: number): string {
  return String(Number(value.toFixed(3)));
}

export function formatIsoDuration(parts: DurationParts): string {
  const { days, hours, minutes, seconds } = parts;
  if (
    !Number.isFinite(days) ||
    !Number.isFinite(hours) ||
    !Number.isFinite(minutes) ||
    !Number.isFinite(seconds) ||
    days < 0 ||
    hours < 0 ||
    minutes < 0 ||
    seconds < 0
  ) {
    throw new Error(`Invalid duration parts: ${JSON.stringify(parts)}`);
  }

  let result = 'P';
  if (days > 0) {
    result += `${days}D`;
  }
  const hasTime = hours > 0 || minutes > 0 || seconds > 0;
  if (hasTime) {
    result += 'T';
    if (hours > 0) {
      result += `${Math.round(hours)}H`;
    }
    if (minutes > 0) {
      result += `${Math.round(minutes)}M`;
    }
    if (seconds > 0) {
      result += `${formatSeconds(seconds)}S`;
    }
  }
  if (result === 'P' || result === 'PT') {
    result = 'PT0S';
  }
  return result;
}
