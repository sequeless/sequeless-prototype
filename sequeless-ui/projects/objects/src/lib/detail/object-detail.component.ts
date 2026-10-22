import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import {
  ApiError,
  ObjectsClient,
  TransitionResponse,
} from '@sequeless/core-api';
import { openConflictDialog, SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { buildObjectForm, formToProperties, SqObjectForm } from './object-form';
import { ObjectDetailStore } from './object-detail.store';

/** View and edit one object. Concurrency conflicts are surfaced, never silently retried. */
@Component({
  selector: 'sq-object-detail',
  imports: [MatButtonModule, SqObjectForm, SqProblemBanner],
  providers: [ObjectDetailStore],
  template: `
    @if (store.object()) {
      <h2>{{ typeName() }}</h2>
      @if (store.type(); as type) {
        <sq-object-form [type]="type" [form]="form()" />
        <button mat-raised-button type="button" (click)="save()">Save</button>
      }
      <div class="transitions">
        @for (transition of transitions(); track transition.name) {
          <button
            mat-stroked-button
            type="button"
            [disabled]="!transition.available"
            [attr.data-transition]="transition.name"
            (click)="fire(transition.name)"
          >
            {{ transition.name }}
          </button>
        }
      </div>
    }
    @if (store.error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
  `,
})
export class SqObjectDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly objects = inject(ObjectsClient);
  private readonly dialog = inject(MatDialog);
  protected readonly store = inject(ObjectDetailStore);

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  protected readonly typeName = computed(() => this.params().get('type') ?? '');
  private readonly objectId = computed(() => this.params().get('id') ?? '');

  readonly form = signal<FormGroup>(new FormGroup({}));
  protected readonly transitions = signal<readonly TransitionResponse[]>([]);
  private loadedProperties: Record<string, unknown> = {};

  async ngOnInit(): Promise<void> {
    await this.store.load(this.typeName(), this.objectId());
    this.resetForm();
    await this.loadTransitions();
  }

  async save(): Promise<void> {
    const type = this.store.type();
    if (type === null) {
      return;
    }
    const local = formToProperties(type, this.form());
    try {
      await firstValueFrom(this.objects.update(this.typeName(), this.objectId(), local));
      await this.store.refetch();
      this.resetForm();
    } catch (err) {
      await this.onSaveError(err as ApiError, local);
    }
  }

  async fire(name: string): Promise<void> {
    try {
      await firstValueFrom(this.objects.fireTransition(this.typeName(), this.objectId(), name));
      await this.store.refetch();
      await this.loadTransitions();
    } catch (err) {
      const error = err as ApiError;
      if (error.transitionName !== null) {
        await this.loadTransitions();
      } else {
        this.store.error.set(error);
      }
    }
  }

  private async onSaveError(error: ApiError, local: Record<string, unknown>): Promise<void> {
    if (error.slug !== 'stale-object') {
      this.store.error.set(error);
      return;
    }

    const server = await this.store.refetch();
    const resolution = await firstValueFrom(
      openConflictDialog(this.dialog, {
        local,
        server: server?.properties ?? {},
        base: this.loadedProperties,
      }),
      { defaultValue: undefined },
    );

    if (resolution?.action === 'reapply') {
      // The refetch above recorded the fresh ETag, so this save carries the current version.
      this.rebuildForm(resolution.value);
      await this.save();
    } else if (resolution?.action === 'discard') {
      this.resetForm();
    }
    // A dismissed dialog keeps the user's edits in the form for another attempt.
  }

  private async loadTransitions(): Promise<void> {
    this.transitions.set(
      await firstValueFrom(this.objects.transitions(this.typeName(), this.objectId())),
    );
  }

  private resetForm(): void {
    this.loadedProperties = this.store.object()?.properties ?? {};
    this.rebuildForm(this.loadedProperties);
  }

  private rebuildForm(initial: Record<string, unknown>): void {
    const type = this.store.type();
    if (type !== null) {
      this.form.set(buildObjectForm(type, initial));
    }
  }
}
