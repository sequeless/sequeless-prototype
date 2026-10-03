import type { QuadDelta } from './model';
import type { RdfStore } from './store';

/**
 * Swaps a `QuadDelta`'s `added`/`removed` sets so that applying the result undoes the original.
 */
export function invertDelta(d: QuadDelta): QuadDelta {
  return { added: d.removed, removed: d.added };
}

/**
 * Wraps a store with an undo/redo history. `apply` pushes the delta onto the store and the undo
 * stack (clearing the redo stack); `undo`/`redo` move deltas between stacks, applying
 * appropriate inverses to the store, and return `false` (leaving the store untouched) when their
 * stack is empty.
 */
export class DeltaJournal {
  private readonly _store: RdfStore;
  private readonly _undo: QuadDelta[] = [];
  private readonly _redo: QuadDelta[] = [];

  constructor(store: RdfStore) {
    this._store = store;
  }

  apply(d: QuadDelta): void {
    this._store.apply(d);
    this._undo.push(d);
    this._redo.length = 0;
  }

  undo(): boolean {
    const d = this._undo.pop();
    if (d === undefined) {
      return false;
    }
    this._store.apply(invertDelta(d));
    this._redo.push(d);
    return true;
  }

  redo(): boolean {
    const d = this._redo.pop();
    if (d === undefined) {
      return false;
    }
    this._store.apply(d);
    this._undo.push(d);
    return true;
  }

  get canUndo(): boolean {
    return this._undo.length > 0;
  }

  get canRedo(): boolean {
    return this._redo.length > 0;
  }

  clear(): void {
    this._undo.length = 0;
    this._redo.length = 0;
  }
}
