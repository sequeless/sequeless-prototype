import { Parser, Writer } from 'n3';
import type { Quad } from '@rdfjs/types';
import type { RdfStore } from './store';
import { N3RdfStore } from './store';

/**
 * A parsed ontology document: the base quads (as fetched), a working `RdfStore` seeded from a copy
 * of those base quads, and the captured prefix map (from the n3 Parser), so that re-serialisation
 * re-uses the same abbreviations the document arrived with.
 */
export interface OntologyDocument {
  readonly baseQuads: readonly Quad[];
  readonly store: RdfStore;
  readonly prefixes: Readonly<Record<string, string>>;
}

/**
 * Synchronously parses a Turtle document into an {@link OntologyDocument}.
 *
 * n3's public prefix-capture surface is the parse callback's third argument, but it is delivered via
 * `queueMicrotask`, so it cannot be observed before `parseOntology` returns. We therefore take the no-
 * callback (synchronous) parse path — which throws synchronously on invalid Turtle, returning the quads
 * as an array — and read the parser's internal `_prefixes` map afterwards (the same object the callback
 * would have been handed on the EOF event). The working store is seeded from a copy of those base quads,
 * so mutating the store never reaches back into `baseQuads`.
 */
export function parseOntology(turtle: string): OntologyDocument {
  const parser = new Parser({ format: 'text/turtle', blankNodePrefix: '' });

  // Synchronous path: `parse(input)` (no callback) tokenises on the call stack and throws here
  // if the Turtle is syntactically invalid.
  const baseQuads: readonly Quad[] = parser.parse(turtle);

  // n3 keeps the captured prefix declarations on `parser._prefixes` as `{name: iriString}`; the
  // synthetic `_` entry is the blank-node prefix and is not a real declared prefix.
  const rawPrefixes = (parser as unknown as { _prefixes: Record<string, unknown> })._prefixes;
  const prefixes: Record<string, string> = {};
  for (const [name, value] of Object.entries(rawPrefixes)) {
    if (name === '_' || value === undefined) {
      continue;
    }
    prefixes[name] = String(value);
  }

  return {
    baseQuads,
    store: new N3RdfStore(baseQuads as Iterable<Quad>),
    prefixes: Object.freeze(prefixes),
  };
}

/**
 * Serialises an {@link OntologyDocument}'s working store back to Turtle, re-using the captured
 * prefix map. Quads are written in the working store's iteration order; the promise resolves with
 * the full document text (all of the working store, in the default graph).
 */
export function serializeOntology(doc: OntologyDocument): Promise<string> {
  const writer = new Writer({
    format: 'text/turtle',
    prefixes: doc.prefixes,
  });
  writer.addQuads(doc.store.quads());

  return new Promise<string>((resolve, reject) => {
    writer.end((err, result) => {
      if (err) {
        reject(err);
      } else {
        resolve(result as string);
      }
    });
  });
}
