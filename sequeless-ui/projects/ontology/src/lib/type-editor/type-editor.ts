import { NgComponentOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError, OntologyIssue } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { ONTOLOGY_EDITOR_PANELS, panelsFor } from '../extension/editor-panels';
import type { OntologyEditorPanelContext } from '../extension/editor-panels';
import { SqImportBar } from '../import-result/import-bar';
import { ControlRegistry } from '../issues/control-registry';
import { SqRawTurtleTab } from '../raw/raw-turtle-tab';
import { OntologySession } from '../session/ontology-session';
import { SqStateMachineEditor } from '../state-machine-editor/state-machine-editor';
import { SqClassForm } from './class-form';
import { SqPropertyList } from './property-list';

/** The editor for one type, reached by the route's `:type` parameter (a type name or IRI). */
@Component({
  selector: 'sq-type-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgComponentOutlet,
    MatTabsModule,
    SqProblemBanner,
    SqImportBar,
    SqClassForm,
    SqPropertyList,
    SqStateMachineEditor,
    SqRawTurtleTab,
  ],
  template: `
    <sq-import-bar />
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    } @else if (typeIri(); as iri) {
      <mat-tab-group>
        <mat-tab label="Class">
          <ng-template matTabContent>
            <sq-class-form [typeIri]="iri" />
            <sq-property-list [typeIri]="iri" />
            @for (panel of extraPanels; track panel.id) {
              <ng-container *ngComponentOutlet="panel.component; inputs: { context: context(iri) }" />
            }
          </ng-template>
        </mat-tab>
        <mat-tab label="State machine">
          <ng-template matTabContent><sq-state-machine-editor [typeIri]="iri" /></ng-template>
        </mat-tab>
        <mat-tab label="Turtle">
          <ng-template matTabContent><sq-raw-turtle-tab /></ng-template>
        </mat-tab>
      </mat-tab-group>
    } @else if (resolved()) {
      <p>Unknown type "{{ name() }}".</p>
    }
  `,
})
export class SqTypeEditor {
  /** Overrides the route's `:type` parameter; the shell does not bind route params to inputs. */
  readonly type = input<string>();

  private readonly params = inject(ActivatedRoute, { optional: true })?.paramMap;
  private readonly routeType = this.params ? toSignal(this.params) : signal(null);
  protected readonly name = computed(() => this.type() ?? this.routeType()?.get('type') ?? '');

  private readonly session = inject(OntologySession);
  private readonly registry = inject(ControlRegistry);
  private readonly types = inject(TypesClient);

  protected readonly extraPanels = panelsFor(inject(ONTOLOGY_EDITOR_PANELS), 'type-extra');
  protected readonly typeIri = signal<string | null>(null);
  protected readonly resolved = signal(false);
  protected readonly error = signal<ApiError | readonly OntologyIssue[] | null>(null);

  constructor() {
    effect((onCleanup) => {
      const type = this.name();
      let stale = false;
      onCleanup(() => (stale = true));
      this.registry.clear();
      this.typeIri.set(null);
      this.resolved.set(false);
      this.error.set(null);
      void this.resolve(type, () => stale);
    });
    inject(DestroyRef).onDestroy(() => this.registry.clear());
  }

  protected context(typeIri: string): OntologyEditorPanelContext {
    return { typeIri, session: this.session, registry: this.registry };
  }

  private async resolve(type: string, isStale: () => boolean): Promise<void> {
    try {
      const [all] = await Promise.all([firstValueFrom(this.types.list()), this.session.ensureLoaded()]);
      if (isStale()) {
        return;
      }
      this.typeIri.set(all.find((t) => t.name === type || t.iri === type)?.iri ?? null);
    } catch (error) {
      if (!isStale()) {
        this.error.set(
          typeof error === 'object' && error !== null && 'issues' in error
            ? (error as ApiError)
            : [{ severity: 'error', subjectIri: null, message: error instanceof Error ? error.message : String(error) }],
        );
      }
    } finally {
      if (!isStale()) {
        this.resolved.set(true);
      }
    }
  }
}
