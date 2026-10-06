import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { computed } from '@angular/core';
import { Observable } from 'rxjs';
import { reapplyLocal, threeWayDiff } from '../util/three-way-diff';

export interface ConflictDialogData {
  local: Record<string, unknown>;
  server: Record<string, unknown>;
  base: Record<string, unknown>;
  labels?: Record<string, string>;
}

export interface ConflictResolution {
  action: 'reapply' | 'discard';
  value: Record<string, unknown>;
}

@Component({
  selector: 'sq-conflict-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>This record changed on the server</h2>
    <mat-dialog-content>
      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Original</th>
            <th>Yours</th>
            <th>Server</th>
          </tr>
        </thead>
        <tbody>
          @for (d of changed(); track d.key) {
            <tr [attr.data-key]="d.key" [class.sq-conflict]="d.conflict">
              <th>
                {{ label(d.key) }}
              </th>
              <td data-col="base">{{ show(d.base) }}</td>
              <td data-col="local">{{ show(d.local) }}</td>
              <td data-col="server">{{ show(d.server) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-button [attr.data-action]="'discard'" (click)="discard()">
        Discard my changes
      </button>
      <button
        mat-raised-button
        [attr.data-action]="'reapply'"
        (click)="reapply()"
      >
        Reapply my changes
      </button>
    </mat-dialog-actions>
  `,
})
export class SqConflictDialog {
  private readonly ref = inject(MatDialogRef<SqConflictDialog, ConflictResolution>);

  readonly data = inject<ConflictDialogData>(MAT_DIALOG_DATA);

  readonly diffs = computed(() =>
    threeWayDiff(this.data.local, this.data.server, this.data.base),
  );

  readonly changed = computed(() =>
    this.diffs().filter((d) => d.localChanged || d.serverChanged),
  );

  label(key: string): string {
    const label = this.data.labels?.[key];
    return label === undefined ? key : label;
  }

  show(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    if (typeof value === 'object') {
      return JSON.stringify(value);
    }
    return String(value);
  }

  discard(): void {
    this.ref.close({ action: 'discard', value: this.data.server });
  }

  reapply(): void {
    this.ref.close({
      action: 'reapply',
      value: reapplyLocal(this.diffs()),
    });
  }
}

export function openConflictDialog(
  dialog: MatDialog,
  data: ConflictDialogData,
): Observable<ConflictResolution | undefined> {
  return dialog
    .open<SqConflictDialog, ConflictDialogData, ConflictResolution>(
      SqConflictDialog,
      { data, disableClose: true },
    )
    .afterClosed();
}
