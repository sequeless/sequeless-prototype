import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
} from '@angular/core';
import type { ApiError, OntologyIssue } from '@sequeless/core-api';
import { normalizeProblems } from '../util/problems';

@Component({
  selector: 'sq-problem-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length) {
      <div role="alert" class="sq-problem-banner">
        <ul>
          @for (p of items(); track $index) {
            <li
              [attr.data-severity]="p.severity"
              [class]="'sq-problem--' + p.severity"
            >
              {{ p.message }}
              @if (p.subject) {
                <code>{{ p.subject }}</code>
              }
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: `
    .sq-problem-banner {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      padding: 0.5rem 0.75rem;
      border-radius: 4px;
      background: color-mix(in srgb, currentColor 6%, transparent);
    }

    .sq-problem-banner ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .sq-problem-banner li {
      border-left: 3px solid currentColor;
      padding-left: 0.5rem;
      font-size: 0.875rem;
    }

    .sq-problem-banner li code {
      margin-left: 0.375rem;
      font-size: 0.8125rem;
      word-break: break-all;
    }

    .sq-problem--error {
      color: #b3261e;
    }

    .sq-problem--warning {
      color: #9a6700;
    }

    .sq-problem--info {
      color: #1a56b3;
    }
  `,
})
export class SqProblemBanner {
  readonly problems = input.required<ApiError | readonly OntologyIssue[]>();
  readonly items = computed(() => normalizeProblems(this.problems()));

  constructor() {
    effect(() => {
      const v = this.problems();
      console.log(
        '[DIAG] zone=',
        typeof (globalThis as unknown as { Zone?: unknown }).Zone,
        'problems=',
        Array.isArray(v) ? v.length : 'obj',
        'items=',
        this.items().length,
      );
    });
  }
}
