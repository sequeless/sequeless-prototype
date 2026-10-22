/**
 * Contract [OBJ-SIGNALS] (lib/signals/known-signals.ts, `knownSignals`): the signal names a type's
 * state machine accepts from outside, unique and sorted. When a current state is given, only the
 * transitions leaving that state count.
 */
import type { TypeDetailResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';

import { knownSignals } from './known-signals';

type Transition = NonNullable<TypeDetailResponse['stateMachine']>['transitions'][number];

function transition(overrides: Partial<Transition>): Transition {
  return {
    name: 't',
    from: 'Open',
    to: 'Closed',
    hasGuard: false,
    trigger: 'EXTERNAL_SIGNAL',
    after: null,
    signalName: null,
    watch: [],
    ...overrides,
  };
}

function typeWith(transitions: Transition[]): TypeDetailResponse {
  return {
    ...PROJECT_TYPE_DETAIL,
    stateMachine: {
      iri: `${PROJECT_TYPE_DETAIL.iri}#lifecycle`,
      states: [],
      initialState: 'Open',
      transitions,
    },
  };
}

describe('knownSignals', () => {
  it('returns unique names sorted alphabetically', () => {
    const type = typeWith([
      transition({ signalName: 'reopened' }),
      transition({ signalName: 'approved' }),
      transition({ signalName: 'reopened', from: 'Closed' }),
    ]);

    expect(knownSignals(type)).toEqual(['approved', 'reopened']);
  });

  it('ignores non-EXTERNAL_SIGNAL transitions and null signal names', () => {
    const type = typeWith([
      transition({ trigger: 'USER_ACTION', signalName: 'ignored' }),
      transition({ signalName: null }),
      transition({ signalName: 'approved' }),
    ]);

    expect(knownSignals(type)).toEqual(['approved']);
  });

  it('keeps only transitions leaving the current state when one is given', () => {
    const type = typeWith([
      transition({ from: 'Open', signalName: 'approved' }),
      transition({ from: 'Closed', signalName: 'reopened' }),
    ]);

    expect(knownSignals(type, 'Closed')).toEqual(['reopened']);
    expect(knownSignals(type, null)).toEqual(['approved', 'reopened']);
  });

  it('returns [] without a state machine', () => {
    expect(knownSignals({ ...PROJECT_TYPE_DETAIL, stateMachine: null })).toEqual([]);
  });
});
