/**
 * `CodeMirrorTextEditorAdapter` implements the `TextEditorAdapter` contract from
 * `@sequeless/core-api` by hosting a CodeMirror v6 editor with Turtle syntax.
 *
 * The `codemirror` packages are loaded only inside `mount()` via dynamic `import()` — no
 * top-level value imports — so CodeMirror stays out of the main bundle. Type-only imports
 * from codemirror packages are avoided entirely; value-level typing is `any`, mirroring the
 * convention in `dagre-graph-layout.ts`.
 *
 * Pending values / diagnostics: `setValue` / `setDiagnostics` calls made before `mount()`
 * resolve are stored and applied as soon as the editor is attached.
 */
import type { EditorDiagnostic, TextEditorAdapter } from '@sequeless/core-api';

export class CodeMirrorTextEditorAdapter implements TextEditorAdapter {
  private view: any;
  private lint: any;
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
    return this.view?.state.doc.toString() ?? this.pendingValue;
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
    return this.lint.setDiagnostics(this.view.state, diagnostics.map((d) => ({
      from: d.from,
      to: d.to,
      message: d.message,
      severity: d.severity,
    })));
  }
}
