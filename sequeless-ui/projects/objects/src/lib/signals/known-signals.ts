import type { TypeDetailResponse } from '@sequeless/core-api';

/**
 * The signal names a type's state machine accepts from outside (EXTERNAL_SIGNAL transitions),
 * unique and sorted. A signal only applies from the transition's `from` state, so passing the
 * object's current state narrows the list to what can be fired now.
 */
export function knownSignals(type: TypeDetailResponse, state: string | null = null): string[] {
  const transitions = type.stateMachine?.transitions ?? [];
  const names = transitions
    .filter((t) => t.trigger === 'EXTERNAL_SIGNAL' && (state === null || t.from === state))
    .flatMap((t) => (t.signalName ? [t.signalName] : []));
  return [...new Set(names)].sort();
}
