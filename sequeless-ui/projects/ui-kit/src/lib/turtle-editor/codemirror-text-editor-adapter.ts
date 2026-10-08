/**
 * `CodeMirrorTextEditorAdapter` implements the `TextEditorAdapter` contract from
 * `@sequeless/core-api` by hosting a CodeMirror v6 editor with Turtle syntax.
 *
 * The `codemirror` packages are loaded only inside `mount()` via dynamic `import()` — no
 * top-level value imports — so CodeMirror stays out of the main bundle. Type-only imports
 * from codemirror packages are avoided entirely; the value-level API surface this class
 * consumes is described by a few minimal structural interfaces, mirroring the convention
 * in `dagre-graph-layout.ts`.
 *
 * Pending values / diagnostics: `setValue` / `setDiagnostics` calls made before `mount()`
 * resolve are stored and applied as soon as the editor is attached.
 */
import type { EditorDiagnostic, TextEditorAdapter } from '@sequeless/core-api';

/** Minimal structural slice of the CodeMirror value-level API this class consumes. */
interface CmText {
  readonly length: number;
  toString(): string;
}

interface CmEditorState {
  readonly doc: CmText;
}

interface CmEditorView {
  readonly state: CmEditorState;
  dispatch(spec: unknown): void;
  destroy(): void;
}

/** Mirrors the `EditorDiagnostic` shape consumed by `@codemirror/lint`. */
interface CmDiagnostic {
  from: number;
  to: number;
  message: string;
  severity: 'error' | 'warning' | 'info';
}

interface CmLintModule {
  setDiagnostics(state: CmEditorState, diagnostics: readonly CmDiagnostic[]): unknown;
}

export class CodeMirrorTextEditorAdapter implements TextEditorAdapter {
  private view: CmEditorView | undefined;
  private lint: CmLintModule | undefined;
  private pendingValue = '';
  private pendingDiagnostics: readonly EditorDiagnostic[] = [];

  async mount(
    host: HTMLElement,
    initialValue: string,
    onChange: (value: string) => void,
  ): Promise<void> {
    const [
      { EditorView, basicSetup },
      { StreamLanguage },
      { turtle },
      lint,
    ] = await Promise.all([
      import('codemirror'),
      import('@codemirror/language'),
      import('@codemirror/legacy-modes/mode/turtle'),
      import('@codemirror/lint'),
    ]);

    this.lint = lint;
    this.view = new EditorView({
      parent: host,
      doc: initialValue,
      extensions: [
        basicSetup,
        StreamLanguage.define(turtle),
        lint.lintGutter(),
        EditorView.updateListener.of(
          (u: { docChanged: boolean; state: { doc: { toString(): string } } }) => {
            if (u.docChanged) onChange(u.state.doc.toString());
          },
        ),
      ],
    });
    this.pendingValue = initialValue;
    if (this.pendingDiagnostics.length > 0) {
      this.view.dispatch(this.applyDiagnostics(this.pendingDiagnostics));
      this.pendingDiagnostics = [];
    }
  }

  getValue(): string {
    if (!this.view) {
      return this.pendingValue;
    }
    return this.view.state.doc.toString();
  }

  setValue(value: string): void {
    if (this.view) {
      const current = this.view.state.doc.toString();
      if (current !== value) {
        this.view.dispatch({
          changes: { from: 0, to: this.view.state.doc.length, insert: value },
        });
      }
    }
    this.pendingValue = value;
  }

  setDiagnostics(diagnostics: readonly EditorDiagnostic[]): void {
    if (this.view) {
      this.view.dispatch(this.applyDiagnostics(diagnostics));
    } else {
      this.pendingDiagnostics = diagnostics;
    }
  }

  dispose(): void {
    this.view?.destroy();
    this.view = undefined;
  }

  private applyDiagnostics(diagnostics: readonly EditorDiagnostic[]): unknown {
    if (!this.view || !this.lint) {
      return undefined;
    }
    return this.lint.setDiagnostics(this.view.state, diagnostics.map((d) => ({
      from: d.from,
      to: d.to,
      message: d.message,
      severity: d.severity,
    })));
  }
}
