/**
 * Contract under test (lib/util/edge-style.ts), consumed by `sq-state-machine-graph`:
 * `edgeStyle(trigger)` maps a `TriggerKind` to the SVG styling fixed by the phase-5 spec — solid for
 * `USER_ACTION`, dashed for `ON_CHANGE`, dotted with a clock glyph for `TIMER`, and double-stroke
 * with an antenna glyph for `EXTERNAL_SIGNAL`. (The lock glyph for `hasGuard` is drawn separately by
 * the component and is not this function's concern.) `edgeLabel(t)` combines the transition's `name`
 * with whichever of `after`/`signalName`/`watch.join(', ')` applies to its trigger kind, omitting the
 * parenthetical entirely when that field is null/empty or the trigger is `USER_ACTION`.
 * `pointsToPath(points)` renders a dagre-produced polyline as a plain SVG path `d` attribute using
 * `M`/`L` commands, with no curve smoothing.
 */
import type { TransitionSummaryResponse, TriggerKind } from '@sequeless/core-api';
import { edgeLabel, edgeStyle, pointsToPath } from './edge-style';

function transition(overrides: Partial<TransitionSummaryResponse>): TransitionSummaryResponse {
  return {
    name: 'name',
    from: 'StateA',
    to: 'StateB',
    hasGuard: false,
    trigger: 'USER_ACTION',
    after: null,
    signalName: null,
    watch: [],
    ...overrides,
  };
}

describe('edgeStyle', () => {
  it('renders USER_ACTION as a plain solid line with no glyph', () => {
    expect(edgeStyle('USER_ACTION')).toEqual({ dash: null, double: false, glyph: null });
  });

  it('renders ON_CHANGE as a dashed single stroke with no glyph', () => {
    expect(edgeStyle('ON_CHANGE')).toEqual({ dash: '6 4', double: false, glyph: null });
  });

  it('renders TIMER as a dotted single stroke with a clock glyph', () => {
    expect(edgeStyle('TIMER')).toEqual({ dash: '2 4', double: false, glyph: 'clock' });
  });

  it('renders EXTERNAL_SIGNAL as a solid double stroke with an antenna glyph', () => {
    expect(edgeStyle('EXTERNAL_SIGNAL')).toEqual({ dash: null, double: true, glyph: 'antenna' });
  });

  it.each<TriggerKind>(['USER_ACTION', 'ON_CHANGE', 'TIMER', 'EXTERNAL_SIGNAL'])(
    'returns a fresh object for %s, not a shared mutable instance',
    (trigger) => {
      const a = edgeStyle(trigger);
      const b = edgeStyle(trigger);
      expect(a).not.toBe(b);
      expect(a).toEqual(b);
    },
  );
});

describe('edgeLabel', () => {
  it('appends the after duration for a TIMER transition', () => {
    expect(edgeLabel(transition({ trigger: 'TIMER', after: 'PT1H' }))).toBe('name (PT1H)');
  });

  it('appends the signal name for an EXTERNAL_SIGNAL transition', () => {
    expect(edgeLabel(transition({ trigger: 'EXTERNAL_SIGNAL', signalName: 'paid' }))).toBe('name (paid)');
  });

  it('appends the comma-joined watch list for an ON_CHANGE transition', () => {
    expect(edgeLabel(transition({ trigger: 'ON_CHANGE', watch: ['a', 'b'] }))).toBe('name (a, b)');
  });

  it('omits the parenthetical for USER_ACTION even if after/signalName/watch are set', () => {
    expect(
      edgeLabel(
        transition({
          trigger: 'USER_ACTION',
          after: 'PT1H',
          signalName: 'paid',
          watch: ['a'],
        }),
      ),
    ).toBe('name');
  });

  it('omits the parenthetical for TIMER when after is null', () => {
    expect(edgeLabel(transition({ trigger: 'TIMER', after: null }))).toBe('name');
  });

  it('omits the parenthetical for EXTERNAL_SIGNAL when signalName is null', () => {
    expect(edgeLabel(transition({ trigger: 'EXTERNAL_SIGNAL', signalName: null }))).toBe('name');
  });

  it('omits the parenthetical for ON_CHANGE when watch is empty', () => {
    expect(edgeLabel(transition({ trigger: 'ON_CHANGE', watch: [] }))).toBe('name');
  });

  it('uses the transition name as-is, independent of from/to/hasGuard', () => {
    expect(
      edgeLabel(transition({ name: 'approve', from: 'Draft', to: 'Approved', hasGuard: true })),
    ).toBe('approve');
  });
});

describe('pointsToPath', () => {
  it('renders a 3-point polyline as M followed by L commands', () => {
    expect(
      pointsToPath([
        { x: 0, y: 0 },
        { x: 10, y: 5 },
        { x: 20, y: 5 },
      ]),
    ).toBe('M 0 0 L 10 5 L 20 5');
  });

  it('renders a single point as a bare M command', () => {
    expect(pointsToPath([{ x: 3, y: 7 }])).toBe('M 3 7');
  });

  it('renders an empty point list as an empty string', () => {
    expect(pointsToPath([])).toBe('');
  });
});
