/**
 * Contract [AUT-SIGNALS-PANEL] (signal-catalogue/signals-panel.ts): `SqAutomationSignalsPanel`
 * (`sq-automation-signals-panel`) has no inputs; it injects ObjectDetailStore, SignalCatalogue and
 * ObjectsClient and asks the catalogue to load. It lists `signalsForState(entries, type.iri, object.state)`:
 * enabled signals are buttons (`[data-signal=name]`), the others are greyed (`[data-signal-disabled=name]`,
 * disabled) with "requires state <label>". A JSON payload textarea is parsed with `parsePayload`; a bad
 * payload shows `[data-payload-error]` and fires nothing. Firing calls `ObjectsClient.signal(type, id, name,
 * payload)`, says "dispatched — takes effect asynchronously", and refetches the object only after
 * OBJECTS_SIGNAL_REFETCH_DELAY_MS (never on the 202 itself). "Refresh" refetches now and refreshes the
 * catalogue. A 400 `unknown-signal` (ApiError.signalName) shows a field error on that signal row
 * (`[data-signal-error=name]`) and refreshes the catalogue; any other failure, and a failed catalogue scan,
 * show a problem banner. `provideAutomationSignalsPanel()` binds the panel to OBJECT_SIGNALS_PANEL.
 */
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ObjectsClient } from '@sequeless/core-api';
import type { ApiError, BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { OBJECT_SIGNALS_PANEL, OBJECTS_SIGNAL_REFETCH_DELAY_MS, ObjectDetailStore } from '@sequeless/objects';
import { of, throwError } from 'rxjs';

import { SignalCatalogue } from './signal-catalogue';
import type { SignalEntry } from './signal-catalogue';
import { SqAutomationSignalsPanel, provideAutomationSignalsPanel } from './signals-panel';

const TYPE = {
  iri: 'ex:Order',
  name: 'Order',
  stateMachine: {
    iri: 'ex:SM',
    initialState: 'ex:Open',
    states: [
      { iri: 'ex:Open', name: 'Open', label: 'Open', displayOrder: 1 },
      { iri: 'ex:Closed', name: 'Closed', label: 'Closed', displayOrder: 2 },
    ],
    transitions: [],
  },
} as unknown as TypeDetailResponse;

const OBJECT = { id: 'o-1', type: 'Order', typeIri: 'ex:Order', version: 1, state: 'ex:Open', properties: {} } as unknown as BusinessObjectResponse;

const entry = (signalName: string, from: string): SignalEntry => ({
  typeIri: 'ex:Order',
  typeName: 'Order',
  signalName,
  from,
  to: 'ex:Closed',
});

const unknownSignal = { status: 400, kind: 'problem', title: 'Bad Request', detail: 'Unknown signal', slug: 'unknown-signal', signalName: 'approve', violations: [], issues: [] } as unknown as ApiError;

function setup(opts: { signalResult?: () => ReturnType<ObjectsClient['signal']>; catalogueError?: ApiError | null } = {}) {
  const objects = { signal: vi.fn(opts.signalResult ?? (() => of(undefined))) };
  const store = { type: signal(TYPE), object: signal(OBJECT), refetch: vi.fn(() => Promise.resolve(OBJECT)) };
  const catalogue = {
    entries: signal([entry('approve', 'ex:Open'), entry('reopen', 'ex:Closed')]),
    loading: signal(false),
    error: signal(opts.catalogueError ?? null),
    ensureLoaded: vi.fn(() => Promise.resolve()),
    refresh: vi.fn(() => Promise.resolve()),
  };
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      { provide: ObjectsClient, useValue: objects },
      { provide: ObjectDetailStore, useValue: store },
      { provide: SignalCatalogue, useValue: catalogue },
      { provide: OBJECTS_SIGNAL_REFETCH_DELAY_MS, useValue: 1000 },
    ],
  });
  const fixture = TestBed.createComponent(SqAutomationSignalsPanel);
  fixture.detectChanges();
  const host = fixture.nativeElement as HTMLElement;
  const click = (selector: string) => {
    (host.querySelector(selector) as HTMLElement).click();
    fixture.detectChanges();
  };
  const typePayload = (text: string) => {
    const area = host.querySelector('textarea') as HTMLTextAreaElement;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  return { fixture, host, objects, store, catalogue, click, typePayload };
}

describe('SqAutomationSignalsPanel', () => {
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('loads the catalogue', () => {
    expect(setup().catalogue.ensureLoaded).toHaveBeenCalled();
  });

  it('enables the signals from the current state and greys the rest with the state they need', () => {
    const { host } = setup();
    expect(host.querySelector('button[data-signal="approve"]')).not.toBeNull();
    const reopen = host.querySelector('[data-signal-disabled="reopen"]') as HTMLButtonElement;
    expect(reopen.disabled).toBe(true);
    expect(host.textContent).toContain('requires state Closed');
    expect(host.querySelector('[data-signal="reopen"]')).toBeNull();
  });

  it('fires the signal with the parsed payload and never claims the transition happened', () => {
    const { host, objects, typePayload, click } = setup();
    typePayload('{"reason": "ok"}');
    click('[data-signal="approve"]');
    expect(objects.signal).toHaveBeenCalledWith('Order', 'o-1', 'approve', { reason: 'ok' });
    expect(host.querySelector('[role="status"]')?.textContent).toContain('dispatched — takes effect asynchronously');
  });

  it('fires with no payload when the textarea is blank', () => {
    const { objects, click } = setup();
    click('[data-signal="approve"]');
    expect(objects.signal).toHaveBeenCalledWith('Order', 'o-1', 'approve', undefined);
  });

  it('refuses a payload that is not a JSON object', () => {
    const { host, objects, typePayload, click } = setup();
    typePayload('[1]');
    click('[data-signal="approve"]');
    expect(objects.signal).not.toHaveBeenCalled();
    expect(host.querySelector('[data-payload-error]')).not.toBeNull();
  });

  it('refetches only after the delay, not on the 202', () => {
    vi.useFakeTimers();
    const { store, click } = setup();
    click('[data-signal="approve"]');
    vi.advanceTimersByTime(999);
    expect(store.refetch).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(store.refetch).toHaveBeenCalledTimes(1);
  });

  it('refetches and refreshes the catalogue on demand', () => {
    const { store, catalogue, click } = setup();
    click('[data-refresh]');
    expect(store.refetch).toHaveBeenCalled();
    expect(catalogue.refresh).toHaveBeenCalled();
  });

  it('shows an unknown-signal rejection on the signal row and refreshes the catalogue', () => {
    const { host, catalogue, click } = setup({ signalResult: () => throwError(() => unknownSignal) });
    click('[data-signal="approve"]');
    expect(host.querySelector('[data-signal-error="approve"]')?.textContent).toContain('Unknown signal');
    expect(catalogue.refresh).toHaveBeenCalled();
    expect(host.querySelector('[role="status"]')).toBeNull();
  });

  it('shows other failures in a problem banner', () => {
    const failure = { status: 500, kind: 'problem', title: 'boom', detail: null, slug: null, signalName: null, violations: [], issues: [] } as unknown as ApiError;
    const { host, click } = setup({ signalResult: () => throwError(() => failure) });
    click('[data-signal="approve"]');
    expect(host.querySelector('sq-problem-banner')).not.toBeNull();
  });

  it('shows a failed catalogue scan in a problem banner', () => {
    const failure = { status: 500, kind: 'problem', title: 'boom', detail: null, slug: null, signalName: null, violations: [], issues: [] } as unknown as ApiError;
    expect(setup({ catalogueError: failure }).host.querySelector('sq-problem-banner')).not.toBeNull();
  });
});

describe('provideAutomationSignalsPanel', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('binds the panel to OBJECT_SIGNALS_PANEL', () => {
    TestBed.configureTestingModule({ providers: [provideAutomationSignalsPanel()] });
    expect(TestBed.inject(OBJECT_SIGNALS_PANEL)).toBe(SqAutomationSignalsPanel);
  });
});
