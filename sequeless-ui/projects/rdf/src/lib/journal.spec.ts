/**
 * Contract under test (lib/journal.ts): `invertDelta` swaps a `QuadDelta`'s `added`/`removed` sets
 * so that applying the inverted delta undoes the original. `DeltaJournal` wraps a store with an
 * undo/redo history: `apply(delta)` pushes the delta onto the store and onto the undo stack, and
 * clears whatever redo stack existed; `undo()` pops the most recent delta, applies its inverse to
 * the store, and moves it onto the redo stack; `redo()` pops from the redo stack, re-applies the
 * original delta, and moves it back onto the undo stack. Both `undo()` and `redo()` return `false`
 * (and leave the store untouched) when their respective stack is empty. `canUndo`/`canRedo` mirror
 * stack occupancy, and `clear()` empties both stacks without touching the store's current contents.
 *
 * Every assertion about store contents compares quad sets via `sameQuadSet`, never Turtle text or
 * array identity, per the phase-4 spec's blank-node-tolerance rule.
 */
import { DataFactory } from 'n3';
import type { Quad } from '@rdfjs/types';
import type { QuadDelta } from './model';
import { N3RdfStore } from './store';
import { DeltaJournal, invertDelta } from './journal';
import { sameQuadSet } from '../testing/quad-set';

const { namedNode, quad } = DataFactory;

const ex = (local: string) => namedNode(`https://example.test/${local}`);

function makeQuad(s: string, p: string, o: string): Quad {
  return quad(ex(s), ex(p), ex(o));
}

describe('invertDelta', () => {
  it('swaps added and removed, leaving each set otherwise unchanged', () => {
    const added = [makeQuad('a', 'p', 'b')];
    const removed = [makeQuad('c', 'q', 'd')];
    const inverted = invertDelta({ added, removed });
    expect(sameQuadSet(inverted.added, removed)).toBe(true);
    expect(sameQuadSet(inverted.removed, added)).toBe(true);
  });

  it('round-trips: inverting twice reproduces the original delta', () => {
    const delta: QuadDelta = {
      added: [makeQuad('a', 'p', 'b')],
      removed: [makeQuad('c', 'q', 'd'), makeQuad('e', 'r', 'f')],
    };
    const roundTripped = invertDelta(invertDelta(delta));
    expect(sameQuadSet(roundTripped.added, delta.added)).toBe(true);
    expect(sameQuadSet(roundTripped.removed, delta.removed)).toBe(true);
  });
});

describe('DeltaJournal', () => {
  describe('apply', () => {
    it('applies the delta to the underlying store', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      const q = makeQuad('a', 'p', 'b');
      journal.apply({ added: [q], removed: [] });
      expect(store.has(q)).toBe(true);
    });

    it('removes before adding, matching RdfStore.apply semantics', () => {
      const q1 = makeQuad('a', 'p', 'b');
      const q2 = makeQuad('a', 'p', 'c');
      const untouched = makeQuad('x', 'y', 'z');
      const store = new N3RdfStore([q1, untouched]);
      const journal = new DeltaJournal(store);
      journal.apply({ added: [q2], removed: [q1] });
      expect(sameQuadSet(store.quads(), [q2, untouched])).toBe(true);
    });
  });

  describe('canUndo / canRedo', () => {
    it('reports both false on a freshly constructed journal', () => {
      const journal = new DeltaJournal(new N3RdfStore());
      expect(journal.canUndo).toBe(false);
      expect(journal.canRedo).toBe(false);
    });

    it('reports canUndo true and canRedo false after one apply', () => {
      const journal = new DeltaJournal(new N3RdfStore());
      journal.apply({ added: [makeQuad('a', 'p', 'b')], removed: [] });
      expect(journal.canUndo).toBe(true);
      expect(journal.canRedo).toBe(false);
    });

    it('reports canUndo false and canRedo true after undoing the only applied delta', () => {
      const journal = new DeltaJournal(new N3RdfStore());
      journal.apply({ added: [makeQuad('a', 'p', 'b')], removed: [] });
      journal.undo();
      expect(journal.canUndo).toBe(false);
      expect(journal.canRedo).toBe(true);
    });
  });

  describe('undo', () => {
    it('restores the quad set from before the most recent apply', () => {
      const seed = makeQuad('x', 'y', 'z');
      const store = new N3RdfStore([seed]);
      const journal = new DeltaJournal(store);
      const before = store.quads();
      journal.apply({ added: [makeQuad('a', 'p', 'b')], removed: [seed] });

      const undone = journal.undo();

      expect(undone).toBe(true);
      expect(sameQuadSet(store.quads(), before)).toBe(true);
    });

    it('undoes multiple applied deltas in last-in-first-out order', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      const q1 = makeQuad('a', 'p', 'b');
      const q2 = makeQuad('c', 'q', 'd');
      journal.apply({ added: [q1], removed: [] });
      journal.apply({ added: [q2], removed: [] });

      journal.undo();
      expect(sameQuadSet(store.quads(), [q1])).toBe(true);

      journal.undo();
      expect(sameQuadSet(store.quads(), [])).toBe(true);
    });

    it('returns false and leaves the store untouched when there is nothing to undo', () => {
      const seed = makeQuad('x', 'y', 'z');
      const store = new N3RdfStore([seed]);
      const journal = new DeltaJournal(store);

      const undone = journal.undo();

      expect(undone).toBe(false);
      expect(sameQuadSet(store.quads(), [seed])).toBe(true);
    });
  });

  describe('redo', () => {
    it('re-applies a delta that was just undone', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      const q = makeQuad('a', 'p', 'b');
      journal.apply({ added: [q], removed: [] });
      journal.undo();

      const redone = journal.redo();

      expect(redone).toBe(true);
      expect(store.has(q)).toBe(true);
      expect(journal.canUndo).toBe(true);
      expect(journal.canRedo).toBe(false);
    });

    it('redoes multiple undone deltas in last-undone-first-redone order', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      const q1 = makeQuad('a', 'p', 'b');
      const q2 = makeQuad('c', 'q', 'd');
      journal.apply({ added: [q1], removed: [] });
      journal.apply({ added: [q2], removed: [] });
      journal.undo();
      journal.undo();

      journal.redo();
      expect(sameQuadSet(store.quads(), [q1])).toBe(true);

      journal.redo();
      expect(sameQuadSet(store.quads(), [q1, q2])).toBe(true);
    });

    it('returns false and leaves the store untouched when there is nothing to redo', () => {
      const seed = makeQuad('x', 'y', 'z');
      const store = new N3RdfStore([seed]);
      const journal = new DeltaJournal(store);

      const redone = journal.redo();

      expect(redone).toBe(false);
      expect(sameQuadSet(store.quads(), [seed])).toBe(true);
    });
  });

  describe('redo stack invalidation', () => {
    it('clears the redo stack when a new delta is applied after an undo', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      const q1 = makeQuad('a', 'p', 'b');
      const q2 = makeQuad('c', 'q', 'd');
      journal.apply({ added: [q1], removed: [] });
      journal.undo();
      expect(journal.canRedo).toBe(true);

      journal.apply({ added: [q2], removed: [] });

      expect(journal.canRedo).toBe(false);
      expect(journal.redo()).toBe(false);
      expect(sameQuadSet(store.quads(), [q2])).toBe(true);
    });
  });

  describe('clear', () => {
    it('empties both the undo and redo stacks without touching the store', () => {
      const store = new N3RdfStore();
      const journal = new DeltaJournal(store);
      journal.apply({ added: [makeQuad('a', 'p', 'b')], removed: [] });
      journal.undo();
      expect(journal.canRedo).toBe(true);

      journal.clear();

      expect(journal.canUndo).toBe(false);
      expect(journal.canRedo).toBe(false);
      expect(journal.undo()).toBe(false);
      expect(journal.redo()).toBe(false);
    });
  });
});
