import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';

import type { OntologyDiff } from './ontology-diff';
import { SqTextDiff } from './text-diff';

/** Quad changes grouped by subject, followed by a side-by-side text diff. */
@Component({
  selector: 'sq-diff-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatExpansionModule, SqTextDiff],
  template: `
    @if (diff().subjects.length === 0) {
      <p>No changes.</p>
    } @else {
      <mat-accordion multi>
        @for (change of diff().subjects; track change.subject) {
          <mat-expansion-panel [attr.data-subject]="change.subject">
            <mat-expansion-panel-header>
              <mat-panel-title>{{ change.subject }}</mat-panel-title>
              <mat-panel-description>
                +{{ change.added.length }} −{{ change.removed.length }}
              </mat-panel-description>
            </mat-expansion-panel-header>
            @for (line of change.removed; track $index) {
              <pre class="sq-removed">− {{ line }}</pre>
            }
            @for (line of change.added; track $index) {
              <pre class="sq-added">+ {{ line }}</pre>
            }
          </mat-expansion-panel>
        }
      </mat-accordion>
    }
    <sq-text-diff [base]="diff().baseText" [working]="diff().workingText" />
  `,
})
export class SqDiffPreview {
  readonly diff = input.required<OntologyDiff>();
}
