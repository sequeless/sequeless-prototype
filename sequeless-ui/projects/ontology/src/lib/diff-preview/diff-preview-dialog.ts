import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import type { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';

import { SqDiffPreview } from './diff-preview';
import type { OntologyDiff } from './ontology-diff';

@Component({
  selector: 'sq-diff-preview-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, SqDiffPreview],
  template: `
    <h2 mat-dialog-title>Review changes</h2>
    <mat-dialog-content>
      <sq-diff-preview [diff]="diff" />
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-button data-action="cancel" (click)="ref.close(false)">Cancel</button>
      <button mat-flat-button data-action="import" (click)="ref.close(true)">Import</button>
    </mat-dialog-actions>
  `,
})
export class SqDiffPreviewDialog {
  protected readonly ref = inject(MatDialogRef<SqDiffPreviewDialog, boolean>);
  protected readonly diff = inject<OntologyDiff>(MAT_DIALOG_DATA);
}

/** Opens the preview; resolves `true` when the user chooses Import, `false` otherwise. */
export function openDiffPreview(dialog: MatDialog, diff: OntologyDiff): Observable<boolean> {
  return dialog
    .open<SqDiffPreviewDialog, OntologyDiff, boolean>(SqDiffPreviewDialog, {
      data: diff,
      width: '960px',
      maxWidth: '95vw',
    })
    .afterClosed()
    .pipe(map((result) => result === true));
}
