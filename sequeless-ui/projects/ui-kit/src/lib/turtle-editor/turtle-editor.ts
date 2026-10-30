/**
 * `SqTurtleEditor` (selector `sq-turtle-editor`) hosts a pluggable `TextEditorAdapter`
 * (`@sequeless/core-api`) behind the `TURTLE_EDITOR` token (phase-5 spec, "Turtle editor").
 *
 * `value = input.required<string>()`; `diagnostics = input<readonly EditorDiagnostic[]>([])`;
 * `valueChange = output<string>()`. The adapter factory comes from `TURTLE_EDITOR` (falls
 * back to a `CodeMirrorTextEditorAdapter`), is mounted once against the template's
 * `<div class="sq-turtle-editor-host" #host>` after the first render, and receives value /
 * diagnostics updates through effects. `adapter.dispose()` runs on destroy.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TURTLE_EDITOR } from '@sequeless/core-api';
import type { EditorDiagnostic } from '@sequeless/core-api';

import { CodeMirrorTextEditorAdapter } from './codemirror-text-editor-adapter';
import type { TextEditorAdapter } from '@sequeless/core-api';

@Component({
  selector: 'sq-turtle-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sq-turtle-editor-host" #host style="display: block; min-height: 240px;"></div>
  `,
})
export class SqTurtleEditor {
  readonly value = input.required<string>();
  readonly diagnostics = input<readonly EditorDiagnostic[]>([]);
  readonly valueChange = output<string>();

  readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  readonly mounted = signal(false);
  readonly adapter: TextEditorAdapter;

  constructor() {
    const factory = inject(TURTLE_EDITOR, { optional: true }) ?? (() => new CodeMirrorTextEditorAdapter());
    this.adapter = factory();

    const { value, diagnostics, valueChange, host, mounted, adapter } = this;

    afterNextRender(() => {
      adapter
        .mount(host().nativeElement, value(), v => valueChange.emit(v))
        .then(() => mounted.set(true));
    });

    effect(() => {
      const v = value();
      if (mounted() && v !== adapter.getValue()) {
        adapter.setValue(v);
      }
    });

    effect(() => {
      const d = diagnostics();
      if (mounted()) {
        adapter.setDiagnostics(d);
      }
    });

    inject(DestroyRef).onDestroy(() => adapter.dispose());
  }
}
