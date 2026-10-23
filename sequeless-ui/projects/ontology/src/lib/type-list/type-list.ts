import { ChangeDetectionStrategy, Component, effect, inject, OnInit, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError, TypeSummaryResponse } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { SqImportBar } from '../import-result/import-bar';
import { OntologySession } from '../session/ontology-session';

/** Every type of the schema (abstract ones too), the entry point to the per-type editor. */
@Component({
  selector: 'sq-ontology-type-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatListModule, RouterLink, SqProblemBanner, SqImportBar],
  template: `
    <div class="sq-ontology-toolbar">
      <a data-action="raw-turtle" [routerLink]="['raw']">Raw Turtle</a>
      <sq-import-bar />
    </div>
    @if (session.loadError(); as loadError) {
      <sq-problem-banner [problems]="loadError" />
    }
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    } @else if (loaded() && types().length === 0) {
      <p>No types available.</p>
    } @else {
      <mat-nav-list>
        @for (type of types(); track type.name) {
          <a mat-list-item [attr.data-type]="type.name" [routerLink]="[type.name]">
            <span class="sq-type-label">{{ type.label }}</span>@if (type.abstract) {
              <em> (abstract)</em>
            }
          </a>
        }
      </mat-nav-list>
    }
  `,
})
export class SqOntologyTypeList implements OnInit {
  private readonly client = inject(TypesClient);
  protected readonly session = inject(OntologySession);

  readonly types = signal<TypeSummaryResponse[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<ApiError | null>(null);

  constructor() {
    let first = true;
    effect(() => {
      this.session.importRevision();
      if (first) {
        first = false;
        return;
      }
      void this.refresh();
    });
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([this.session.load(), this.refresh()]);
  }

  private async refresh(): Promise<void> {
    try {
      const all = await firstValueFrom(this.client.list());
      this.types.set([...all].sort((a, b) => a.label.localeCompare(b.label)));
      this.error.set(null);
    } catch (error) {
      this.error.set(error as ApiError);
    } finally {
      this.loaded.set(true);
    }
  }
}
