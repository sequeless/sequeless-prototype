import {
  ChangeDetectionStrategy,
  Component,
  ComponentRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
  ViewContainerRef,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError, TypeDetailResponse } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { AutomationGapsNote } from './gaps-note';
import { STATE_MACHINE_DIAGRAM } from './state-machine-diagram';
import type { StateMachineDiagramContract } from './state-machine-diagram';
import { describeTransition, stateBrowseLink } from './transition-format';

/**
 * One type's whole state machine, drawn from the type endpoint (every trigger kind). The per-object
 * transitions endpoint lists callable actions only and is never used here.
 */
@Component({
  selector: 'sq-state-machine-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SqProblemBanner, AutomationGapsNote],
  template: `
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
    @if (detail(); as d) {
      <h2>{{ d.label }}</h2>
      @if (d.stateMachine === null) {
        <p>This type has no state machine.</p>
      }
    }
    <ng-container #outlet />
    @if (selected(); as t) {
      <section data-transition-panel>
        <h3>{{ t.name }}</h3>
        <p>{{ describe(t) }}</p>
        <a [routerLink]="['/ontology', name()]">Edit in ontology</a>
      </section>
    }
    <sq-automation-gaps-note [sm]="detail()?.stateMachine ?? null" />
  `,
})
export class SqStateMachineView {
  /** Overrides the route's `:type` parameter; the shell does not bind route params to inputs. */
  readonly type = input<string>();

  private readonly types = inject(TypesClient);
  private readonly router = inject(Router);
  private readonly diagram = inject(STATE_MACHINE_DIAGRAM);
  private readonly params = inject(ActivatedRoute, { optional: true })?.paramMap;
  private readonly routeType = this.params ? toSignal(this.params) : signal(null);
  private readonly outlet = viewChild.required('outlet', { read: ViewContainerRef });

  protected readonly name = computed(() => this.type() ?? this.routeType()?.get('type') ?? '');
  protected readonly detail = signal<TypeDetailResponse | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  private readonly selectedName = signal<string | null>(null);
  private ref: ComponentRef<StateMachineDiagramContract> | null = null;

  protected readonly selected = computed(() => {
    const name = this.selectedName();
    return this.detail()?.stateMachine?.transitions.find((t) => t.name === name) ?? null;
  });

  constructor() {
    effect((onCleanup) => {
      const name = this.name();
      let stale = false;
      onCleanup(() => (stale = true));
      this.detail.set(null);
      this.error.set(null);
      this.selectedName.set(null);
      if (name) {
        void this.load(name, () => stale);
      }
    });

    effect(() => {
      const sm = this.detail()?.stateMachine ?? null;
      untracked(() => this.mount(sm));
    });

    effect(() => {
      const selected = this.selectedName();
      this.ref?.setInput('selectedTransition', selected);
    });
  }

  protected describe(t: NonNullable<ReturnType<typeof this.selected>>): string {
    const labels = new Map(this.detail()?.stateMachine?.states.map((s) => [s.iri, s.label]));
    return describeTransition(t, (iri) => labels.get(iri) ?? iri);
  }

  private mount(sm: NonNullable<TypeDetailResponse['stateMachine']> | null): void {
    this.outlet().clear();
    this.ref = null;
    if (sm === null) {
      return;
    }
    const ref = this.outlet().createComponent(this.diagram) as ComponentRef<StateMachineDiagramContract>;
    ref.setInput('stateMachine', sm);
    ref.setInput('selectedTransition', this.selectedName());
    ref.instance.transitionSelect.subscribe((name) => this.selectedName.set(name));
    ref.instance.stateSelect.subscribe((iri) => this.browse(sm, iri));
    this.ref = ref;
  }

  private browse(sm: NonNullable<TypeDetailResponse['stateMachine']>, iri: string): void {
    const state = sm.states.find((s) => s.iri === iri);
    if (state) {
      const link = stateBrowseLink(this.name(), state.name);
      void this.router.navigate(link.commands, { queryParams: link.queryParams });
    }
  }

  private async load(name: string, isStale: () => boolean): Promise<void> {
    try {
      const detail = await firstValueFrom(this.types.describe(name));
      if (!isStale()) {
        this.detail.set(detail);
      }
    } catch (error) {
      if (!isStale()) {
        this.error.set(error as ApiError);
      }
    }
  }
}
