import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { addAttributeProperty, addRelationshipProperty, readClasses, readProperties } from '@sequeless/rdf';
import type { AttributePropertySpec, RelationshipPropertySpec } from '@sequeless/rdf';
import { firstValueFrom } from 'rxjs';

import { OntologySession } from '../session/ontology-session';
import { openAddProperty } from './add-property-dialog';
import { SqPropertyEditor } from './property-editor';

/** The properties declared on a type, one editor each, plus the actions that add new ones. */
@Component({
  selector: 'sq-property-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, SqPropertyEditor],
  template: `
    @for (property of properties(); track property.iri) {
      <sq-property-editor [property]="property" />
    }
    <div class="sq-property-actions">
      <button mat-button data-action="add-attribute" (click)="add('attribute')">Add attribute</button>
      <button mat-button data-action="add-relationship" (click)="add('relationship')">Add relationship</button>
    </div>
  `,
})
export class SqPropertyList {
  readonly typeIri = input.required<string>();

  private readonly session = inject(OntologySession);
  private readonly dialog = inject(MatDialog);

  protected readonly properties = computed(() => {
    this.session.revision();
    const store = this.session.store();
    return store ? readProperties(store).filter((p) => p.domainIri === this.typeIri()) : [];
  });

  protected async add(kind: 'attribute' | 'relationship'): Promise<void> {
    const store = this.session.store();
    if (!store) {
      return;
    }
    const result = await firstValueFrom(
      openAddProperty(this.dialog, {
        kind,
        typeIri: this.typeIri(),
        targets: readClasses(store).map((c) => ({ iri: c.iri, label: c.label ?? c.iri })),
        existingIris: readProperties(store).map((p) => p.iri),
      }),
    );
    if (!result) {
      return;
    }
    this.session.apply(
      kind === 'attribute'
        ? addAttributeProperty(store, result as AttributePropertySpec)
        : addRelationshipProperty(store, result as RelationshipPropertySpec),
    );
  }
}
