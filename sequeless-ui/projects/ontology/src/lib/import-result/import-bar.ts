import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { SqProblemBanner } from '@sequeless/ui-kit';

import { OntologySession } from '../session/ontology-session';
import { ImportFlow } from './import-flow';
import { openImportResult } from './import-result';

/** Undo / redo / "Review & import", with the flow's problems shown in a banner. */
@Component({
  selector: 'sq-import-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, SqProblemBanner],
  template: `
    <div class="sq-import-bar">
      <button mat-button data-action="undo" [disabled]="!session.canUndo()" (click)="session.undo()">
        Undo
      </button>
      <button mat-button data-action="redo" [disabled]="!session.canRedo()" (click)="session.redo()">
        Redo
      </button>
      <button mat-flat-button data-action="import" [disabled]="!session.dirty()" (click)="review()">
        Review &amp; import
      </button>
    </div>
    @if (flow.problems(); as problems) {
      <sq-problem-banner [problems]="problems" />
    }
  `,
})
export class SqImportBar {
  protected readonly session = inject(OntologySession);
  protected readonly flow = inject(ImportFlow);
  private readonly dialog = inject(MatDialog);

  protected async review(): Promise<void> {
    if ((await this.flow.run()) === 'accepted') {
      const result = this.session.lastImport();
      if (result) {
        openImportResult(this.dialog, result);
      }
    }
  }
}
