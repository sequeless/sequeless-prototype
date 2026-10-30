import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { ApiError, ObjectsClient, TransitionResponse } from '@sequeless/core-api';
import { firstValueFrom } from 'rxjs';

import { ObjectDetailStore } from './object-detail.store';

/** The transitions of the object on the detail screen. Shares the detail's `ObjectDetailStore`. */
@Component({
  selector: 'sq-transitions-panel',
  imports: [MatButtonModule],
  template: `
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
  `,
})
export class SqTransitionsPanel implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly objects = inject(ObjectsClient);
  private readonly store = inject(ObjectDetailStore);

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  private readonly typeName = computed(() => this.params().get('type') ?? '');
  private readonly objectId = computed(() => this.params().get('id') ?? '');

  protected readonly transitions = signal<readonly TransitionResponse[]>([]);
  protected readonly transitionError = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.loadTransitions();
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

  private async loadTransitions(): Promise<void> {
    this.transitions.set(
      await firstValueFrom(this.objects.transitions(this.typeName(), this.objectId())),
    );
  }
}
