/**
 * Contract under test (lib/store.ts): `RdfStore` is an RDF/JS-shaped quad-store interface, backed
 * by `N3RdfStore` over the default graph only. `match` treats `undefined` and `null` identically as
 * a wildcard in any position; `apply(delta)` removes every quad in `delta.removed` before adding
 * `delta.added`, so a quad present in both ends up present afterwards; `clone()` returns an
 * independent copy whose later mutation (in either direction) never affects the other.
 */
import { DataFactory } from 'n3';
import type { Quad } from '@rdfjs/types';
import type { QuadDelta } from './model';
import { N3RdfStore } from './store';
import { sameQuadSet } from '../testing/quad-set';

const { namedNode, quad } = DataFactory;

const ex = (local: string) => namedNode(`https://example.test/${local}`);

function makeQuad(s: string, p: string, o: string): Quad {
  return quad(ex(s), ex(p), ex(o));
}

describe('N3RdfStore', () => {
  describe('constructor', () => {
    it('starts empty with size 0 when given no quads', () => {
      const store = new N3RdfStore();
      expect(store.size).toBe(0);
      expect(store.quads()).toEqual([]);
    });

    it('seeds its contents from an iterable of quads', () => {
      const seed = [makeQuad('a', 'p', 'b'), makeQuad('a', 'p', 'c')];
      const store = new N3RdfStore(seed);
      expect(store.size).toBe(2);
      expect(sameQuadSet(store.quads(), seed)).toBe(true);
    });
  });

  describe('add/has/delete', () => {
    it('reports a quad absent before add and present after it', () => {
      const store = new N3RdfStore();
      const q = makeQuad('a', 'p', 'b');
      expect(store.has(q)).toBe(false);
      store.add(q);
      expect(store.has(q)).toBe(true);
      expect(store.size).toBe(1);
    });

    it('removes a quad on delete, leaving unrelated quads intact', () => {
      const q1 = makeQuad('a', 'p', 'b');
      const q2 = makeQuad('a', 'p', 'c');
      const store = new N3RdfStore([q1, q2]);
      store.delete(q1);
      expect(store.has(q1)).toBe(false);
      expect(store.has(q2)).toBe(true);
      expect(store.size).toBe(1);
    });

    it('is a no-op deleting a quad that was never added', () => {
      const store = new N3RdfStore([makeQuad('a', 'p', 'b')]);
      store.delete(makeQuad('x', 'y', 'z'));
      expect(store.size).toBe(1);
    });
  });

  describe('match', () => {
    const q1 = makeQuad('a', 'p', 'b');
    const q2 = makeQuad('a', 'p', 'c');
    const q3 = makeQuad('a', 'q', 'b');
    const q4 = makeQuad('d', 'p', 'b');

    function buildStore(): N3RdfStore {
      return new N3RdfStore([q1, q2, q3, q4]);
    }

    it('matches quads by a fully specified subject, predicate and object', () => {
      const store = buildStore();
      expect(sameQuadSet(store.match(ex('a'), ex('p'), ex('b')), [q1])).toBe(true);
    });

    it('treats an omitted (undefined) object position as a wildcard', () => {
      const store = buildStore();
      expect(sameQuadSet(store.match(ex('a'), ex('p'), undefined), [q1, q2])).toBe(true);
    });

    it('treats a null object position as a wildcard, identically to undefined', () => {
      const store = buildStore();
      expect(sameQuadSet(store.match(ex('a'), ex('p'), null), [q1, q2])).toBe(true);
    });

    it('matches every quad in the store when called with no arguments', () => {
      const store = buildStore();
      expect(sameQuadSet(store.match(), [q1, q2, q3, q4])).toBe(true);
    });

    it('returns an empty array when no quad matches the given pattern', () => {
      const store = buildStore();
      expect(store.match(ex('nonexistent'), null, null)).toEqual([]);
    });
  });

  describe('quads', () => {
    it('returns every quad currently held by the store', () => {
      const seed = [makeQuad('a', 'p', 'b'), makeQuad('c', 'q', 'd')];
      const store = new N3RdfStore(seed);
      expect(sameQuadSet(store.quads(), seed)).toBe(true);
    });
  });

  describe('apply', () => {
    it('adds every quad listed in delta.added', () => {
      const store = new N3RdfStore();
      const q = makeQuad('a', 'p', 'b');
      store.apply({ added: [q], removed: [] });
      expect(store.has(q)).toBe(true);
    });

    it('removes every quad listed in delta.removed', () => {
      const q = makeQuad('a', 'p', 'b');
      const store = new N3RdfStore([q]);
      store.apply({ added: [], removed: [q] });
      expect(store.has(q)).toBe(false);
      expect(store.size).toBe(0);
    });

    it('removes before adding, so a quad listed in both removed and added ends up present', () => {
      const q = makeQuad('a', 'p', 'b');
      const store = new N3RdfStore([q]);
      const delta: QuadDelta = { added: [q], removed: [q] };
      store.apply(delta);
      expect(store.has(q)).toBe(true);
      expect(store.size).toBe(1);
    });

    it('applies a mixed delta in one call, dropping the old quad and keeping the untouched one', () => {
      const oldQuad = makeQuad('a', 'p', 'b');
      const newQuad = makeQuad('a', 'p', 'c');
      const untouched = makeQuad('x', 'y', 'z');
      const store = new N3RdfStore([oldQuad, untouched]);
      store.apply({ added: [newQuad], removed: [oldQuad] });
      expect(sameQuadSet(store.quads(), [newQuad, untouched])).toBe(true);
    });
  });

  describe('clone', () => {
    it('produces a copy holding the same quads as the original', () => {
      const seed = [makeQuad('a', 'p', 'b'), makeQuad('c', 'q', 'd')];
      const store = new N3RdfStore(seed);
      const clone = store.clone();
      expect(sameQuadSet(clone.quads(), store.quads())).toBe(true);
    });

    it('is unaffected when the original store is mutated after cloning', () => {
      const q = makeQuad('a', 'p', 'b');
      const store = new N3RdfStore([q]);
      const clone = store.clone();
      store.add(makeQuad('c', 'd', 'e'));
      store.delete(q);
      expect(clone.has(q)).toBe(true);
      expect(sameQuadSet(clone.quads(), [q])).toBe(true);
    });

    it('leaves the original store unaffected when the clone is mutated', () => {
      const q = makeQuad('a', 'p', 'b');
      const store = new N3RdfStore([q]);
      const clone = store.clone();
      clone.add(makeQuad('c', 'd', 'e'));
      clone.delete(q);
      expect(store.has(q)).toBe(true);
      expect(sameQuadSet(store.quads(), [q])).toBe(true);
    });
  });
});
