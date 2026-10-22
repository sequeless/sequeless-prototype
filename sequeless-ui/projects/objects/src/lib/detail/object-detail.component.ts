import { NgComponentOutlet } from '@angular/common';
import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ApiError,
  ObjectsClient,
  TransitionResponse,
} from '@sequeless/core-api';
import { openConflictDialog, SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { OBJECT_SIGNALS_PANEL } from '../signals/object-signals';
import { buildObjectForm, formToProperties, SqObjectForm } from './object-form';
import { ObjectDetailStore } from './object-detail.store';

/** View and edit one object. Concurrency conflicts are surfaced, never silently retried. */
@Component({
  selector: 'sq-object-detail',
  imports: [MatButtonModule, NgComponentOutlet, SqObjectForm, SqProblemBanner],
  providers: [ObjectDetailStore],
  template: `
    @if (store.object()) {
      <h2>{{ typeName() }}</h2>
      @if (store.type(); as type) {
        <sq-object-form [type]="type" [form]="form()" />
        @if (editing()) {
          <button mat-raised-button type="button" data-action="save" (click)="save()">Save</button>
          <button mat-stroked-button type="button" data-action="cancel" (click)="cancel()">
            Cancel
          </button>
        } @else {
          <button mat-raised-button type="button" data-action="edit" (click)="edit()">Edit</button>
        }
        <button mat-stroked-button type="button" data-action="delete" (click)="delete()">
          Delete
        </button>
        <ng-container *ngComponentOutlet="signalsPanel" />
      }
      <div class="transitions">
        @for (transition of transitions(); track transition.name) {
          <button
            mat-stroked-button
            type="button"
            [disabled]="!transition.available"
            [attr.data-transition]="transition.name"
            [attr.title]="transition.reason"
            (click)="fire(transition.name)"
          >
            {{ transition.name }}
          </button>
        }
      </div>
      @if (transitionError(); as detail) {
        <p role="alert" data-transition-error>{{ detail }}</p>
      }
    }
    @if (store.error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
  `,
})
export class SqObjectDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly objects = inject(ObjectsClient);
  private readonly dialog = inject(MatDialog);
  readonly store = inject(ObjectDetailStore);
  protected readonly signalsPanel = inject(OBJECT_SIGNALS_PANEL);

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  protected readonly typeName = computed(() => this.params().get('type') ?? '');
  private readonly objectId = computed(() => this.params().get('id') ?? '');

  readonly form = signal<FormGroup>(new FormGroup({}));
  /** View mode is `editing() === false`: the form is fully disabled and no Save or Cancel shows. */
  readonly editing = signal(false);
  protected readonly transitions = signal<readonly TransitionResponse[]>([]);
  protected readonly transitionError = signal<string | null>(null);
  private loadedProperties: Record<string, unknown> = {};

  async ngOnInit(): Promise<void> {
    await this.store.load(this.typeName(), this.objectId());
    this.resetForm();
    await this.loadTransitions();
  }

  edit(): void {
    this.editing.set(true);
    this.rebuildForm(this.loadedProperties);
  }

  cancel(): void {
    this.editing.set(false);
    this.resetForm();
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
      this.editing.set(false);
      this.resetForm();
    } catch (err) {
      await this.onSaveError(err as ApiError, local);
    }
  }

  async delete(): Promise<void> {
    if (!window.confirm('Delete this object?')) {
      return;
    }
    try {
      await firstValueFrom(this.objects.delete(this.typeName(), this.objectId()));
    } catch (err) {
      this.store.error.set(err as ApiError);
      return;
    }
    await this.router.navigate(['..'], { relativeTo: this.route });
  }

  async fire(name: string): Promise<void> {
    this.transitionError.set(null);
    try {
      await firstValueFrom(this.objects.fireTransition(this.typeName(), this.objectId(), name));
      await this.store.refetch();
      await this.loadTransitions();
    } catch (err) {
      const error = err as ApiError;
      if (error.transitionName !== null) {
        await this.loadTransitions();
        this.transitionError.set(error.detail);
      } else if (error.slug === 'stale-object') {
        // The transition POST carries If-Match, so a concurrent edit can reject it. Refresh and
        // report; never retry the transition silently.
        await this.store.refetch();
        await this.loadTransitions();
        this.store.error.set(error);
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
      const form = buildObjectForm(type, initial);
      if (!this.editing()) {
        // The single place view mode is enforced: every rebuild outside edit mode is disabled.
        form.disable();
      }
      this.form.set(form);
    }
  }
}
