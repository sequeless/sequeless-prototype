import type { Params } from '@angular/router';
import type { StateMachineResponse, TransitionSummaryResponse } from '@sequeless/core-api';
import { parseIsoDuration } from '@sequeless/ui-kit';

function compactDuration(iso: string): string {
  try {
    const { days, hours, minutes, seconds } = parseIsoDuration(iso);
    const parts = [
      days ? `${days}d` : '',
      hours ? `${hours}h` : '',
      minutes ? `${minutes}m` : '',
      seconds ? `${seconds}s` : '',
    ].filter(Boolean);
    return parts.length ? parts.join(' ') : iso;
  } catch {
    return iso;
  }
}

export function describeTransition(
  t: TransitionSummaryResponse,
  stateLabel: (iri: string) => string,
): string {
  switch (t.trigger) {
    case 'ON_CHANGE':
      return `fires when ${t.watch.length ? t.watch.join(', ') : 'it'} changes`;
    case 'TIMER':
      return t.after
        ? `fires ${compactDuration(t.after)} after entering ${stateLabel(t.from)}`
        : `fires after entering ${stateLabel(t.from)}`;
    case 'EXTERNAL_SIGNAL':
      return `fires on signal ${t.signalName ?? ''}`.trimEnd();
    case 'USER_ACTION':
      return 'fired by a user';
  }
}

export function hasAutomatedTransitions(sm: StateMachineResponse): boolean {
  return sm.transitions.some((t) => t.trigger !== 'USER_ACTION');
}

/** The Phase 6 browse route filtered to objects currently in one state. */
export function stateBrowseLink(
  typeName: string,
  stateName: string,
): { commands: string[]; queryParams: Params } {
  return { commands: ['/objects', typeName], queryParams: { 'filter[state][eq]': stateName } };
}
