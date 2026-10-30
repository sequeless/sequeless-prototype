/*
 * Contract [ONT-RAW]: SqRawTurtleTab hosts an sq-turtle-editor on the session's current Turtle. Edits are applied
 * to the session through applyText after a 300 ms pause (and flushed on destroy); invalid Turtle is not applied
 * and shows an error diagnostic at the offending line; the session's lastRejection shows as diagnostics located
 * by subject IRI. The editor is not reset by the echo of its own edit, but follows undo/redo and other edits.
 */
import { TestBed } from '@angular/core/testing';
import { OntologyClient, TURTLE_EDITOR } from '@sequeless/core-api';
import type { EditorDiagnostic, TextEditorAdapter } from '@sequeless/core-api';
import { readClasses } from '@sequeless/rdf';
import { of } from 'rxjs';

import { OntologySession } from '../session/ontology-session';
import { FIXTURE_TTL, TASK } from '../testing/fixture';
import { SqRawTurtleTab } from './raw-turtle-tab';

class FakeAdapter implements TextEditorAdapter {
  value = '';
  diagnostics: readonly EditorDiagnostic[] = [];
  setValueCalls: string[] = [];
  disposed = false;
  private onChange: (v: string) => void = () => undefined;
  async mount(_host: HTMLElement, initial: string, onChange: (v: string) => void): Promise<void> {
    this.value = initial;
    this.onChange = onChange;
  }
  getValue() {
    return this.value;
  }
  setValue(v: string) {
    this.value = v;
    this.setValueCalls.push(v);
  }
  setDiagnostics(d: readonly EditorDiagnostic[]) {
    this.diagnostics = d;
  }
  dispose() {
    this.disposed = true;
  }
  type(v: string) {
    this.value = v;
    this.onChange(v);
  }
}

const flush = async (ms = 0) => {
  await vi.advanceTimersByTimeAsync(ms);
};

async function setup() {
  const adapter = new FakeAdapter();
  TestBed.configureTestingModule({
    providers: [
      OntologySession,
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
      { provide: TURTLE_EDITOR, useValue: () => adapter },
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  const fixture = TestBed.createComponent(SqRawTurtleTab);
  fixture.detectChanges();
  await flush(10);
  fixture.detectChanges();
  await flush(10);
  return { fixture, session, adapter };
}

const labelOf = (s: OntologySession) => readClasses(s.store()!).find((c) => c.iri === TASK)?.label;

describe('SqRawTurtleTab', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('shows the session text in the editor', async () => {
    const { adapter } = await setup();
    expect(adapter.value).toContain('Task');
    expect(adapter.value).toContain('sq:label');
  });

  it('applies an edit after 300 ms of quiet', async () => {
    const { adapter, session } = await setup();
    adapter.type(adapter.value.replace('"Task"', '"Ticket"'));
    await flush(299);
    expect(session.revision()).toBe(0);
    await flush(2);
    expect(labelOf(session)).toBe('Ticket');
    expect(session.revision()).toBe(1);
  });

  it('applies only the last of a burst of edits', async () => {
    const { adapter, session } = await setup();
    adapter.type(adapter.value.replace('"Task"', '"A"'));
    await flush(100);
    adapter.type(adapter.value.replace('"A"', '"B"'));
    await flush(400);
    expect(labelOf(session)).toBe('B');
    expect(session.revision()).toBe(1);
  });

  it('does not reset the editor with the echo of its own edit', async () => {
    const { adapter, fixture } = await setup();
    adapter.setValueCalls.length = 0;
    adapter.type(adapter.value.replace('"Task"', '"Ticket"'));
    await flush(400);
    fixture.detectChanges();
    await flush(10);
    fixture.detectChanges();
    expect(adapter.setValueCalls).toEqual([]);
  });

  it('follows undo', async () => {
    const { adapter, session, fixture } = await setup();
    adapter.type(adapter.value.replace('"Task"', '"Ticket"'));
    await flush(400);
    session.undo();
    fixture.detectChanges();
    await flush(10);
    fixture.detectChanges();
    expect(adapter.value).toContain('"Task"');
  });

  it('does not apply invalid Turtle and marks the failing line', async () => {
    const { adapter, session } = await setup();
    adapter.type('@prefix ex: <http://ex.org/> .\nex:A ex:p\n');
    await flush(400);
    expect(session.revision()).toBe(0);
    expect(adapter.diagnostics).toHaveLength(1);
    expect(adapter.diagnostics[0].severity).toBe('error');
  });

  it('clears the parse error once the text is valid again', async () => {
    const { adapter } = await setup();
    adapter.type('ex:A ex:p');
    await flush(400);
    adapter.type(adapter.value.replace('ex:A ex:p', '') + '\n');
    adapter.type(FIXTURE_TTL);
    await flush(400);
    expect(adapter.diagnostics).toEqual([]);
  });

  it('shows the last rejection as diagnostics at the subject IRI', async () => {
    const { adapter, session, fixture } = await setup();
    session.setRejection({ consistent: false, issues: [{ severity: 'error', subjectIri: TASK, message: 'cyclic' }] });
    fixture.detectChanges();
    await flush(10);
    expect(adapter.diagnostics).toHaveLength(1);
    expect(adapter.diagnostics[0].message).toBe('cyclic');
  });

  it('flushes a pending edit when destroyed', async () => {
    const { adapter, session, fixture } = await setup();
    adapter.type(adapter.value.replace('"Task"', '"Ticket"'));
    fixture.destroy();
    expect(labelOf(session)).toBe('Ticket');
    expect(adapter.disposed).toBe(true);
  });
});
