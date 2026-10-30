/**
 * Contract under test (public-api.ts): the library's public surface re-exports every runtime
 * symbol from lib/dynamic-field/* (the host, one component per datatype, and the default
 * `DEFAULT_FIELD_RENDERERS` map), lib/relationship-picker/* , lib/duration-field/*,
 * lib/turtle-editor/* (the component, the default CodeMirror adapter, and `provideUiKit`'s
 * `TURTLE_EDITOR` factory target), lib/state-machine-graph/* (the component and the default
 * `DagreGraphLayout`), lib/problem-banner/*, lib/conflict-dialog/* and every lib/util/* helper —
 * and nothing else, so a consumer never has to reach past `./public-api` into `./lib/*`.
 *
 * Alongside that export-surface check: `provideUiKit()` must wire core-api's three ui-kit slots
 * (`FIELD_RENDERERS`, `TURTLE_EDITOR`, `GRAPH_LAYOUT`) to this library's defaults, and an
 * end-to-end smoke test exercises the round-trip this library exists for — build a control with
 * `controlFor`, render it through `SqDynamicField`, type into the rendered native input, and
 * confirm the control lands on exactly the value `toWireValue` would produce.
 */
import { TestBed } from '@angular/core/testing';
import { FormArray } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse, TypeDetailResponse } from '@sequeless/core-api';
import { FIELD_RENDERERS, GRAPH_LAYOUT, TURTLE_EDITOR } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import * as UiKitPublicApi from './public-api';

const PROJECT_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;

// Every runtime (non-type-only) symbol public-api.ts must re-export, grouped by source file.
const EXPECTED_EXPORTS: Record<string, 'function' | 'object'> = {
  // lib/dynamic-field/dynamic-field.ts
  SqDynamicField: 'function',
  // lib/dynamic-field/default-field-renderers.ts (and friends)
  DEFAULT_FIELD_RENDERERS: 'object',
  // lib/dynamic-field/fields/*.ts
  SqFieldString: 'function',
  SqFieldBoolean: 'function',
  SqFieldInteger: 'function',
  SqFieldDecimal: 'function',
  SqFieldDate: 'function',
  SqFieldTime: 'function',
  SqFieldDateTime: 'function',
  SqFieldDuration: 'function',
  SqFieldAnyUri: 'function',
  // lib/duration-field/duration-field.ts
  SqDurationField: 'function',
  // lib/relationship-picker/*.ts
  SqRelationshipPicker: 'function',
  RELATIONSHIP_LOOKUP: 'object',
  // lib/turtle-editor/*.ts
  SqTurtleEditor: 'function',
  CodeMirrorTextEditorAdapter: 'function',
  // lib/state-machine-graph/*.ts
  SqStateMachineGraph: 'function',
  DagreGraphLayout: 'function',
  // lib/problem-banner/problem-banner.ts
  SqProblemBanner: 'function',
  // lib/conflict-dialog/conflict-dialog.ts
  SqConflictDialog: 'function',
  openConflictDialog: 'function',
  // lib/util/wire-value.ts
  toWireValue: 'function',
  fromWireValue: 'function',
  // lib/util/validators.ts
  controlFor: 'function',
  isRepeatable: 'function',
  validatorsFor: 'function',
  // lib/util/labels.ts
  pickLabel: 'function',
  // lib/util/edge-style.ts
  edgeLabel: 'function',
  edgeStyle: 'function',
  pointsToPath: 'function',
  // lib/util/problems.ts
  normalizeProblems: 'function',
  // lib/util/three-way-diff.ts
  threeWayDiff: 'function',
  reapplyLocal: 'function',
  // lib/util/iso-duration.ts
  formatIsoDuration: 'function',
  parseIsoDuration: 'function',
  // provide-ui-kit.ts
  provideUiKit: 'function',
};

describe('public-api exports', () => {
  it('exposes exactly the documented runtime symbols and nothing invented', () => {
    expect(Object.keys(UiKitPublicApi).sort()).toEqual(Object.keys(EXPECTED_EXPORTS).sort());
  });

  it('exports each documented symbol as the expected kind of runtime value', () => {
    for (const [name, kind] of Object.entries(EXPECTED_EXPORTS)) {
      const value = (UiKitPublicApi as Record<string, unknown>)[name];
      expect(value).toBeDefined();
      expect(typeof value).toBe(kind);
    }
  });
});

describe('provideUiKit', () => {
  it('provides FIELD_RENDERERS as the DEFAULT_FIELD_RENDERERS map', () => {
    TestBed.configureTestingModule({ providers: [UiKitPublicApi.provideUiKit()] });

    expect(TestBed.inject(FIELD_RENDERERS)).toBe(UiKitPublicApi.DEFAULT_FIELD_RENDERERS);
  });

  it('provides TURTLE_EDITOR as a factory that constructs a CodeMirrorTextEditorAdapter', () => {
    TestBed.configureTestingModule({ providers: [UiKitPublicApi.provideUiKit()] });

    const factory = TestBed.inject(TURTLE_EDITOR);
    expect(typeof factory).toBe('function');
    const adapter = factory!();
    expect(adapter).toBeInstanceOf(UiKitPublicApi.CodeMirrorTextEditorAdapter);
  });

  it('provides GRAPH_LAYOUT as a DagreGraphLayout instance', () => {
    TestBed.configureTestingModule({ providers: [UiKitPublicApi.provideUiKit()] });

    expect(TestBed.inject(GRAPH_LAYOUT)).toBeInstanceOf(UiKitPublicApi.DagreGraphLayout);
  });
});

describe('end-to-end smoke test: controlFor -> SqDynamicField -> typed wire value', () => {
  it('types into the first visible STRING attribute and lands on exactly toWireValue\'s result', async () => {
    const property = PROJECT_DETAIL.properties.find(
      (p): p is AttributePropertyResponse =>
        p.kind === 'attribute' && !p.hidden && p.datatype === 'STRING',
    );
    if (!property) {
      throw new Error('fixture has no visible STRING attribute property');
    }

    const control = UiKitPublicApi.controlFor(property) as FormArray;

    TestBed.configureTestingModule({ providers: [provideNoopAnimations()] });
    const fixture = TestBed.createComponent(UiKitPublicApi.SqDynamicField);
    fixture.componentRef.setInput('property', property);
    fixture.componentRef.setInput('control', control);
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    host
      .querySelector<HTMLButtonElement>('button[data-action="add"]')
      ?.click();
    await fixture.whenStable();

    const input = host.querySelector<HTMLInputElement>(
      `[data-index="0"] input[data-field="${property.name}"]`,
    );
    if (!input) {
      throw new Error('no rendered input found for the first repeatable array item');
    }
    input.value = 'Apollo';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const itemControl = control.at(0);
    expect(itemControl.value).toBe(UiKitPublicApi.toWireValue('STRING', 'Apollo'));
    expect(itemControl.value).toBe('Apollo');
  });
});
