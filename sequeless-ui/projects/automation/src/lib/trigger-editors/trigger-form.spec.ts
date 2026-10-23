/**
 * Contract under test (trigger-editors/trigger-form.ts): the pure mapping between rdf's `TriggerView` and the
 * trigger editor's form. `triggerToForm` flattens a view; `triggerFromForm` rebuilds one, returning `{error}`
 * for a TIMER without a positive ISO duration or an EXTERNAL_SIGNAL with a blank name (names are trimmed), and
 * ignoring fields of other kinds. `relationshipWatchOptions` lists a type's relationship properties.
 * `signalCollisions` names other transitions of the same machine that use the same signal name (a warning, D9).
 * `timerEcho` is "fires 72h after entering OnHold".
 */
import type { TypeDetailResponse } from '@sequeless/core-api';
import type { StateMachineView, TransitionView, TriggerView } from '@sequeless/rdf';

import {
  relationshipWatchOptions,
  signalCollisions,
  timerEcho,
  triggerFromForm,
  triggerToForm,
  type TriggerFormValue,
} from './trigger-form';

const form = (over: Partial<TriggerFormValue>): TriggerFormValue => ({
  kind: 'USER_ACTION',
  watch: [],
  after: null,
  signalName: '',
  ...over,
});

describe('triggerToForm', () => {
  it('flattens each kind', () => {
    expect(triggerToForm({ kind: 'USER_ACTION' })).toEqual(form({}));
    expect(triggerToForm({ kind: 'ON_CHANGE', watchIris: ['ex:p'] })).toEqual(
      form({ kind: 'ON_CHANGE', watch: ['ex:p'] }),
    );
    expect(triggerToForm({ kind: 'TIMER', after: 'PT1H' })).toEqual(form({ kind: 'TIMER', after: 'PT1H' }));
    expect(triggerToForm({ kind: 'EXTERNAL_SIGNAL', signalName: 'go' })).toEqual(
      form({ kind: 'EXTERNAL_SIGNAL', signalName: 'go' }),
    );
  });

  it('treats an empty timer as no duration', () => {
    expect(triggerToForm({ kind: 'TIMER', after: '' }).after).toBeNull();
  });
});

describe('triggerFromForm', () => {
  it('builds each kind and ignores fields of other kinds', () => {
    expect(triggerFromForm(form({ watch: ['x'], signalName: 's' }))).toEqual({ kind: 'USER_ACTION' });
    expect(triggerFromForm(form({ kind: 'ON_CHANGE', watch: ['ex:p'], signalName: 's' }))).toEqual({
      kind: 'ON_CHANGE',
      watchIris: ['ex:p'],
    });
    expect(triggerFromForm(form({ kind: 'TIMER', after: 'PT72H' }))).toEqual({ kind: 'TIMER', after: 'PT72H' });
    expect(triggerFromForm(form({ kind: 'EXTERNAL_SIGNAL', signalName: '  go ' }))).toEqual({
      kind: 'EXTERNAL_SIGNAL',
      signalName: 'go',
    });
  });

  it('accepts an on-change with no watch (the object itself)', () => {
    expect(triggerFromForm(form({ kind: 'ON_CHANGE' }))).toEqual({ kind: 'ON_CHANGE', watchIris: [] });
  });

  it('rejects a timer without a positive valid duration', () => {
    for (const after of [null, '', 'garbage', 'PT0S', 'P1M']) {
      expect(triggerFromForm(form({ kind: 'TIMER', after }))).toHaveProperty('error');
    }
  });

  it('rejects a blank signal name', () => {
    expect(triggerFromForm(form({ kind: 'EXTERNAL_SIGNAL', signalName: '   ' }))).toHaveProperty('error');
  });
});

describe('relationshipWatchOptions', () => {
  it('lists relationship properties only', () => {
    const type = {
      properties: [
        { kind: 'attribute', iri: 'ex:a', label: 'A' },
        { kind: 'relationship', iri: 'ex:r', label: 'R' },
      ],
    } as unknown as TypeDetailResponse;
    expect(relationshipWatchOptions(type)).toEqual([{ iri: 'ex:r', label: 'R' }]);
  });
});

describe('signalCollisions', () => {
  const t = (id: string, trigger: TriggerView, name: string | null = id): TransitionView => ({
    node: { termType: 'BlankNode', value: id } as TransitionView['node'],
    name,
    fromIri: 'ex:A',
    toIri: 'ex:B',
    trigger,
    guard: null,
    guardMessage: null,
  });
  const a = t('a', { kind: 'EXTERNAL_SIGNAL', signalName: 'go' }, 'first');
  const b = t('b', { kind: 'EXTERNAL_SIGNAL', signalName: 'go' }, 'second');
  const c = t('c', { kind: 'EXTERNAL_SIGNAL', signalName: 'stop' }, 'third');
  const d = t('d', { kind: 'TIMER', after: 'PT1H' }, 'fourth');
  const machine = { transitions: [a, b, c, d] } as unknown as StateMachineView;

  it('names the other transitions that use the signal', () => {
    expect(signalCollisions(machine, a, 'go')).toEqual(['second']);
  });

  it('checks the typed name, not the stored one, and skips the transition itself', () => {
    expect(signalCollisions(machine, c, 'go')).toEqual(['first', 'second']);
    expect(signalCollisions(machine, a, 'unique')).toEqual([]);
  });

  it('ignores a blank name', () => {
    expect(signalCollisions(machine, a, '  ')).toEqual([]);
  });
});

describe('timerEcho', () => {
  it('words the delay after entering the from state', () => {
    expect(timerEcho('PT72H', 'OnHold')).toBe('fires 72h after entering OnHold');
  });
});
