import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import { TEXT_DIFF_VIEW } from './text-diff-view';
import type { TextDiffView } from './text-diff-view';

/** Side-by-side text diff of the base ontology against the working one. */
@Component({
  selector: 'sq-text-diff',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="sq-text-diff-host" #host></div>`,
})
export class SqTextDiff {
  readonly base = input.required<string>();
  readonly working = input.required<string>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly view = signal<TextDiffView | null>(null);

  constructor() {
    const factory = inject(TEXT_DIFF_VIEW);
    afterNextRender(() => this.view.set(factory(this.host().nativeElement)));
    effect(() => this.view()?.update(this.base(), this.working()));
    inject(DestroyRef).onDestroy(() => this.view()?.dispose());
  }
}
