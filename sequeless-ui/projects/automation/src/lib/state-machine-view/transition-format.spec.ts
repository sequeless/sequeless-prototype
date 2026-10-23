/**
 * Contract under test (state-machine-view/transition-format.ts): pure helpers for the state-machine view.
 * `describeTransition(t, stateLabel)` words a transition by trigger kind — ON_CHANGE "fires when <watch> changes"
 * ("fires when it changes" with no watch), TIMER "fires <duration> after entering <from label>" with the ISO
 * duration shown compactly ("PT72H" -> "72h", "P3DT4H" -> "3d 4h"), EXTERNAL_SIGNAL "fires on signal <name>",
 * USER_ACTION "fired by a user". `hasAutomatedTransitions` is true when any trigger is not USER_ACTION.
 * `stateBrowseLink(type, state)` is the Phase 6 browse route filtered to one state.
 */
import type { StateMachineResponse, TransitionSummaryResponse } from '@sequeless/core-api';

import { describeTransition, hasAutomatedTransitions, stateBrowseLink } from './transition-format';

function transition(over: Partial<TransitionSummaryResponse>): TransitionSummaryResponse {
  return {
    name: 't',
    from: 'ex:A',
    to: 'ex:B',
    hasGuard: false,
    trigger: 'USER_ACTION',
    after: null,
    signalName: null,
    watch: [],
    ...over,
  };
}

const label = (iri: string) => iri.replace('ex:', 'Label ');

describe('describeTransition', () => {
  it('words a user action', () => {
    expect(describeTransition(transition({}), label)).toBe('fired by a user');
  });

  it('words an on-change transition with its watched properties', () => {
    const t = transition({ trigger: 'ON_CHANGE', watch: ['owner', 'parent'] });
    expect(describeTransition(t, label)).toBe('fires when owner, parent changes');
  });

  it('words an on-change transition without a watch as the object itself', () => {
    expect(describeTransition(transition({ trigger: 'ON_CHANGE' }), label)).toBe('fires when it changes');
  });

  it('words a timer with a compact duration and the from-state label', () => {
    const t = transition({ trigger: 'TIMER', after: 'PT72H' });
    expect(describeTransition(t, label)).toBe('fires 72h after entering Label A');
  });

  it('combines days and hours', () => {
    const t = transition({ trigger: 'TIMER', after: 'P3DT4H' });
    expect(describeTransition(t, label)).toBe('fires 3d 4h after entering Label A');
  });

  it('falls back to the raw text when the duration is not parseable', () => {
    const t = transition({ trigger: 'TIMER', after: 'P1M' });
    expect(describeTransition(t, label)).toBe('fires P1M after entering Label A');
  });

  it('words a timer with no duration', () => {
    expect(describeTransition(transition({ trigger: 'TIMER' }), label)).toBe(
      'fires after entering Label A',
    );
  });

  it('words an external signal', () => {
    const t = transition({ trigger: 'EXTERNAL_SIGNAL', signalName: 'approve' });
    expect(describeTransition(t, label)).toBe('fires on signal approve');
  });
});

describe('hasAutomatedTransitions', () => {
  const machine = (...ts: TransitionSummaryResponse[]): StateMachineResponse => ({
    iri: 'ex:SM',
    states: [],
    initialState: 'ex:A',
    transitions: ts,
  });

  it('is false for user actions only, and for none', () => {
    expect(hasAutomatedTransitions(machine(transition({})))).toBe(false);
    expect(hasAutomatedTransitions(machine())).toBe(false);
  });

  it('is true when any transition is automated', () => {
    expect(hasAutomatedTransitions(machine(transition({}), transition({ trigger: 'TIMER' })))).toBe(true);
  });
});

describe('stateBrowseLink', () => {
  it('links to browse filtered by state equality', () => {
    expect(stateBrowseLink('order', 'OnHold')).toEqual({
      commands: ['/objects', 'order'],
      queryParams: { 'filter[state][eq]': 'OnHold' },
    });
  });
});
