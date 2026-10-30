import { Component, inject, OnInit, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { ApiError, TypesClient, TypeSummaryResponse } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

/** Lists the concrete types, sorted by label, each linking to its object browse view. */
@Component({
  selector: 'sq-type-list',
  imports: [MatListModule, RouterLink, SqProblemBanner],
  template: `
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    } @else if (loaded() && types().length === 0) {
      <p>No types available.</p>
    } @else {
      <mat-nav-list>
        @for (type of types(); track type.name) {
          <a mat-list-item [routerLink]="[type.name]">{{ type.label }}</a>
        }
      </mat-nav-list>
    }
  `,
})
export class SqTypeList implements OnInit {
  private readonly client = inject(TypesClient);

  readonly types = signal<TypeSummaryResponse[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<ApiError | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const all = await firstValueFrom(this.client.list());
      this.types.set(
        all.filter((type) => !type.abstract).sort((a, b) => a.label.localeCompare(b.label)),
      );
      this.loaded.set(true);
    } catch (err) {
      this.error.set(err as ApiError);
    }
  }
}
