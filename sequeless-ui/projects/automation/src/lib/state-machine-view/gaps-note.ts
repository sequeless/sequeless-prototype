import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { StateMachineResponse } from '@sequeless/core-api';

import { hasAutomatedTransitions } from './transition-format';

/** What this screen cannot show, said plainly rather than faked (D8). */
@Component({
  selector: 'sq-automation-gaps-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="sq-gaps-note">
      Timers show their duration only (no countdown): the server exposes no scheduled timers. There is no outbox
      or automation history view either.
    </p>
    @if (hidden()) {
      <p class="sq-gaps-note" data-hidden-hint>
        This type shows no automated transitions; the server may be hiding them.
      </p>
    }
  `,
})
export class AutomationGapsNote {
  readonly sm = input<StateMachineResponse | null>(null);

  protected readonly hidden = computed(() => {
    const sm = this.sm();
    return sm !== null && !hasAutomatedTransitions(sm);
  });
}
