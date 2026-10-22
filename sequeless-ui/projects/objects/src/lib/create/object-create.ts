import { Component, inject, signal, OnInit } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ApiError,
  CreateObjectRequest,
  ObjectsClient,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { buildObjectForm, formToProperties, SqObjectForm } from '../detail/object-form';

/** Creates one object of the routed type from the same dynamic form the detail view uses. */
@Component({
  selector: 'sq-object-create',
  imports: [MatButtonModule, SqObjectForm, SqProblemBanner],
  template: `
    @if (type(); as type) {
      <h2>New {{ typeName() }}</h2>
      <sq-object-form [type]="type" [form]="form()" />
      <button mat-raised-button type="button" (click)="save()">Create</button>
    }
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
  `,
})
export class SqObjectCreate implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly objects = inject(ObjectsClient);
  private readonly types = inject(TypesClient);

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  protected readonly typeName = () => this.params().get('type') ?? '';

  readonly type = signal<TypeDetailResponse | null>(null);
  readonly form = signal<FormGroup>(new FormGroup({}));
  readonly error = signal<ApiError | null>(null);

  async ngOnInit(): Promise<void> {
    const type = await firstValueFrom(this.types.describe(this.typeName()));
    this.type.set(type);
    this.form.set(buildObjectForm(type));
  }

  async save(): Promise<void> {
    const type = this.type();
    if (type === null) {
      return;
    }
    const form = this.form();
    const body: CreateObjectRequest = { properties: formToProperties(type, form) };
    try {
      const created = await firstValueFrom(this.objects.create(this.typeName(), body));
      await this.router.navigate(['/objects', this.typeName(), created.id]);
    } catch (err) {
      const error = err as ApiError;
      this.error.set(error);
      if (error.status === 400) {
        this.markViolations(form, error);
      }
    }
  }

  private markViolations(form: FormGroup, error: ApiError): void {
    for (const violation of error.violations) {
      if (violation.property !== null) {
        form.get(violation.property)?.setErrors({ server: violation.message });
      }
    }
  }
}
