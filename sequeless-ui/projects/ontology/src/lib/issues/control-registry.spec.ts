/*
 * Contract [ONT-REGISTRY]: ControlRegistry maps subject IRIs to form controls. routeIssues sets
 * `{server: message}` on controls registered for an issue's subjectIri and returns the first matched
 * control plus the issues with no match (null/empty/unregistered subjectIri). issuesToDiagnostics turns
 * issues into editor diagnostics located by the first occurrence of the subject IRI in the text.
 */
import { FormControl } from '@angular/forms';
import type { OntologyIssue } from '@sequeless/core-api';

import { ControlRegistry, issuesToDiagnostics } from './control-registry';

const A = 'http://ex.org/A';
const B = 'http://ex.org/B';

function issue(subjectIri: string | null, message = 'm', severity = 'error'): OntologyIssue {
  return { severity, subjectIri, message };
}

describe('ControlRegistry', () => {
  it('sets a server error on the control registered for the subject', () => {
    const registry = new ControlRegistry();
    const control = new FormControl('x');
    registry.register(A, control);
    const result = registry.routeIssues([issue(A, 'bad label')]);
    expect(control.errors).toEqual({ server: 'bad label' });
    expect(result.matchedFirst).toBe(control);
    expect(result.unmatched).toEqual([]);
  });

  it('returns issues with null, empty or unregistered subjects as unmatched', () => {
    const registry = new ControlRegistry();
    registry.register(A, new FormControl(''));
    const nul = issue(null);
    const empty = issue('');
    const other = issue(B);
    const result = registry.routeIssues([nul, empty, other]);
    expect(result.matchedFirst).toBeNull();
    expect(result.unmatched).toEqual([nul, empty, other]);
  });

  it('reports the first matched control in issue order', () => {
    const registry = new ControlRegistry();
    const a = new FormControl('');
    const b = new FormControl('');
    registry.register(A, a);
    registry.register(B, b);
    expect(registry.routeIssues([issue(B), issue(A)]).matchedFirst).toBe(b);
  });

  it('joins several issues for one subject and marks every control registered for it', () => {
    const registry = new ControlRegistry();
    const one = new FormControl('');
    const two = new FormControl('');
    registry.register(A, one);
    registry.register(A, two);
    registry.routeIssues([issue(A, 'first'), issue(A, 'second')]);
    expect(one.errors).toEqual({ server: 'first; second' });
    expect(two.errors).toEqual({ server: 'first; second' });
  });

  it('reveals a control by scrolling its registered element into view', () => {
    const registry = new ControlRegistry();
    const control = new FormControl('');
    const element = document.createElement('div');
    element.scrollIntoView = vi.fn();
    registry.register(A, control, element);
    registry.reveal(control);
    expect(element.scrollIntoView).toHaveBeenCalledWith({ block: 'center', behavior: 'smooth' });
  });

  it('reveal() ignores controls registered without an element', () => {
    const registry = new ControlRegistry();
    const control = new FormControl('');
    registry.register(A, control);
    expect(() => registry.reveal(control)).not.toThrow();
  });

  it('forgets controls after clear()', () => {
    const registry = new ControlRegistry();
    const control = new FormControl('');
    registry.register(A, control);
    registry.clear();
    const i = issue(A);
    expect(registry.routeIssues([i])).toEqual({ matchedFirst: null, unmatched: [i] });
    expect(control.errors).toBeNull();
  });
});

describe('issuesToDiagnostics', () => {
  const text = `<${A}> a <${B}> .\n<${B}> a <${A}> .`;

  it('locates the first occurrence of the subject IRI', () => {
    const [d] = issuesToDiagnostics(text, [issue(B, 'oops')]);
    expect(d).toEqual({ from: text.indexOf(B), to: text.indexOf(B) + B.length, message: 'oops', severity: 'error' });
  });

  it('uses {0,0} when the subject is absent, null or empty', () => {
    const diagnostics = issuesToDiagnostics(text, [issue('http://nope'), issue(null), issue('')]);
    expect(diagnostics.map((d) => [d.from, d.to])).toEqual([[0, 0], [0, 0], [0, 0]]);
  });

  it('maps severities, defaulting unknown ones to error', () => {
    const diagnostics = issuesToDiagnostics(text, [
      issue(A, 'a', 'warning'),
      issue(A, 'b', 'INFO'),
      issue(A, 'c', 'weird'),
    ]);
    expect(diagnostics.map((d) => d.severity)).toEqual(['warning', 'info', 'error']);
  });
});
