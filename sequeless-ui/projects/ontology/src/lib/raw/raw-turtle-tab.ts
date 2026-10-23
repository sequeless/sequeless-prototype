import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import type { EditorDiagnostic } from '@sequeless/core-api';
import { SqTurtleEditor } from '@sequeless/ui-kit';

import { issuesToDiagnostics } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';

const DEBOUNCE_MS = 300;

function parseErrorDiagnostic(text: string, error: unknown): EditorDiagnostic {
  const message = error instanceof Error ? error.message : String(error);
  const line = /line (\d+)/i.exec(message);
  if (!line) {
    return { from: 0, to: 0, message, severity: 'error' };
  }
  const lines = text.split('\n');
  const index = Math.min(Number(line[1]), lines.length) - 1;
  const from = lines.slice(0, index).reduce((n, l) => n + l.length + 1, 0);
  return { from, to: from + (lines[index]?.length ?? 0), message, severity: 'error' };
}

/** The escape hatch: edit the whole ontology as Turtle, journalled like any structured edit. */
@Component({
  selector: 'sq-raw-turtle-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SqTurtleEditor],
  template: `<sq-turtle-editor [value]="text()" [diagnostics]="diagnostics()" (valueChange)="edited($event)" />`,
})
export class SqRawTurtleTab {
  private readonly session = inject(OntologySession);

  protected readonly text = signal('');
  private readonly parseError = signal<EditorDiagnostic | null>(null);
  private readonly liveText = signal('');

  private pending: string | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private ownRevision = -1;

  protected readonly diagnostics = (): EditorDiagnostic[] => {
    const error = this.parseError();
    return [
      ...(error ? [error] : []),
      ...issuesToDiagnostics(this.liveText() || this.text(), this.session.lastRejection()),
    ];
  };

  constructor() {
    effect(() => {
      const revision = this.session.revision();
      if (revision === this.ownRevision) {
        return;
      }
      void this.session.currentText().then((text) => {
        this.text.set(text);
        this.liveText.set(text);
        this.parseError.set(null);
      });
    });

    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.timer);
      this.flush();
    });
  }

  protected edited(value: string): void {
    this.liveText.set(value);
    this.pending = value;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), DEBOUNCE_MS);
  }

  private flush(): void {
    const value = this.pending;
    this.pending = null;
    if (value === null) {
      return;
    }
    try {
      this.session.applyText(value);
      this.ownRevision = this.session.revision();
      // Keep the bound value equal to what the editor shows, so a later undo to the old text still differs.
      this.text.set(value);
      this.parseError.set(null);
    } catch (error) {
      this.parseError.set(parseErrorDiagnostic(value, error));
    }
  }
}
