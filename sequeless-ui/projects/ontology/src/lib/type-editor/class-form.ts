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
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { readClasses, relabel } from '@sequeless/rdf';
import type { ClassView } from '@sequeless/rdf';

import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';

/** Edits a class's label. Abstract and super-types are shown only (D10): rdf has no edit for them yet. */
@Component({
  selector: 'sq-class-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field>
      <mat-label>Label</mat-label>
      <input matInput data-field="label" [formControl]="label" (change)="commit()" />
    </mat-form-field>
    @if (label.errors?.['server']; as message) {
      <span class="sq-server-error" role="alert">{{ message }}</span>
    }
    <dl class="sq-class-facts">
      <dt>Abstract</dt>
      <dd data-field="abstract">{{ view()?.abstract ? 'Yes' : 'No' }}</dd>
      <dt>Super-types</dt>
      <dd data-field="super-types">{{ superTypes() }}</dd>
    </dl>
  `,
})
export class SqClassForm implements OnInit {
  readonly typeIri = input.required<string>();

  protected readonly label = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  private readonly session = inject(OntologySession);
  private readonly registry = inject(ControlRegistry);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      this.label.reset(this.view()?.label ?? '');
    });
  }

  ngOnInit(): void {
    this.registry.register(this.typeIri(), this.label, this.element);
    this.destroyRef.onDestroy(() => this.registry.unregister(this.label));
  }

  protected view(): ClassView | undefined {
    this.session.revision();
    const store = this.session.store();
    return store ? readClasses(store).find((c) => c.iri === this.typeIri()) : undefined;
  }

  protected superTypes(): string {
    const supers = this.view()?.superClassIris ?? [];
    return supers.length > 0 ? supers.join(', ') : 'none';
  }

  protected commit(): void {
    const store = this.session.store();
    const value = this.label.value.trim();
    if (!store || this.label.invalid || value === (this.view()?.label ?? '')) {
      return;
    }
    this.session.apply(relabel(store, this.typeIri(), value));
  }
}
