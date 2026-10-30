import type { Quad, Term } from '@rdfjs/types';

/**
 * Quad-set equality for tests, per docs/specs/phase-4-rdf.md: assert quad-set equality, never
 * Turtle string equality, because round-tripping through `N3.Writer` relabels blank nodes.
 *
 * Ground quads (no blank node in any position) must match exactly as a set. Quads that touch a
 * blank node are compared as a multiset with every blank node's identity collapsed to a single
 * placeholder, since blank node labels are only stable within one parse and carry no meaning
 * across two stores. Default graph only.
 */

const XSD_STRING = 'http://www.w3.org/2001/XMLSchema#string';

function termKey(term: Term): string {
  switch (term.termType) {
    case 'NamedNode':
      return `N:${term.value}`;
    case 'BlankNode':
      return 'B:_';
    case 'Variable':
      return `V:${term.value}`;
    case 'DefaultGraph':
      return 'G:';
    case 'Literal':
      return `L:${term.value}\u0000${term.datatype.value}\u0000${term.language}`;
    case 'Quad':
      return `Q:${termKey(term.subject)}|${termKey(term.predicate)}|${termKey(term.object)}|${termKey(term.graph)}`;
  }
}

function quadKey(quad: Quad): string {
  return [
    termKey(quad.subject),
    termKey(quad.predicate),
    termKey(quad.object),
    termKey(quad.graph),
  ].join('\u0001');
}

function describeTerm(term: Term): string {
  switch (term.termType) {
    case 'NamedNode':
      return `<${term.value}>`;
    case 'BlankNode':
      return `_:${term.value}`;
    case 'Variable':
      return `?${term.value}`;
    case 'DefaultGraph':
      return '';
    case 'Literal':
      if (term.language) {
        return `"${term.value}"@${term.language}`;
      }
      if (term.datatype.value === XSD_STRING) {
        return `"${term.value}"`;
      }
      return `"${term.value}"^^<${term.datatype.value}>`;
    case 'Quad':
      return `<<${describeTerm(term.subject)} ${describeTerm(term.predicate)} ${describeTerm(term.object)}>>`;
  }
}

function describeQuad(quad: Quad): string {
  return `${describeTerm(quad.subject)} ${describeTerm(quad.predicate)} ${describeTerm(quad.object)}`;
}

interface CountedSample {
  count: number;
  sample: Quad;
}

function toMultiset(quads: Iterable<Quad>): Map<string, CountedSample> {
  const counts = new Map<string, CountedSample>();
  for (const quad of quads) {
    const key = quadKey(quad);
    const existing = counts.get(key);
    if (existing) {
      existing.count += 1;
    } else {
      counts.set(key, { count: 1, sample: quad });
    }
  }
  return counts;
}

/**
 * True if `a` and `b` contain the same quads as a multiset, with blank node identity ignored
 * (every blank node in every position is treated as interchangeable with every other).
 */
export function sameQuadSet(a: Iterable<Quad>, b: Iterable<Quad>): boolean {
  const countsA = toMultiset(a);
  const countsB = toMultiset(b);
  if (countsA.size !== countsB.size) {
    return false;
  }
  for (const [key, { count }] of countsA) {
    if (countsB.get(key)?.count !== count) {
      return false;
    }
  }
  return true;
}

/** Human-readable multiset diff between `a` and `b`, for use in test failure messages. */
export function quadSetDiff(
  a: Iterable<Quad>,
  b: Iterable<Quad>,
): { onlyInA: string[]; onlyInB: string[] } {
  const countsA = toMultiset(a);
  const countsB = toMultiset(b);

  const describeExtra = (counts: Map<string, CountedSample>, other: Map<string, CountedSample>): string[] => {
    const lines: string[] = [];
    for (const [key, { count, sample }] of counts) {
      const extra = count - (other.get(key)?.count ?? 0);
      if (extra <= 0) {
        continue;
      }
      lines.push(extra === 1 ? describeQuad(sample) : `${describeQuad(sample)} (×${extra})`);
    }
    return lines;
  };

  return {
    onlyInA: describeExtra(countsA, countsB),
    onlyInB: describeExtra(countsB, countsA),
  };
}
