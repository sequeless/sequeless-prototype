import { inject, InjectionToken } from '@angular/core';
import type { Quad, Term } from '@rdfjs/types';
import { SEQUELESS_FEATURES } from '@sequeless/core-api';
import { N3RdfStore, serializeOntology } from '@sequeless/rdf';
import type { OntologyDocument, QuadDelta, RdfStore } from '@sequeless/rdf';

/** The quads added to and removed from one subject, as N-Triples-ish lines. */
export interface SubjectChange {
  subject: string;
  added: readonly string[];
  removed: readonly string[];
}

/** A plain view-model for the diff preview; independent of how it was computed. */
export interface OntologyDiff {
  subjects: readonly SubjectChange[];
  baseText: string;
  workingText: string;
}

export interface OntologyDiffSource {
  diff(base: OntologyDocument, working: RdfStore): Promise<OntologyDiff>;
}

function termText(term: Term): string {
  switch (term.termType) {
    case 'NamedNode':
      return `<${term.value}>`;
    case 'BlankNode':
      return `_:${term.value}`;
    case 'Literal': {
      const value = JSON.stringify(term.value);
      if (term.language) {
        return `${value}@${term.language}`;
      }
      return term.datatype.value === 'http://www.w3.org/2001/XMLSchema#string'
        ? value
        : `${value}^^<${term.datatype.value}>`;
    }
    default:
      return term.value;
  }
}

function subjectKey(q: Quad): string {
  return q.subject.termType === 'BlankNode' ? `_:${q.subject.value}` : q.subject.value;
}

function line(q: Quad): string {
  return `${termText(q.subject)} ${termText(q.predicate)} ${termText(q.object)} .`;
}

export function groupBySubject(delta: QuadDelta): SubjectChange[] {
  const bySubject = new Map<string, { added: string[]; removed: string[] }>();
  const entry = (q: Quad) => {
    const key = subjectKey(q);
    let value = bySubject.get(key);
    if (!value) {
      value = { added: [], removed: [] };
      bySubject.set(key, value);
    }
    return value;
  };
  delta.added.forEach((q) => entry(q).added.push(line(q)));
  delta.removed.forEach((q) => entry(q).removed.push(line(q)));
  return [...bySubject.entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([subject, change]) => ({ subject, ...change }));
}

/** Diffs the document's original quads against the working store, entirely in the browser. */
export function clientSideDiff(): OntologyDiffSource {
  return {
    async diff(base, working) {
      const original = new N3RdfStore(base.baseQuads);
      const delta: QuadDelta = {
        removed: original.quads().filter((q) => !working.has(q)),
        added: working.quads().filter((q) => !original.has(q)),
      };
      const [baseText, workingText] = await Promise.all([
        serializeOntology({ ...base, store: original }),
        serializeOntology({ ...base, store: working }),
      ]);
      return { subjects: groupBySubject(delta), baseText, workingText };
    },
  };
}

function serverSideDiff(): OntologyDiffSource {
  return {
    diff: () => Promise.reject(new Error('Server-side ontology diff is not implemented')),
  };
}

export const ONTOLOGY_DIFF_SOURCE = new InjectionToken<OntologyDiffSource>('Ontology diff source', {
  providedIn: 'root',
  factory: () =>
    inject(SEQUELESS_FEATURES, { optional: true })?.serverSideOntologyDiff
      ? serverSideDiff()
      : clientSideDiff(),
});
