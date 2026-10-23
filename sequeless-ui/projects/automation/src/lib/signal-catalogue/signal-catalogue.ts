import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError, TypeDetailResponse } from '@sequeless/core-api';
import { firstValueFrom, from, mergeMap, toArray } from 'rxjs';

export interface SignalEntry {
  typeIri: string;
  typeName: string;
  signalName: string;
  from: string;
  to: string;
}

/** How many `GET /api/types/{iri}` the scan keeps in flight (D6). */
export const SIGNAL_CATALOGUE_CONCURRENCY = new InjectionToken<number>('Signal catalogue concurrency', {
  providedIn: 'root',
  factory: () => 4,
});

export function collectSignals(type: TypeDetailResponse): SignalEntry[] {
  const transitions = type.stateMachine?.transitions ?? [];
  return transitions.flatMap((t) =>
    t.trigger === 'EXTERNAL_SIGNAL' && t.signalName != null
      ? [{ typeIri: type.iri, typeName: type.name, signalName: t.signalName, from: t.from, to: t.to }]
      : [],
  );
}

export function signalsForState(
  entries: readonly SignalEntry[],
  typeIri: string,
  stateIri: string | null,
): { enabled: SignalEntry[]; disabled: (SignalEntry & { requires: string })[] } {
  const own = entries.filter((e) => e.typeIri === typeIri);
  return {
    enabled: own.filter((e) => stateIri !== null && e.from === stateIri),
    disabled: own
      .filter((e) => stateIri === null || e.from !== stateIri)
      .map((e) => ({ ...e, requires: e.from })),
  };
}

/** The signals every non-abstract type declares: one `describe` per type, bounded, cached until `refresh()`. */
@Injectable({ providedIn: 'root' })
export class SignalCatalogue {
  private readonly types = inject(TypesClient);
  private readonly concurrency = inject(SIGNAL_CATALOGUE_CONCURRENCY);
  private loaded = false;
  private scanning: Promise<void> | null = null;

  private readonly entriesSignal = signal<readonly SignalEntry[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<ApiError | null>(null);

  readonly entries = this.entriesSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  ensureLoaded(): Promise<void> {
    return this.loaded ? Promise.resolve() : this.refresh();
  }

  refresh(): Promise<void> {
    this.scanning ??= this.scan().finally(() => (this.scanning = null));
    return this.scanning;
  }

  private async scan(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const summaries = (await firstValueFrom(this.types.list())).filter((t) => !t.abstract);
      const details = await firstValueFrom(
        from(summaries).pipe(
          mergeMap((t) => this.types.describe(t.name), this.concurrency),
          toArray(),
        ),
      );
      this.entriesSignal.set(details.flatMap(collectSignals));
      this.loaded = true;
    } catch (err) {
      this.loaded = false;
      this.errorSignal.set(err as ApiError);
    } finally {
      this.loadingSignal.set(false);
    }
  }
}
