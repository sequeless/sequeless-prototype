/*
 * Contract [ONT-PROPLIST] (add dialog): SqAddPropertyDialog collects the minimum for addAttributeProperty /
 * addRelationshipProperty. It takes `{kind, typeIri, targets, existingIris}`; the new property's IRI is the
 * class's namespace plus the typed name, which must be an identifier and not already exist. "Add" closes with
 * the spec, "Cancel" with undefined. namespaceOf() is the part of an IRI up to and including the last '#' or '/'.
 */
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { namespaceOf, SqAddPropertyDialog } from './add-property-dialog';
import type { AddPropertyDialogData } from './add-property-dialog';

const DATA = (kind: 'attribute' | 'relationship'): AddPropertyDialogData => ({
  kind,
  typeIri: 'http://ex.org/Task',
  targets: [{ iri: 'http://ex.org/Person', label: 'Person' }],
  existingIris: ['http://ex.org/title'],
});

function open(data: AddPropertyDialogData) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture = TestBed.createComponent(SqAddPropertyDialog);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const set = (field: string, value: string) => {
    const control = el.querySelector(`[data-field="${field}"]`) as HTMLInputElement;
    control.value = value;
    control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const click = (action: string) => (el.querySelector(`[data-action="${action}"]`) as HTMLButtonElement).click();
  const addButton = () => el.querySelector('[data-action="add"]') as HTMLButtonElement;
  return { fixture, el, close, set, click, addButton };
}

describe('namespaceOf', () => {
  it('keeps everything up to the last # or /', () => {
    expect(namespaceOf('http://ex.org/ns#Task')).toBe('http://ex.org/ns#');
    expect(namespaceOf('http://ex.org/Task')).toBe('http://ex.org/');
    expect(namespaceOf('urn:Task')).toBe('urn:Task');
  });
});

describe('SqAddPropertyDialog', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('closes with an attribute spec', () => {
    const { set, click, close } = open(DATA('attribute'));
    set('name', 'dueDate');
    set('label', 'Due');
    set('datatype', 'DATE');
    click('add');
    expect(close).toHaveBeenCalledWith({
      iri: 'http://ex.org/dueDate',
      domainIri: 'http://ex.org/Task',
      datatype: 'DATE',
      label: 'Due',
    });
  });

  it('closes with a relationship spec', () => {
    const { set, click, close } = open(DATA('relationship'));
    set('name', 'owner');
    set('target', 'http://ex.org/Person');
    click('add');
    expect(close).toHaveBeenCalledWith({
      iri: 'http://ex.org/owner',
      domainIri: 'http://ex.org/Task',
      targetTypeIri: 'http://ex.org/Person',
    });
  });

  it('omits an empty label', () => {
    const { set, click, close } = open(DATA('attribute'));
    set('name', 'x');
    click('add');
    expect(close.mock.calls[0][0]).not.toHaveProperty('label');
  });

  it('disables Add for a bad or duplicate name', () => {
    const { set, addButton } = open(DATA('attribute'));
    expect(addButton().disabled).toBe(true);
    set('name', '1bad name');
    expect(addButton().disabled).toBe(true);
    set('name', 'title');
    expect(addButton().disabled).toBe(true);
    set('name', 'fresh');
    expect(addButton().disabled).toBe(false);
  });

  it('closes with undefined on Cancel', () => {
    const { click, close } = open(DATA('attribute'));
    click('cancel');
    expect(close).toHaveBeenCalledWith(undefined);
  });
});
