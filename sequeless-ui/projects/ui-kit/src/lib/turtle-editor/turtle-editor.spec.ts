/**
 * Contract under test (lib/turtle-editor/turtle-editor.ts): `SqTurtleEditor` (selector
 * `sq-turtle-editor`) hosts a pluggable `TextEditorAdapter` (`@sequeless/core-api`) behind the
 * `TURTLE_EDITOR` token (phase-5 spec, "Turtle editor"), so this spec substitutes a `FakeAdapter`
 * and never touches the real CodeMirror adapter (./codemirror-text-editor-adapter) at all.
 *
 * `value = input.required<string>()`; `diagnostics = input<readonly EditorDiagnostic[]>([])`;
 * `valueChange = output<string>()`. The component resolves its adapter factory via
 * `inject(TURTLE_EDITOR, { optional: true }) ?? (() => new CodeMirrorTextEditorAdapter())` and, on
 * first render, calls `adapter.mount(host, value(), onChange)` against the template's
 * `<div class="sq-turtle-editor-host" #host>`, where `onChange` re-emits its argument on
 * `valueChange`. After mount, a `value()` change calls `adapter.setValue(next)` only when `next`
 * differs from `adapter.getValue()` — so writing back the editor's own current text (e.g. a parent
 * form control echoing what the user just typed) does not reset the editor's cursor/undo state —
 * while a `diagnostics()` change calls `adapter.setDiagnostics(next)` unconditionally. On destroy,
 * the component calls `adapter.dispose()`.
 */
import { TestBed } from '@angular/core/testing';
import { TURTLE_EDITOR } from '@sequeless/core-api';
import type { EditorDiagnostic, TextEditorAdapter } from '@sequeless/core-api';
import { SqTurtleEditor } from './turtle-editor';

class FakeAdapter implements TextEditorAdapter {
  readonly mountCalls: { host: HTMLElement; initialValue: string }[] = [];
  readonly setValueCalls: string[] = [];
  readonly setDiagnosticsCalls: (readonly EditorDiagnostic[])[] = [];
  disposeCalls = 0;
  private current = '';
  private onChange: ((value: string) => void) | null = null;

  async mount(
    host: HTMLElement,
    initialValue: string,
    onChange: (value: string) => void,
  ): Promise<void> {
    this.mountCalls.push({ host, initialValue });
    this.current = initialValue;
    this.onChange = onChange;
  }

  getValue(): string {
    return this.current;
  }

  setValue(value: string): void {
    this.setValueCalls.push(value);
    this.current = value;
  }

  setDiagnostics(diagnostics: readonly EditorDiagnostic[]): void {
    this.setDiagnosticsCalls.push(diagnostics);
  }

  dispose(): void {
    this.disposeCalls += 1;
  }

  /** Simulates the user typing: the underlying editor already holds the new text. */
  triggerChange(value: string): void {
    this.current = value;
    this.onChange?.(value);
  }
}

function createFixture(fake: FakeAdapter, value: string) {
  TestBed.configureTestingModule({
    providers: [{ provide: TURTLE_EDITOR, useValue: () => fake }],
  });
  const fixture = TestBed.createComponent(SqTurtleEditor);
  fixture.componentRef.setInput('value', value);
  return fixture;
}

describe('SqTurtleEditor', () => {
  let fake: FakeAdapter;

  beforeEach(() => {
    fake = new FakeAdapter();
  });

  it('mounts the adapter exactly once with the host element and the initial value', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    expect(fake.mountCalls).toHaveLength(1);
    expect(fake.mountCalls[0].initialValue).toBe('ex:a ex:b ex:c .');
    const host = fake.mountCalls[0].host;
    expect(host.classList.contains('sq-turtle-editor-host')).toBe(true);
    expect(fixture.nativeElement.contains(host)).toBe(true);
  });

  it('emits valueChange with the value reported by the adapter onChange callback', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    const emitted: string[] = [];
    fixture.componentInstance.valueChange.subscribe((v: string) => emitted.push(v));

    fake.triggerChange('ex:a ex:b ex:d .');

    expect(emitted).toEqual(['ex:a ex:b ex:d .']);
  });

  it('does not call setValue when the value input is set to the text the adapter already holds', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    fixture.componentRef.setInput('value', 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    expect(fake.setValueCalls).toEqual([]);
  });

  it('calls setValue when the value input changes to text the adapter does not already hold', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    fixture.componentRef.setInput('value', 'ex:a ex:b ex:e .');
    await fixture.whenStable();

    expect(fake.setValueCalls).toEqual(['ex:a ex:b ex:e .']);
  });

  it('does not call setValue again once the adapter already holds the just-set value', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    fixture.componentRef.setInput('value', 'ex:a ex:b ex:e .');
    await fixture.whenStable();
    expect(fake.setValueCalls).toEqual(['ex:a ex:b ex:e .']);

    fixture.componentRef.setInput('value', 'ex:a ex:b ex:e .');
    await fixture.whenStable();

    expect(fake.setValueCalls).toEqual(['ex:a ex:b ex:e .']);
  });

  it('does not call setValue for the text the adapter reports back via its own onChange', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    fake.triggerChange('ex:a ex:b ex:d .');
    fixture.componentRef.setInput('value', 'ex:a ex:b ex:d .');
    await fixture.whenStable();

    expect(fake.setValueCalls).toEqual([]);
  });

  it('defaults diagnostics to an empty array when not provided', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    expect(fixture.componentInstance.diagnostics()).toEqual([]);
  });

  it('forwards the initial diagnostics input to setDiagnostics', async () => {
    const diagnostics: readonly EditorDiagnostic[] = [
      { from: 0, to: 3, message: 'unknown prefix', severity: 'error' },
    ];
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    fixture.componentRef.setInput('diagnostics', diagnostics);
    await fixture.whenStable();

    expect(fake.setDiagnosticsCalls.at(-1)).toEqual(diagnostics);
  });

  it('forwards a changed diagnostics input to setDiagnostics', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    const diagnostics: readonly EditorDiagnostic[] = [
      { from: 5, to: 8, message: 'unexpected token', severity: 'warning' },
    ];
    fixture.componentRef.setInput('diagnostics', diagnostics);
    await fixture.whenStable();

    expect(fake.setDiagnosticsCalls.at(-1)).toEqual(diagnostics);
  });

  it('does not call dispose before the component is destroyed', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    expect(fake.disposeCalls).toBe(0);
  });

  it('calls dispose exactly once when the component is destroyed', async () => {
    const fixture = createFixture(fake, 'ex:a ex:b ex:c .');
    await fixture.whenStable();

    fixture.destroy();

    expect(fake.disposeCalls).toBe(1);
  });
});
