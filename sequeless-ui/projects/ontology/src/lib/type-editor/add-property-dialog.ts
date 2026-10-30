import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import type { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AttributePropertySpec, Datatype, RelationshipPropertySpec } from '@sequeless/rdf';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';

export interface AddPropertyDialogData {
  kind: 'attribute' | 'relationship';
  typeIri: string;
  targets: readonly { iri: string; label: string }[];
  existingIris: readonly string[];
}

export type AddPropertyResult = AttributePropertySpec | RelationshipPropertySpec;

export const DATATYPES: readonly Datatype[] = [
  'STRING', 'BOOLEAN', 'INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'DATE', 'TIME', 'DATE_TIME', 'DURATION', 'ANY_URI',
];

/** The part of an IRI up to and including the last '#' or '/'. */
export function namespaceOf(iri: string): string {
  const cut = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
  return cut >= 0 ? iri.slice(0, cut + 1) : iri;
}

@Component({
  selector: 'sq-add-property-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Add {{ data.kind }}</h2>
    <mat-dialog-content [formGroup]="form">
      <mat-form-field>
        <mat-label>Name</mat-label>
        <input matInput data-field="name" formControlName="name" />
      </mat-form-field>
      @if (form.controls.name.errors?.['duplicate']) {
        <span class="sq-form-error" role="alert">A property with this name already exists.</span>
      }
      <mat-form-field>
        <mat-label>Label</mat-label>
        <input matInput data-field="label" formControlName="label" />
      </mat-form-field>
      @if (data.kind === 'attribute') {
        <mat-form-field>
          <mat-label>Datatype</mat-label>
          <select matNativeControl data-field="datatype" formControlName="datatype">
            @for (datatype of datatypes; track datatype) {
              <option [value]="datatype">{{ datatype }}</option>
            }
          </select>
        </mat-form-field>
      } @else {
        <mat-form-field>
          <mat-label>Target type</mat-label>
          <select matNativeControl data-field="target" formControlName="target">
            @for (target of data.targets; track target.iri) {
              <option [value]="target.iri">{{ target.label }}</option>
            }
          </select>
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-button data-action="cancel" (click)="ref.close(undefined)">Cancel</button>
      <button mat-flat-button data-action="add" [disabled]="form.invalid" (click)="add()">Add</button>
    </mat-dialog-actions>
  `,
})
export class SqAddPropertyDialog {
  protected readonly ref = inject(MatDialogRef<SqAddPropertyDialog, AddPropertyResult>);
  protected readonly data = inject<AddPropertyDialogData>(MAT_DIALOG_DATA);
  protected readonly datatypes = DATATYPES;

  private readonly namespace = namespaceOf(this.data.typeIri);

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Za-z_][A-Za-z0-9_-]*$/), (c) => this.notDuplicate(c)],
    }),
    label: new FormControl('', { nonNullable: true }),
    datatype: new FormControl<Datatype>('STRING', { nonNullable: true }),
    target: new FormControl(this.data.targets[0]?.iri ?? '', { nonNullable: true }),
  });

  private notDuplicate(control: AbstractControl): ValidationErrors | null {
    return this.data.existingIris.includes(this.namespace + control.value) ? { duplicate: true } : null;
  }

  protected add(): void {
    const { name, label, datatype, target } = this.form.getRawValue();
    const common = {
      iri: this.namespace + name,
      domainIri: this.data.typeIri,
      ...(label.trim() !== '' ? { label: label.trim() } : {}),
    };
    this.ref.close(
      this.data.kind === 'attribute'
        ? { ...common, datatype }
        : { ...common, targetTypeIri: target },
    );
  }
}

export function openAddProperty(
  dialog: MatDialog,
  data: AddPropertyDialogData,
): Observable<AddPropertyResult | undefined> {
  return dialog
    .open<SqAddPropertyDialog, AddPropertyDialogData, AddPropertyResult>(SqAddPropertyDialog, { data })
    .afterClosed()
    .pipe(map((result) => result ?? undefined));
}
