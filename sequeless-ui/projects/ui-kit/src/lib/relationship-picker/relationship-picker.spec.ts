/**
 * Contract under test (lib/relationship-picker/relationship-picker.ts): `SqRelationshipPicker`
 * (selector `sq-relationship-picker`) takes a `RelationshipPropertyResponse` and a
 * `FormControl<unknown>` as required signal inputs, and backs a `mat-autocomplete` with the
 * injected `RELATIONSHIP_LOOKUP` (phase-5 spec, "Relationship picker"). On init it resolves
 * `lookup.targetType(property().targetTypeIri)` once, to build option/chip labels via
 * `pickLabel` from `../util/labels`. Typing into the native
 * `<input matInput [matAutocomplete] data-field="{{ property().name }}">` is debounced 300ms
 * before calling `lookup.search(property().targetTypeIri, term)`; the resolved objects are
 * rendered as `mat-option`s labelled via `pickLabel(obj, targetType)`.
 *
 * Cardinality (via `isRepeatable` from `../util/validators`) decides the control shape: a
 * single-valued relationship's `control.value` is the bare target id string, and calling
 * `select()` again simply overwrites it. A repeatable relationship's `control.value` is a
 * `string[]`; `select()` appends the id (skipping a duplicate already present), rendered as a
 * `mat-chip-grid` of `<mat-chip-row data-id="{{id}}">` labelled from a label cache populated at
 * select time (so `remove(id)` never needs to re-fetch); the label falls back to the raw id when
 * the target type has no non-hidden STRING attribute property. In every case the control only
 * ever holds bare id string(s), never the `BusinessObjectResponse` itself.
 */
import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type {
  BusinessObjectResponse,
  RelationshipPropertyResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { isRelationshipProperty } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { type Observable, of } from 'rxjs';
import { RELATIONSHIP_LOOKUP, type RelationshipLookup } from './relationship-lookup';
import { SqRelationshipPicker } from './relationship-picker';

const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const findRelationship = (name: string): RelationshipPropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || !isRelationshipProperty(property)) {
    throw new Error(`fixture has no relationship property named "${name}"`);
  }
  return property;
};

// owner: relationship, cardinalityMax 1 (single-valued), targetTypeIri Person
// hasTask: relationship, cardinalityMax null (repeatable), targetTypeIri Task
const OWNER = findRelationship('owner');
const HAS_TASK = findRelationship('hasTask');

const PERSON_TYPE_DETAIL: TypeDetailResponse = {
  iri: OWNER.targetTypeIri,
  name: 'Person',
  label: 'Person',
  abstract: false,
  superTypes: [],
  group: null,
  hidden: false,
  properties: [
    {
      kind: 'attribute',
      iri: `${OWNER.targetTypeIri}#name`,
      name: 'name',
      label: 'Name',
      order: 1,
      group: null,
      hidden: false,
      facet: false,
      indexed: false,
      searchable: true,
      readOnly: false,
      cardinalityMin: 0,
      cardinalityMax: 1,
      datatype: 'STRING',
      derivation: null,
    },
  ],
  stateMachine: null,
};

// Task has no non-hidden STRING attribute property, so pickLabel always falls back to the id.
const TASK_TYPE_DETAIL: TypeDetailResponse = {
  iri: HAS_TASK.targetTypeIri,
  name: 'Task',
  label: 'Task',
  abstract: false,
  superTypes: [],
  group: null,
  hidden: false,
  properties: [
    {
      kind: 'attribute',
      iri: `${HAS_TASK.targetTypeIri}#priority`,
      name: 'priority',
      label: 'Priority',
      order: 1,
      group: null,
      hidden: false,
      facet: false,
      indexed: false,
      searchable: false,
      readOnly: false,
      cardinalityMin: 0,
      cardinalityMax: 1,
      datatype: 'INTEGER',
      derivation: null,
    },
  ],
  stateMachine: null,
};

function makePerson(id: string, name: string): BusinessObjectResponse {
  return {
    id,
    type: 'Person',
    typeIri: PERSON_TYPE_DETAIL.iri,
    version: 0,
    state: null,
    properties: { name },
    audit: {
      createdAt: '2026-09-28T10:15:30Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-28T10:15:30Z',
      updatedBy: 'anonymous',
    },
  };
}

function makeTask(id: string): BusinessObjectResponse {
  return {
    id,
    type: 'Task',
    typeIri: TASK_TYPE_DETAIL.iri,
    version: 0,
    state: null,
    properties: { priority: 1 },
    audit: {
      createdAt: '2026-09-28T10:15:30Z',
      createdBy: 'anonymous',
      updatedAt: '2026-09-28T10:15:30Z',
      updatedBy: 'anonymous',
    },
  };
}

const ALICE = makePerson('person-1', 'Alice');
const BOB = makePerson('person-2', 'Bob');
const TASK_A = makeTask('task-1');
const TASK_B = makeTask('task-2');

function inputEl(fixture: { nativeElement: HTMLElement }, name: string): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(`input[data-field="${name}"]`);
  if (!el) {
    throw new Error(`no input found for data-field="${name}"`);
  }
  return el;
}

function typeInto(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function chipEl(fixture: { nativeElement: HTMLElement }, id: string): HTMLElement | null {
  return fixture.nativeElement.querySelector<HTMLElement>(`mat-chip-row[data-id="${id}"]`);
}

describe('SqRelationshipPicker', () => {
  let searchFn: ReturnType<typeof vi.fn<(targetTypeIri: string, term: string) => Observable<readonly BusinessObjectResponse[]>>>;
  let targetTypeFn: ReturnType<typeof vi.fn<(targetTypeIri: string) => Observable<TypeDetailResponse>>>;
  let lookup: RelationshipLookup;

  function createFixture(property: RelationshipPropertyResponse, control: FormControl<unknown>) {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations(), { provide: RELATIONSHIP_LOOKUP, useValue: lookup }],
    });
    const fixture = TestBed.createComponent(SqRelationshipPicker);
    fixture.componentRef.setInput('property', property);
    fixture.componentRef.setInput('control', control);
    return fixture;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    targetTypeFn = vi.fn<(targetTypeIri: string) => Observable<TypeDetailResponse>>((iri: string) =>
      of(iri === OWNER.targetTypeIri ? PERSON_TYPE_DETAIL : TASK_TYPE_DETAIL),
    );
    searchFn = vi.fn<(targetTypeIri: string, term: string) => Observable<readonly BusinessObjectResponse[]>>((iri: string) =>
      of(iri === OWNER.targetTypeIri ? [ALICE, BOB] : [TASK_A, TASK_B]),
    );
    lookup = { targetType: targetTypeFn, search: searchFn };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads the target type once on init to resolve labels', async () => {
    const fixture = createFixture(OWNER, new FormControl<unknown>(null));
    await fixture.whenStable();

    expect(targetTypeFn).toHaveBeenCalledTimes(1);
    expect(targetTypeFn).toHaveBeenCalledWith(OWNER.targetTypeIri);
  });

  describe('search debouncing', () => {
    it('does not call search before 300ms have elapsed', async () => {
      const fixture = createFixture(OWNER, new FormControl<unknown>(null));
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'owner'), 'ap');
      vi.advanceTimersByTime(200);

      expect(searchFn).not.toHaveBeenCalled();
    });

    it('calls search exactly once with the settled term after rapid typing within the debounce window', async () => {
      const fixture = createFixture(OWNER, new FormControl<unknown>(null));
      await fixture.whenStable();

      const input = inputEl(fixture, 'owner');
      typeInto(input, 'ap');
      vi.advanceTimersByTime(200);
      typeInto(input, 'apo');
      vi.advanceTimersByTime(200);
      expect(searchFn).not.toHaveBeenCalled();

      vi.advanceTimersByTime(100);

      expect(searchFn).toHaveBeenCalledTimes(1);
      expect(searchFn).toHaveBeenCalledWith(OWNER.targetTypeIri, 'apo');
    });

    it('renders autocomplete options labelled via pickLabel once search resolves', async () => {
      const fixture = createFixture(OWNER, new FormControl<unknown>(null));
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'owner'), 'a');
      vi.advanceTimersByTime(300);
      await fixture.whenStable();

      const host: HTMLElement = fixture.nativeElement;
      const optionLabels = Array.from(
        host.querySelectorAll<HTMLElement>('mat-option'),
      ).map((el) => el.textContent?.trim());
      expect(optionLabels).toContain('Alice');
      expect(optionLabels).toContain('Bob');
    });
  });

  describe('single-valued relationship (owner)', () => {
    it('select() sets the control value to the bare target id string, not the object', async () => {
      const control = new FormControl<unknown>(null);
      const fixture = createFixture(OWNER, control);
      await fixture.whenStable();

      fixture.componentInstance.select(ALICE);
      await fixture.whenStable();

      expect(control.value).toBe('person-1');
      expect(typeof control.value).toBe('string');
    });

    it('select() overwrites a previous single-valued selection with the new id', async () => {
      const control = new FormControl<unknown>(null);
      const fixture = createFixture(OWNER, control);
      await fixture.whenStable();

      fixture.componentInstance.select(ALICE);
      await fixture.whenStable();
      fixture.componentInstance.select(BOB);
      await fixture.whenStable();

      expect(control.value).toBe('person-2');
    });
  });

  describe('repeatable relationship (hasTask)', () => {
    it('select() appends the id to the control array value', async () => {
      const control = new FormControl<unknown>([]);
      const fixture = createFixture(HAS_TASK, control);
      await fixture.whenStable();

      fixture.componentInstance.select(TASK_A);
      await fixture.whenStable();
      fixture.componentInstance.select(TASK_B);
      await fixture.whenStable();

      expect(control.value).toEqual(['task-1', 'task-2']);
    });

    it('select() does not duplicate an id already present in the array', async () => {
      const control = new FormControl<unknown>(['task-1']);
      const fixture = createFixture(HAS_TASK, control);
      await fixture.whenStable();

      fixture.componentInstance.select(TASK_A);
      await fixture.whenStable();

      expect(control.value).toEqual(['task-1']);
    });

    it('renders a mat-chip-row per selected id, labelled with the raw id since Task has no STRING property', async () => {
      const control = new FormControl<unknown>(['task-1', 'task-2']);
      const fixture = createFixture(HAS_TASK, control);
      await fixture.whenStable();

      expect(chipEl(fixture, 'task-1')?.textContent).toContain('task-1');
      expect(chipEl(fixture, 'task-2')?.textContent).toContain('task-2');
    });

    it('remove(id) removes just that id from the control array value', async () => {
      const control = new FormControl<unknown>(['task-1', 'task-2']);
      const fixture = createFixture(HAS_TASK, control);
      await fixture.whenStable();

      fixture.componentInstance.remove('task-1');
      await fixture.whenStable();

      expect(control.value).toEqual(['task-2']);
    });

    it('remove(id) triggers no further lookup calls and leaves the other chip label intact', async () => {
      const control = new FormControl<unknown>(['task-1', 'task-2']);
      const fixture = createFixture(HAS_TASK, control);
      await fixture.whenStable();
      searchFn.mockClear();

      fixture.componentInstance.remove('task-1');
      await fixture.whenStable();

      expect(searchFn).not.toHaveBeenCalled();
      expect(chipEl(fixture, 'task-1')).toBeNull();
      expect(chipEl(fixture, 'task-2')?.textContent).toContain('task-2');
    });
  });
});
