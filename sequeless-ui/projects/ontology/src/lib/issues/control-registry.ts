import type { AbstractControl } from '@angular/forms';
import type { EditorDiagnostic, OntologyIssue } from '@sequeless/core-api';

/** Maps the subject IRI a form control edits to that control, so a 422 report can be routed back onto it. */
export class ControlRegistry {
  private readonly controls = new Map<string, AbstractControl[]>();
  private readonly elements = new Map<AbstractControl, HTMLElement>();

  /** `element`, when given, is what `reveal(control)` scrolls into view. */
  register(subjectIri: string, control: AbstractControl, element?: HTMLElement): void {
    if (element) {
      this.elements.set(control, element);
    }
    const existing = this.controls.get(subjectIri);
    if (existing) {
      existing.push(control);
    } else {
      this.controls.set(subjectIri, [control]);
    }
  }

  clear(): void {
    this.controls.clear();
    this.elements.clear();
  }

  /** Forget a control, e.g. when its form is destroyed. */
  unregister(control: AbstractControl): void {
    this.elements.delete(control);
    for (const [iri, list] of this.controls) {
      const rest = list.filter((c) => c !== control);
      if (rest.length === 0) {
        this.controls.delete(iri);
      } else {
        this.controls.set(iri, rest);
      }
    }
  }

  reveal(control: AbstractControl): void {
    this.elements.get(control)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  /**
   * Sets `{server: messages}` on every control registered for an issue's subject. Issues with a
   * null/empty/unregistered subject are returned as `unmatched`.
   */
  routeIssues(issues: readonly OntologyIssue[]): {
    matchedFirst: AbstractControl | null;
    unmatched: OntologyIssue[];
  } {
    const messages = new Map<string, string[]>();
    const unmatched: OntologyIssue[] = [];
    let matchedFirst: AbstractControl | null = null;
    for (const issue of issues) {
      const controls = issue.subjectIri ? this.controls.get(issue.subjectIri) : undefined;
      if (!issue.subjectIri || !controls) {
        unmatched.push(issue);
        continue;
      }
      matchedFirst ??= controls[0];
      messages.set(issue.subjectIri, [...(messages.get(issue.subjectIri) ?? []), issue.message]);
    }
    for (const [iri, list] of messages) {
      for (const control of this.controls.get(iri) ?? []) {
        control.setErrors({ server: list.join('; ') });
      }
    }
    return { matchedFirst, unmatched };
  }
}

function severityOf(value: string): EditorDiagnostic['severity'] {
  const lower = value.toLowerCase();
  return lower === 'warning' || lower === 'info' ? lower : 'error';
}

/** Turns issues into editor diagnostics located at the first occurrence of the subject IRI in `text`. */
export function issuesToDiagnostics(text: string, issues: readonly OntologyIssue[]): EditorDiagnostic[] {
  return issues.map((issue) => {
    const at = issue.subjectIri ? text.indexOf(issue.subjectIri) : -1;
    const from = at >= 0 ? at : 0;
    const to = at >= 0 && issue.subjectIri ? at + issue.subjectIri.length : 0;
    return { from, to, message: issue.message, severity: severityOf(issue.severity) };
  });
}
