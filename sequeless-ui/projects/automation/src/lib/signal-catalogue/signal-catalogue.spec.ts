/**
 * Contract under test (signal-catalogue/signal-catalogue.ts): `collectSignals(type)` lists one entry per
 * EXTERNAL_SIGNAL transition that has a signal name (`{typeIri, typeName, signalName, from, to}`).
 * `signalsForState(entries, typeIri, stateIri)` keeps only that type's entries: those whose `from` is the
 * state are `enabled`, the rest `disabled` with `requires` = their `from` (a null state enables nothing).
 * `SignalCatalogue` scans `TypesClient.list()` minus abstract types with `describe`, at most
 * `SIGNAL_CATALOGUE_CONCURRENCY` (default 4) at a time, into `entries`; `ensureLoaded()` loads once and is
 * shared by concurrent callers; `refresh()` reloads. A failing scan sets `error` and keeps earlier entries;
 * `loading` is true only while scanning.
 */
import { TestBed } from '@angular/core/testing';
import type {
  ApiError,
  TransitionSummaryResponse,
  TypeDetailResponse,
  TypeSummaryResponse,
} from '@sequeless/core-api';
import { TypesClient } from '@sequeless/core-api';
import { Observable, of, throwError } from 'rxjs';

import {
  SIGNAL_CATALOGUE_CONCURRENCY,
  SignalCatalogue,
  collectSignals,
  signalsForState,
  type SignalEntry,
} from './signal-catalogue';

function transition(over: Partial<TransitionSummaryResponse>): TransitionSummaryResponse {
  return {
    name: 't',
    from: 'ex:A',
    to: 'ex:B',
    hasGuard: false,
    trigger: 'EXTERNAL_SIGNAL',
    after: null,
    signalName: 'go',
    watch: [],
    ...over,
  };
}

function detail(name: string, transitions: TransitionSummaryResponse[] | null): TypeDetailResponse {
  return {
    iri: `ex:${name}`,
    name,
    label: name,
    abstract: false,
    superTypes: [],
    group: null,
    hidden: false,
    properties: [],
    stateMachine: transitions && { iri: `ex:${name}SM`, states: [], initialState: 'ex:A', transitions },
  };
}

function summary(name: string, isAbstract = false): TypeSummaryResponse {
  return { iri: `ex:${name}`, name, label: name, abstract: isAbstract, superTypes: [] };
}

describe('collectSignals', () => {
  it('returns only named external-signal transitions', () => {
    const type = detail('Order', [
      transition({ signalName: 'approve', from: 'ex:A', to: 'ex:B' }),
      transition({ trigger: 'TIMER', after: 'PT1H', signalName: null }),
      transition({ signalName: null }),
      transition({ trigger: 'USER_ACTION', signalName: null }),
    ]);
    expect(collectSignals(type)).toEqual([
      { typeIri: 'ex:Order', typeName: 'Order', signalName: 'approve', from: 'ex:A', to: 'ex:B' },
    ]);
  });

  it('returns nothing without a state machine', () => {
    expect(collectSignals(detail('Plain', null))).toEqual([]);
  });
});

describe('signalsForState', () => {
  const entry = (typeIri: string, signalName: string, from: string): SignalEntry => ({
    typeIri,
    typeName: typeIri,
    signalName,
    from,
    to: 'ex:Z',
  });
  const entries = [entry('ex:T', 'a', 'ex:A'), entry('ex:T', 'b', 'ex:B'), entry('ex:U', 'c', 'ex:A')];

  it('splits one type by whether the signal is accepted from the state', () => {
    const r = signalsForState(entries, 'ex:T', 'ex:A');
    expect(r.enabled.map((e) => e.signalName)).toEqual(['a']);
    expect(r.disabled.map((e) => [e.signalName, e.requires])).toEqual([['b', 'ex:B']]);
  });

  it('disables everything when the state is unknown', () => {
    const r = signalsForState(entries, 'ex:T', null);
    expect(r.enabled).toEqual([]);
    expect(r.disabled).toHaveLength(2);
  });
});

describe('SignalCatalogue', () => {
  let describeCalls: string[];
  let inFlight: number;
  let maxInFlight: number;
  let list: () => Observable<TypeSummaryResponse[]>;
  let details: Record<string, () => Observable<TypeDetailResponse>>;

  function delayed(value: TypeDetailResponse): Observable<TypeDetailResponse> {
    return new Observable((sub) => {
      inFlight++;
      maxInFlight = Math.max(maxInFlight, inFlight);
      const id = setTimeout(() => {
        inFlight--;
        sub.next(value);
        sub.complete();
      }, 5);
      return () => clearTimeout(id);
    });
  }

  function setup(concurrency?: number): SignalCatalogue {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: TypesClient,
          useValue: {
            list: () => list(),
            describe: (name: string) => {
              describeCalls.push(name);
              return details[name]();
            },
          },
        },
        ...(concurrency ? [{ provide: SIGNAL_CATALOGUE_CONCURRENCY, useValue: concurrency }] : []),
      ],
    });
    return TestBed.inject(SignalCatalogue);
  }

  beforeEach(() => {
    describeCalls = [];
    inFlight = 0;
    maxInFlight = 0;
    list = () => of([summary('Order'), summary('Base', true), summary('Task')]);
    details = {
      Order: () => of(detail('Order', [transition({ signalName: 'approve' })])),
      Task: () => of(detail('Task', [transition({ signalName: 'done' })])),
    };
  });

  it('defaults the concurrency to 4', () => {
    TestBed.configureTestingModule({});
    expect(TestBed.inject(SIGNAL_CATALOGUE_CONCURRENCY)).toBe(4);
  });

  it('scans the non-abstract types', async () => {
    const catalogue = setup();
    await catalogue.ensureLoaded();
    expect(describeCalls.sort()).toEqual(['Order', 'Task']);
    expect(catalogue.entries().map((e) => e.signalName).sort()).toEqual(['approve', 'done']);
    expect(catalogue.loading()).toBe(false);
    expect(catalogue.error()).toBeNull();
  });

  it('loads once and shares a scan in flight', async () => {
    const catalogue = setup();
    await Promise.all([catalogue.ensureLoaded(), catalogue.ensureLoaded()]);
    await catalogue.ensureLoaded();
    expect(describeCalls).toHaveLength(2);
  });

  it('rescans on refresh', async () => {
    const catalogue = setup();
    await catalogue.ensureLoaded();
    details['Task'] = () => of(detail('Task', [transition({ signalName: 'finish' })]));
    await catalogue.refresh();
    expect(catalogue.entries().map((e) => e.signalName).sort()).toEqual(['approve', 'finish']);
  });

  it('never has more describes in flight than the concurrency', async () => {
    const names = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
    list = () => of(names.map((n) => summary(n)));
    for (const n of names) {
      details[n] = () => delayed(detail(n, [transition({})]));
    }
    const catalogue = setup(2);
    await catalogue.ensureLoaded();
    expect(maxInFlight).toBe(2);
    expect(catalogue.entries()).toHaveLength(7);
  });

  it('records a failure, keeps earlier entries and allows a retry', async () => {
    const catalogue = setup();
    await catalogue.ensureLoaded();
    const failure = { status: 500, title: 'boom' } as ApiError;
    list = () => throwError(() => failure);
    await catalogue.refresh();
    expect(catalogue.error()).toBe(failure);
    expect(catalogue.entries()).toHaveLength(2);
    expect(catalogue.loading()).toBe(false);

    list = () => of([summary('Order')]);
    await catalogue.ensureLoaded();
    expect(catalogue.error()).toBeNull();
    expect(catalogue.entries()).toHaveLength(1);
  });
});
