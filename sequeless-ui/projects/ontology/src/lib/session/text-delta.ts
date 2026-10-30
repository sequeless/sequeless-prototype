import { parseOntology } from '@sequeless/rdf';
import type { QuadDelta, RdfStore } from '@sequeless/rdf';

/**
 * Diffs `store` against the quads parsed from `turtle`: the returned delta, applied to `store`, leaves
 * it holding exactly the parsed quads. Throws if `turtle` is not valid Turtle. Blank nodes are compared
 * by label, so a re-parse that relabels them replaces the quads that touch them; the outcome is still
 * the parsed content. n3 mints blank-node labels from a counter shared across parses, so text containing
 * blank nodes never yields an empty delta; callers should skip unchanged text themselves.
 */
export function computeTextDelta(store: RdfStore, turtle: string): QuadDelta {
  const parsed = parseOntology(turtle).store;
  return {
    removed: store.quads().filter((q) => !parsed.has(q)),
    added: parsed.quads().filter((q) => !store.has(q)),
  };
}
