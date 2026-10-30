import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  OnInit,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { readCardinality } from '@sequeless/rdf';
import type { PropertyView, QuadDelta } from '@sequeless/rdf';

import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { diffPropertyForm, propertyFormValue, propertyToForm } from './property-form';
import type { PropertyForm } from './property-form';
import type { PropertyFormValue } from './property-form';

/** Edits one property: label, cardinality and derivation. Datatype and display hints are shown, not edited. */
@Component({
  selector: 'sq-property-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <form [formGroup]="form" class="sq-property-editor" (change)="commit()" (submit)="$event.preventDefault()">
      <mat-form-field>
        <mat-label>Label</mat-label>
        <input matInput data-field="label" formControlName="label" />
      </mat-form-field>
      @if (form.controls.label.errors?.['server']; as message) {
        <span class="sq-server-error" role="alert">{{ message }}</span>
      }
      <mat-form-field>
        <mat-label>Min</mat-label>
        <input matInput type="number" data-field="min" formControlName="min" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Max</mat-label>
        <input matInput type="number" data-field="max" formControlName="max" />
      </mat-form-field>
      @if (form.errors?.['maxBelowMin']) {
        <span class="sq-form-error" role="alert">Max must be at least min.</span>
      }
      <mat-form-field>
        <mat-label>Derivation</mat-label>
        <select matNativeControl data-field="derivationKind" formControlName="derivationKind">
          <option value="none">None</option>
          <option value="plugin">Plugin</option>
          @if (property().derivation?.kind === 'rollup') {
            <option value="rollup">Rollup (clear only)</option>
          }
        </select>
      </mat-form-field>
      @if (form.controls.derivationKind.value === 'plugin') {
        <mat-form-field>
          <mat-label>Plugin name</mat-label>
          <input matInput data-field="pluginName" formControlName="pluginName" />
        </mat-form-field>
      }
    </form>
    <dl class="sq-property-facts">
      <dt>Kind</dt>
      <dd>{{ property().kind === 'attribute' ? attributeDatatype() : 'relationship to ' + relationshipTarget() }}</dd>
      <dt>Display</dt>
      <dd>{{ hints() }}</dd>
    </dl>
  `,
})
export class SqPropertyEditor implements OnInit {
  readonly property = input.required<PropertyView>();

  private readonly session = inject(OntologySession);
  private readonly registry = inject(ControlRegistry);

  protected form!: PropertyForm;

  private readonly destroyRef = inject(DestroyRef);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    effect(() => {
      this.session.revision();
      const p = this.property();
      const store = this.session.store();
      if (store && this.form) {
        this.form.reset(propertyFormValue(p, readCardinality(store, p.domainIri, p.iri)));
      }
    });
  }

  ngOnInit(): void {
    const p = this.property();
    const store = this.session.store();
    this.form = propertyToForm(p, store ? readCardinality(store, p.domainIri, p.iri) : { min: 0, max: null });
    const label = this.form.controls.label;
    this.registry.register(p.iri, label, this.element);
    this.destroyRef.onDestroy(() => this.registry.unregister(label));
  }

  protected attributeDatatype(): string {
    const p = this.property();
    return p.kind === 'attribute' ? p.datatype : '';
  }

  protected relationshipTarget(): string {
    const p = this.property();
    return p.kind === 'relationship' ? p.targetTypeIri : '';
  }

  protected hints(): string {
    const p = this.property();
    const flags = (
      [
        ['facet', p.facet],
        ['indexed', p.indexed],
        ['searchable', p.searchable],
        ['hidden', p.hidden],
        ['read-only', p.readOnly],
      ] as const
    )
      .filter(([, on]) => on)
      .map(([name]) => name);
    if (p.group) {
      flags.unshift(`group ${p.group}` as never);
    }
    return flags.length > 0 ? flags.join(', ') : 'none';
  }

  protected commit(): void {
    const store = this.session.store();
    if (!store || this.form.invalid) {
      return;
    }
    const p = this.property();
    const cardinality = readCardinality(store, p.domainIri, p.iri);
    const deltas = diffPropertyForm(store, p, cardinality, this.form.getRawValue() as PropertyFormValue);
    if (deltas.length === 0) {
      return;
    }
    const merged: QuadDelta = {
      added: deltas.flatMap((d) => d.added),
      removed: deltas.flatMap((d) => d.removed),
    };
    this.session.apply(merged);
  }
}
