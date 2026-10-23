import type { TriggerKind, TypeDetailResponse } from '@sequeless/core-api';
import type { StateMachineView, TransitionView, TriggerView } from '@sequeless/rdf';
import { parseIsoDuration } from '@sequeless/ui-kit';

import { compactDuration } from '../state-machine-view/transition-format';

export interface TriggerFormValue {
  kind: TriggerKind;
  watch: string[];
  after: string | null;
  signalName: string;
}

export function triggerToForm(t: TriggerView): TriggerFormValue {
  const base: TriggerFormValue = { kind: t.kind, watch: [], after: null, signalName: '' };
  switch (t.kind) {
    case 'ON_CHANGE':
      return { ...base, watch: [...t.watchIris] };
    case 'TIMER':
      return { ...base, after: t.after || null };
    case 'EXTERNAL_SIGNAL':
      return { ...base, signalName: t.signalName };
    case 'USER_ACTION':
      return base;
  }
}

function isPositiveDuration(iso: string): boolean {
  try {
    const { days, hours, minutes, seconds } = parseIsoDuration(iso);
    return days + hours + minutes + seconds > 0;
  } catch {
    return false;
  }
}

export function triggerFromForm(v: TriggerFormValue): TriggerView | { error: string } {
  switch (v.kind) {
    case 'ON_CHANGE':
      return { kind: 'ON_CHANGE', watchIris: [...v.watch] };
    case 'TIMER':
      return v.after && isPositiveDuration(v.after)
        ? { kind: 'TIMER', after: v.after }
        : { error: 'A timer needs a positive duration.' };
    case 'EXTERNAL_SIGNAL': {
      const signalName = v.signalName.trim();
      return signalName ? { kind: 'EXTERNAL_SIGNAL', signalName } : { error: 'A signal needs a name.' };
    }
    case 'USER_ACTION':
      return { kind: 'USER_ACTION' };
  }
}

export function relationshipWatchOptions(type: TypeDetailResponse): { iri: string; label: string }[] {
  return type.properties
    .filter((p) => p.kind === 'relationship')
    .map((p) => ({ iri: p.iri, label: p.label }));
}

/** Names of the other transitions on the same machine that already use `name` (D9: a warning, never a block). */
export function signalCollisions(
  sm: StateMachineView,
  transition: TransitionView,
  name: string,
): string[] {
  const wanted = name.trim();
  if (!wanted) {
    return [];
  }
  const same = (a: TransitionView['node'], b: TransitionView['node']) =>
    a.termType === b.termType && a.value === b.value;
  return sm.transitions
    .filter(
      (o) =>
        !same(o.node, transition.node) &&
        o.trigger.kind === 'EXTERNAL_SIGNAL' &&
        o.trigger.signalName === wanted,
    )
    .map((o) => o.name ?? o.node.value);
}

export function timerEcho(after: string, fromLabel: string): string {
  return `fires ${compactDuration(after)} after entering ${fromLabel}`;
}
