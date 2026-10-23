import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError, TypeSummaryResponse } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

/** The entry point: every concrete type, each linking to its state-machine view. */
@Component({
  selector: 'sq-automation-type-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatListModule, RouterLink, SqProblemBanner],
  template: `
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    } @else if (loaded() && types().length === 0) {
      <p>No types available.</p>
    } @else {
      <mat-nav-list>
        @for (type of types(); track type.name) {
          <a mat-list-item [attr.data-type]="type.name" [routerLink]="[type.name]">{{ type.label }}</a>
        }
      </mat-nav-list>
    }
  `,
})
export class SqAutomationTypeList implements OnInit {
  private readonly client = inject(TypesClient);

  readonly types = signal<TypeSummaryResponse[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<ApiError | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const all = await firstValueFrom(this.client.list());
      this.types.set(all.filter((t) => !t.abstract).sort((a, b) => a.label.localeCompare(b.label)));
    } catch (error) {
      this.error.set(error as ApiError);
    } finally {
      this.loaded.set(true);
    }
  }
}
