/**
 * Contract under test (lib/detail/object-form.ts, [OBJ-FORM]): pure helpers plus a host
 * component built on top of `controlFor`/`toWireValue` from `@sequeless/ui-kit`.
 *
 * `editableProperties(type)` keeps only properties that are visible (`!hidden`), not `readOnly`,
 * and not derived (`derivation === null`) — on `PROJECT_TYPE_DETAIL` that is `title`, `hasTask`,
 * `status`, `owner`, `description`, in their original order; `createdAt` is dropped for being
 * hidden, `openTaskCount`/`totalEstimatedHours` for being readOnly-and-derived.
 *
 * `groupProperties(type)` only drops `hidden` properties (unlike `editableProperties`, readOnly
 * and derived properties stay) and buckets the rest by `group`: the `null`-group bucket comes
 * first, followed by one bucket per named group, each in the order its first member appears
 * among the type's (already displayOrder-sorted) properties; properties within a bucket keep
 * that same relative order.
 *
 * `buildObjectForm(type, initial?)` builds a `FormGroup` with one control per visible property
 * (via `controlFor`), seeded from `initial` when given; non-editable (readOnly) controls come
 * back disabled. `formToProperties(type, form)` is the inverse projection restricted to
 * `editableProperties`, running each control's raw value through `toWireValue`.
 *
 * `SqObjectForm` (selector `sq-object-form`) takes `type`/`form` as required signal inputs and
 * renders the ungrouped properties as a flat list of `sq-dynamic-field`, then one
 * `mat-expansion-panel` per named group, each containing that group's `sq-dynamic-field`s.
 */
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { TypeDetailResponse } from '@sequeless/core-api';
import { provideUiKit } from '@sequeless/ui-kit';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';

import {
  buildObjectForm,
  editableProperties,
  formToProperties,
  groupProperties,
  SqObjectForm,
} from './object-form';

const TYPE: TypeDetailResponse = PROJECT_TYPE_DETAIL;

describe('editableProperties', () => {
  it('returns visible, non-readOnly, non-derived properties in their original order', () => {
    const names = editableProperties(TYPE).map((p) => p.name);
    expect(names).toEqual(['title', 'hasTask', 'status', 'owner', 'description']);
  });

  it('excludes hidden properties', () => {
    expect(editableProperties(TYPE).some((p) => p.name === 'createdAt')).toBe(false);
  });

  it('excludes readOnly derived properties', () => {
    const names = editableProperties(TYPE).map((p) => p.name);
    expect(names).not.toContain('openTaskCount');
    expect(names).not.toContain('totalEstimatedHours');
  });
});

describe('groupProperties', () => {
  it('puts the ungrouped bucket first', () => {
    const groups = groupProperties(TYPE);
    expect(groups[0]!.group).toBeNull();
  });

  it('lists ungrouped properties, including readOnly/derived ones, before any named group', () => {
    const groups = groupProperties(TYPE);
    const ungrouped = groups[0]!.properties.map((p) => p.name);
    expect(ungrouped).toEqual(['hasTask', 'status', 'openTaskCount', 'totalEstimatedHours', 'owner']);
  });

  it('buckets named groups after the ungrouped bucket, preserving property order within', () => {
    const groups = groupProperties(TYPE);
    const general = groups.find((g) => g.group === 'General');
    expect(general?.properties.map((p) => p.name)).toEqual(['title', 'description']);
  });

  it('excludes hidden properties from every bucket', () => {
    const groups = groupProperties(TYPE);
    const allNames = groups.flatMap((g) => g.properties.map((p) => p.name));
    expect(allNames).not.toContain('createdAt');
  });
});

describe('buildObjectForm', () => {
  it('has one control per visible property', () => {
    const form = buildObjectForm(TYPE);
    const visibleNames = TYPE.properties.filter((p) => !p.hidden).map((p) => p.name);
    expect(Object.keys(form.controls).sort()).toEqual([...visibleNames].sort());
  });

  it('omits a control for hidden properties', () => {
    const form = buildObjectForm(TYPE);
    expect(form.contains('createdAt')).toBe(false);
  });

  it('disables non-editable (readOnly) controls', () => {
    const form = buildObjectForm(TYPE);
    expect(form.get('openTaskCount')!.disabled).toBe(true);
    expect(form.get('totalEstimatedHours')!.disabled).toBe(true);
  });

  it('leaves editable controls enabled', () => {
    const form = buildObjectForm(TYPE);
    expect(form.get('title')!.enabled).toBe(true);
    expect(form.get('status')!.enabled).toBe(true);
  });

  it('seeds controls from the initial values when given', () => {
    const form = buildObjectForm(TYPE, { title: 'Apollo', status: 'active' });
    expect(form.get('title')!.value).toBe('Apollo');
    expect(form.get('status')!.value).toBe('active');
  });

  it('seeds an unspecified property to its controlFor default', () => {
    const form = buildObjectForm(TYPE, { title: 'Apollo' });
    expect(form.get('owner')!.value).toBeNull();
  });
});

describe('formToProperties', () => {
  it('returns only editable properties, each run through toWireValue', () => {
    const form = buildObjectForm(TYPE, { title: 'Apollo', status: 'active', owner: 'person-1' });
    const result = formToProperties(TYPE, form);
    expect(result).toEqual({
      title: 'Apollo',
      hasTask: [],
      status: 'active',
      owner: 'person-1',
      description: null,
    });
  });

  it('excludes readOnly/derived properties even though their controls exist on the form', () => {
    const form = buildObjectForm(TYPE);
    const result = formToProperties(TYPE, form);
    expect(result).not.toHaveProperty('openTaskCount');
    expect(result).not.toHaveProperty('totalEstimatedHours');
  });

  it('excludes hidden properties', () => {
    const form = buildObjectForm(TYPE);
    const result = formToProperties(TYPE, form);
    expect(result).not.toHaveProperty('createdAt');
  });

  it('coerces an empty-string control value to null via toWireValue', () => {
    const form = buildObjectForm(TYPE, { title: '' });
    const result = formToProperties(TYPE, form);
    expect(result['title']).toBeNull();
  });
});

describe('SqObjectForm', () => {
  function createFixture(type: TypeDetailResponse, form: FormGroup) {
    TestBed.configureTestingModule({ providers: [provideUiKit(), provideNoopAnimations()] });
    const fixture = TestBed.createComponent(SqObjectForm);
    fixture.componentRef.setInput('type', type);
    fixture.componentRef.setInput('form', form);
    return fixture;
  }

  it('renders one sq-dynamic-field per visible property', async () => {
    const form = buildObjectForm(TYPE);
    const fixture = createFixture(TYPE, form);
    await fixture.whenStable();

    const fields = fixture.nativeElement.querySelectorAll('sq-dynamic-field');
    const visibleCount = TYPE.properties.filter((p) => !p.hidden).length;
    expect(fields.length).toBe(visibleCount);
  });

  it('renders one mat-expansion-panel per named group', async () => {
    const form = buildObjectForm(TYPE);
    const fixture = createFixture(TYPE, form);
    await fixture.whenStable();

    const panels = fixture.nativeElement.querySelectorAll('mat-expansion-panel');
    const namedGroups = new Set(
      TYPE.properties.filter((p) => !p.hidden && p.group !== null).map((p) => p.group),
    );
    expect(panels.length).toBe(namedGroups.size);
  });

  it('splits fields between the ungrouped list and the group panels, matching the groupProperties split', async () => {
    const form = buildObjectForm(TYPE);
    const fixture = createFixture(TYPE, form);
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    const panels: HTMLElement[] = Array.from(host.querySelectorAll('mat-expansion-panel'));
    const fieldsInPanels = panels.reduce(
      (total, panel) => total + panel.querySelectorAll('sq-dynamic-field').length,
      0,
    );
    const totalFields = host.querySelectorAll('sq-dynamic-field').length;
    const groups = groupProperties(TYPE);
    const ungroupedCount = groups.find((g) => g.group === null)?.properties.length ?? 0;

    expect(totalFields - fieldsInPanels).toBe(ungroupedCount);
  });
});
