import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import type { Provider } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ObjectsClient } from '@sequeless/core-api';
import type { ApiError } from '@sequeless/core-api';
import { OBJECT_SIGNALS_PANEL, OBJECTS_SIGNAL_REFETCH_DELAY_MS, ObjectDetailStore } from '@sequeless/objects';
import { SqProblemBanner } from '@sequeless/ui-kit';

import { parsePayload } from './payload';
import { SignalCatalogue, signalsForState } from './signal-catalogue';

/**
 * Fires the signals the catalogue says the object's type accepts, the ones valid from its current state enabled
 * and the rest greyed. Signals are handled asynchronously (202), so the object is refetched after a delay
 * and the panel never claims the transition happened.
 */
@Component({
  selector: 'sq-automation-signals-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, SqProblemBanner],
  template: `
    @if (catalogue.error(); as catalogueError) {
      <sq-problem-banner [problems]="catalogueError" />
    }
    <label>
      Payload (JSON object, optional)
      <textarea rows="2" (input)="payloadText.set($any($event.target).value)"></textarea>
    </label>
    @if (payloadError(); as payloadError) {
      <p role="alert" data-payload-error>{{ payloadError }}</p>
    }
    @for (s of available().enabled; track s.signalName) {
      <div>
        <button mat-stroked-button type="button" [attr.data-signal]="s.signalName" (click)="fire(s.signalName)">
          {{ s.signalName }}
        </button>
        @if (rowError(); as row) {
          @if (row.signalName === s.signalName) {
            <p role="alert" [attr.data-signal-error]="s.signalName">{{ row.message }}</p>
          }
        }
      </div>
    }
    @for (s of available().disabled; track s.signalName) {
      <div>
        <button mat-stroked-button type="button" disabled [attr.data-signal-disabled]="s.signalName">
          {{ s.signalName }}
        </button>
        <small>requires state {{ stateLabel(s.requires) }}</small>
      </div>
    }
    @if (dispatched(); as name) {
      <p role="status">Signal "{{ name }}" dispatched — takes effect asynchronously.</p>
    }
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
    <button mat-button type="button" data-refresh (click)="refresh()">Refresh</button>
  `,
})
export class SqAutomationSignalsPanel implements OnInit {
  protected readonly catalogue = inject(SignalCatalogue);
  private readonly store = inject(ObjectDetailStore);
  private readonly objects = inject(ObjectsClient);
  private readonly refetchDelayMs = inject(OBJECTS_SIGNAL_REFETCH_DELAY_MS);
  private refetchTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly payloadText = signal('');
  protected readonly payloadError = signal<string | null>(null);
  protected readonly dispatched = signal<string | null>(null);
  protected readonly rowError = signal<{ signalName: string; message: string } | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly available = computed(() => {
    const type = this.store.type();
    const object = this.store.object();
    return type === null || object === null
      ? { enabled: [], disabled: [] }
      : signalsForState(this.catalogue.entries(), type.iri, object.state);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancelRefetch());
  }

  ngOnInit(): void {
    void this.catalogue.ensureLoaded();
  }

  protected stateLabel(iri: string): string {
    return this.store.type()?.stateMachine?.states.find((s) => s.iri === iri)?.label ?? iri;
  }

  protected fire(signalName: string): void {
    const type = this.store.type();
    const object = this.store.object();
    if (type === null || object === null) {
      return;
    }
    const payload = parsePayload(this.payloadText());
    if (!payload.ok) {
      this.payloadError.set(payload.error);
      return;
    }
    this.cancelRefetch();
    this.payloadError.set(null);
    this.dispatched.set(null);
    this.rowError.set(null);
    this.error.set(null);
    this.objects.signal(type.name, object.id, signalName, payload.value).subscribe({
      complete: () => {
        this.dispatched.set(signalName);
        this.refetchTimer = setTimeout(() => {
          this.refetchTimer = null;
          void this.store.refetch();
        }, this.refetchDelayMs);
      },
      error: (err: ApiError) => {
        if (err.slug === 'unknown-signal') {
          this.rowError.set({ signalName: err.signalName ?? signalName, message: err.detail ?? err.title });
          void this.catalogue.refresh();
        } else {
          this.error.set(err);
        }
      },
    });
  }

  protected refresh(): void {
    void this.store.refetch();
    void this.catalogue.refresh();
  }

  private cancelRefetch(): void {
    if (this.refetchTimer !== null) {
      clearTimeout(this.refetchTimer);
      this.refetchTimer = null;
    }
  }
}

export function provideAutomationSignalsPanel(): Provider {
  return { provide: OBJECT_SIGNALS_PANEL, useValue: SqAutomationSignalsPanel };
}
