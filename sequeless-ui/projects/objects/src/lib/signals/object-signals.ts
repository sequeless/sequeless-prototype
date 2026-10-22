import {
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  ApiError,
  BusinessObjectResponse,
  ObjectsClient,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { knownSignals } from './known-signals';

/** Signals are handled asynchronously (202), so the object is refetched this long after a dispatch. */
export const OBJECTS_SIGNAL_REFETCH_DELAY_MS = new InjectionToken<number>(
  'OBJECTS_SIGNAL_REFETCH_DELAY_MS',
  { providedIn: 'root', factory: () => 1500 },
);

/**
 * Fires the signals that the object's type says apply from its current state. The catalogue of all
 * signals belongs to another feature; this panel only offers the ones known for this object.
 */
@Component({
  selector: 'sq-object-signals',
  imports: [MatButtonModule, SqProblemBanner],
  template: `
    @for (name of signalNames(); track name) {
      <button mat-stroked-button type="button" [attr.data-signal]="name" (click)="fire(name)">
        {{ name }}
      </button>
    }
    @if (dispatched(); as name) {
      <p role="status">Signal "{{ name }}" dispatched — takes effect asynchronously.</p>
    }
    @if (error(); as error) {
      @if (error.slug === 'unknown-signal') {
        <p role="alert" data-signal-error>{{ error.detail }}</p>
      } @else {
        <sq-problem-banner [problems]="error" />
      }
    }
    <button mat-button type="button" (click)="refresh.emit()">Refresh</button>
  `,
})
export class SqObjectSignals {
  private readonly objects = inject(ObjectsClient);
  private readonly refetchDelayMs = inject(OBJECTS_SIGNAL_REFETCH_DELAY_MS);
  private refetchTimer: ReturnType<typeof setTimeout> | null = null;

  readonly type = input.required<TypeDetailResponse>();
  readonly object = input.required<BusinessObjectResponse>();
  readonly refresh = output<void>();

  readonly dispatched = signal<string | null>(null);
  readonly error = signal<ApiError | null>(null);

  protected readonly signalNames = computed(() => knownSignals(this.type(), this.object().state));

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancelRefetch());
  }

  async fire(name: string): Promise<void> {
    this.cancelRefetch();
    this.dispatched.set(null);
    this.error.set(null);
    try {
      await firstValueFrom(this.objects.signal(this.type().name, this.object().id, name));
      this.dispatched.set(name);
      this.refetchTimer = setTimeout(() => {
        this.refetchTimer = null;
        this.refresh.emit();
      }, this.refetchDelayMs);
    } catch (err) {
      this.error.set(err as ApiError);
    }
  }

  private cancelRefetch(): void {
    if (this.refetchTimer !== null) {
      clearTimeout(this.refetchTimer);
      this.refetchTimer = null;
    }
  }
}
