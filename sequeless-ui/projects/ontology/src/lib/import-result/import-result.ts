import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import type { MatDialog } from '@angular/material/dialog';
import type { OntologyImportResponse } from '@sequeless/core-api';

/** Shown after an accepted import: how many types the server now has and any warnings it raised. */
@Component({
  selector: 'sq-import-result',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Ontology imported</h2>
    <mat-dialog-content>
      <p>{{ result.typeCount }} types loaded; the ontology is {{ result.consistent ? '' : 'not ' }}consistent.</p>
      @for (issue of result.issues; track $index) {
        <p class="sq-import-warning">
          {{ issue.message }}
          @if (issue.subjectIri) {
            <code>{{ issue.subjectIri }}</code>
          }
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-flat-button data-action="close" (click)="ref.close()">Close</button>
    </mat-dialog-actions>
  `,
})
export class SqImportResult {
  protected readonly ref = inject(MatDialogRef<SqImportResult>);
  protected readonly result = inject<OntologyImportResponse>(MAT_DIALOG_DATA);
}

export function openImportResult(dialog: MatDialog, result: OntologyImportResponse): void {
  dialog.open(SqImportResult, { data: result });
}
