/**
 * Contract under test (lib/problem-banner/problem-banner.ts): `SqProblemBanner` (selector
 * `sq-problem-banner`) takes `problems = input.required<ApiError | readonly OntologyIssue[]>()`,
 * runs it through `normalizeProblems` (./ ../util/problems — see that file's own spec for the
 * normalization rules) and renders the result uniformly per the phase-5 spec.
 *
 * When normalization yields `[]`, the component renders nothing — no `role="alert"` element at
 * all. Otherwise it renders a single `<div role="alert" class="sq-problem-banner">` containing one
 * `<li>` per normalized problem, in order, each tagged `data-severity="error" | "warning" |
 * "info"` matching that problem's `severity` and containing its `message` text. When a problem's
 * `subject` is non-null, the `<li>` additionally contains a `<code>` element with the subject text;
 * when `subject` is null, no `<code>` is rendered for that item.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { ApiError, OntologyIssue } from '@sequeless/core-api';
import { SqProblemBanner } from './problem-banner';

function makeIssue(severity: string, subjectIri: string | null, message: string): OntologyIssue {
  return { severity, subjectIri, message };
}

function makeApiError(overrides: Partial<ApiError> = {}): ApiError {
  return {
    status: 422,
    kind: 'problem',
    title: 'Request failed',
    detail: null,
    slug: 'validation-error',
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: null,
    ...overrides,
  };
}

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqProblemBanner],
  template: `<sq-problem-banner [problems]="problems" />`,
})
class TestHost {
  problems: ApiError | readonly OntologyIssue[] = [];
}

function alertEl(fixture: { nativeElement: HTMLElement }): HTMLElement | null {
  return fixture.nativeElement.querySelector('[role="alert"]');
}

function items(fixture: { nativeElement: HTMLElement }): HTMLLIElement[] {
  return Array.from(fixture.nativeElement.querySelectorAll('li'));
}

describe('SqProblemBanner', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('renders nothing for an empty array of problems', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [];
    await fixture.whenStable();

    expect(alertEl(fixture)).toBeNull();
    expect(items(fixture)).toHaveLength(0);
  });

  it('renders an ApiError with only a detail as a single error item', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = makeApiError({ detail: 'Validation failed' });
    await fixture.whenStable();

    const alert = alertEl(fixture);
    expect(alert).not.toBeNull();
    expect(alert?.tagName).toBe('DIV');
    expect(alert?.classList.contains('sq-problem-banner')).toBe(true);

    const rows = items(fixture);
    expect(rows).toHaveLength(1);
    expect(rows[0].getAttribute('data-severity')).toBe('error');
    expect(rows[0].textContent).toContain('Validation failed');
  });

  it('renders an array of OntologyIssue with ERROR and WARNING as two items in order', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [
      makeIssue('ERROR', 'https://example.test/x', 'inconsistent axiom'),
      makeIssue('WARNING', 'https://example.test/y', 'deprecated property'),
    ];
    await fixture.whenStable();

    const rows = items(fixture);
    expect(rows).toHaveLength(2);
    expect(rows.map((li) => li.getAttribute('data-severity'))).toEqual(['error', 'warning']);
    expect(rows[0].textContent).toContain('inconsistent axiom');
    expect(rows[1].textContent).toContain('deprecated property');
  });

  it('renders the subject in a <code> element when present', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [
      makeIssue('ERROR', 'https://example.test/x', 'inconsistent axiom'),
    ];
    await fixture.whenStable();

    const code = items(fixture)[0].querySelector('code');
    expect(code).not.toBeNull();
    expect(code?.textContent).toBe('https://example.test/x');
  });

  it('renders no <code> element when the subject is null', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [makeIssue('ERROR', null, 'inconsistent axiom')];
    await fixture.whenStable();

    expect(items(fixture)[0].querySelector('code')).toBeNull();
  });

  it('maps a violation subject (property name) into the <code> element', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = makeApiError({
      violations: [{ property: 'status', propertyIri: 'status-iri', message: 'invalid value' }],
    });
    await fixture.whenStable();

    const rows = items(fixture);
    expect(rows).toHaveLength(1);
    expect(rows[0].getAttribute('data-severity')).toBe('error');
    expect(rows[0].querySelector('code')?.textContent).toBe('status');
    expect(rows[0].textContent).toContain('invalid value');
  });

  it('re-renders when the problems input is updated from empty to non-empty', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [];
    await fixture.whenStable();
    expect(alertEl(fixture)).toBeNull();

    fixture.componentInstance.problems = [makeIssue('info', null, 'fyi')];
    await fixture.whenStable();

    expect(alertEl(fixture)).not.toBeNull();
    expect(items(fixture)).toHaveLength(1);
    expect(items(fixture)[0].getAttribute('data-severity')).toBe('info');
  });

  it('re-renders when the problems input is updated from non-empty to empty', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [makeIssue('error', null, 'one')];
    await fixture.whenStable();
    expect(alertEl(fixture)).not.toBeNull();

    fixture.componentInstance.problems = [];
    await fixture.whenStable();

    expect(alertEl(fixture)).toBeNull();
  });

  it('renders every item as a child of the single alert container', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.problems = [
      makeIssue('ERROR', null, 'one'),
      makeIssue('WARNING', null, 'two'),
      makeIssue('other', null, 'three'),
    ];
    await fixture.whenStable();

    const alert = alertEl(fixture);
    expect(alert?.querySelectorAll('li')).toHaveLength(3);
    expect(
      Array.from(alert?.querySelectorAll('li') ?? []).map((li) => li.getAttribute('data-severity')),
    ).toEqual(['error', 'warning', 'info']);
  });
});
