import { Store as N3Store } from 'n3';
import type { Term as N3Term } from 'n3';
import type { Quad, Term } from '@rdfjs/types';
import type { QuadDelta } from './model';

/**
 * A minimal RDF quad-store, shaped after the RDF/JS `DatasetCore`/`Store` vocabulary but trimmed to
 * what the ontology tooling needs, and restricted to the **default graph** only. Implementations must
 * treat `undefined` and `null` identically as a wildcard in any `match` position, and `apply(delta)`
 * must remove every quad in `removed` before adding every quad in `added` (so a quad in both sets ends
 * up present).
 */
export interface RdfStore {
  readonly size: number;
  match(s?: Term | null, p?: Term | null, o?: Term | null): Quad[];
  has(q: Quad): boolean;
  add(q: Quad): void;
  delete(q: Quad): void;
  quads(): Quad[];
  /** Removes every quad in `delta.removed`, then adds every quad in `delta.added`. */
  apply(delta: QuadDelta): void;
  /** Returns a deep, independent copy holding the same quads; later mutation never leaks between them. */
  clone(): RdfStore;
}

/**
 * {@link RdfStore} backed by `n3`'s `Store`, operating on the default graph only. n3's `Store` accepts
 * an array or a `Dataset` in its constructor, so an arbitrary {@link Iterable} of quads is materialised
 * to an array here before being passed through.
 */
export class N3RdfStore implements RdfStore {
  private readonly _store: N3Store;

  constructor(quads?: Iterable<Quad>) {
    this._store = new N3Store(quads === undefined ? undefined : Array.from(quads));
  }

  get size(): number {
    return this._store.size;
  }

  match(s?: Term | null, p?: Term | null, o?: Term | null): Quad[] {
    return (
      this._store
        .match(s as N3Term | null, p as N3Term | null, o as N3Term | null)
        .toArray() as Quad[]
    );
  }

  has(q: Quad): boolean {
    return this._store.has(q);
  }

  add(q: Quad): void {
    this._store.add(q);
  }

  delete(q: Quad): void {
    this._store.delete(q);
  }

  quads(): Quad[] {
    return this._store.toArray();
  }

  apply(delta: QuadDelta): void {
    for (const q of delta.removed) {
      this.delete(q);
    }
    for (const q of delta.added) {
      this.add(q);
    }
  }

  clone(): RdfStore {
    return new N3RdfStore(this.quads());
  }
}
