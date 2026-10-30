/**
 * Contract under test (lib/util/three-way-diff.ts): `threeWayDiff(local, server, base)` builds a
 * per-field three-way comparison for `sq-conflict-dialog`'s 409 `stale-object` recovery flow (the
 * phase-5 spec's "Conflict dialog" component) — one `FieldDiff` per field across the three
 * snapshots, keyed by the union of all three objects' keys.
 *
 * Key order: `base`'s own keys first (in `base`'s enumeration order), then any key present in
 * `server` but not `base`, then any key present in `local` but neither `base` nor already counted
 * as a new `server` key — so a key added by both `local` and `server` (and absent from `base`)
 * lands in the "new server key" slot, not duplicated. A key missing from a given snapshot reads as
 * `undefined` in that slot.
 *
 * Equality for `localChanged`/`serverChanged`/`conflict` is structural (deep) — plain objects and
 * arrays are compared by contents, not reference, and array element order matters.
 * `localChanged`/`serverChanged` compare `local`/`server` against `base`; `conflict` additionally
 * requires `local` and `server` to differ from each other, so both sides changing to the *same*
 * value is a (non-conflicting) agreement, not a conflict.
 *
 * `reapplyLocal(diffs)` reconstructs the record the "reapply my changes onto the server version"
 * action produces: it starts from each diff's `server` value and overrides it with `local` for
 * every `localChanged` field (so local wins a real conflict too), omitting any key whose resulting
 * value is `undefined`. Neither function mutates its inputs.
 */
import type { FieldDiff } from './three-way-diff';
import { reapplyLocal, threeWayDiff } from './three-way-diff';

describe('threeWayDiff', () => {
  it('orders keys as base keys first, then new server keys, then new local keys', () => {
    const base = { b: 1, a: 2 };
    const local = { b: 1, a: 2, z: 'local-only' };
    const server = { b: 1, a: 2, y: 'server-only' };
    const diffs = threeWayDiff(local, server, base);
    expect(diffs.map((d) => d.key)).toEqual(['b', 'a', 'y', 'z']);
  });

  it('groups a key added by both local and server (not in base) under the new-server-key slot', () => {
    const diffs = threeWayDiff({ shared: 'local-value' }, { shared: 'server-value' }, {});
    expect(diffs.map((d) => d.key)).toEqual(['shared']);
  });

  it('reads a key missing from one snapshot as undefined in that slot', () => {
    const base = { name: 'Alice' };
    const diffs = threeWayDiff({ name: 'Alice' }, {}, base);
    expect(diffs).toEqual([
      {
        key: 'name',
        base: 'Alice',
        local: 'Alice',
        server: undefined,
        localChanged: false,
        serverChanged: true,
        conflict: false,
      },
    ]);
  });

  it('flags no changes when all three snapshots agree', () => {
    const base = { title: 'Widget' };
    const diffs = threeWayDiff({ title: 'Widget' }, { title: 'Widget' }, base);
    expect(diffs).toEqual([
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget',
        server: 'Widget',
        localChanged: false,
        serverChanged: false,
        conflict: false,
      },
    ]);
  });

  it('flags localChanged only when local diverges from base and server matches base', () => {
    const base = { title: 'Widget' };
    const diffs = threeWayDiff({ title: 'Widget v2' }, { title: 'Widget' }, base);
    expect(diffs).toEqual([
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget',
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
    ]);
  });

  it('flags serverChanged only when server diverges from base and local matches base', () => {
    const base = { title: 'Widget' };
    const diffs = threeWayDiff({ title: 'Widget' }, { title: 'Widget v3' }, base);
    expect(diffs).toEqual([
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget',
        server: 'Widget v3',
        localChanged: false,
        serverChanged: true,
        conflict: false,
      },
    ]);
  });

  it('does not flag a conflict when local and server both change to the same value', () => {
    const base = { title: 'Widget' };
    const diffs = threeWayDiff({ title: 'Widget v4' }, { title: 'Widget v4' }, base);
    expect(diffs).toEqual([
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v4',
        server: 'Widget v4',
        localChanged: true,
        serverChanged: true,
        conflict: false,
      },
    ]);
  });

  it('flags a conflict when local and server change to different values', () => {
    const base = { title: 'Widget' };
    const diffs = threeWayDiff({ title: 'Widget v2' }, { title: 'Widget v3' }, base);
    expect(diffs).toEqual([
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget v3',
        localChanged: true,
        serverChanged: true,
        conflict: true,
      },
    ]);
  });

  it('compares arrays structurally, so equal contents in the same order is unchanged', () => {
    const base = { tags: ['a', 'b'] };
    const diffs = threeWayDiff({ tags: ['a', 'b'] }, { tags: ['a', 'b'] }, base);
    expect(diffs[0].localChanged).toBe(false);
    expect(diffs[0].serverChanged).toBe(false);
  });

  it('treats a reordered array as changed, since array order matters', () => {
    const base = { tags: ['a', 'b'] };
    const diffs = threeWayDiff({ tags: ['b', 'a'] }, { tags: ['a', 'b'] }, base);
    expect(diffs[0].localChanged).toBe(true);
    expect(diffs[0].serverChanged).toBe(false);
  });

  it('compares plain objects structurally regardless of key order', () => {
    const base = { address: { city: 'Metropolis', zip: '00000' } };
    const local = { address: { zip: '00000', city: 'Metropolis' } };
    const diffs = threeWayDiff(local, { address: { city: 'Metropolis', zip: '00000' } }, base);
    expect(diffs[0].localChanged).toBe(false);
  });

  it('flags a nested object as changed when a nested field differs, without flagging a conflict', () => {
    const base = { address: { city: 'Metropolis', zip: '00000' } };
    const local = { address: { city: 'Gotham', zip: '00000' } };
    const diffs = threeWayDiff(local, { address: { city: 'Metropolis', zip: '00000' } }, base);
    expect(diffs[0].localChanged).toBe(true);
    expect(diffs[0].conflict).toBe(false);
  });

  it('treats deletion (present in base, missing from a snapshot) as a change', () => {
    const base = { note: 'hello' };
    const diffs = threeWayDiff({}, { note: 'hello' }, base);
    expect(diffs).toEqual([
      {
        key: 'note',
        base: 'hello',
        local: undefined,
        server: 'hello',
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
    ]);
  });

  it('does not mutate any of its input records', () => {
    const base = { title: 'Widget' };
    const local = { title: 'Widget v2' };
    const server = { title: 'Widget' };
    const baseCopy = { ...base };
    const localCopy = { ...local };
    const serverCopy = { ...server };

    threeWayDiff(local, server, base);

    expect(base).toEqual(baseCopy);
    expect(local).toEqual(localCopy);
    expect(server).toEqual(serverCopy);
  });
});

describe('reapplyLocal', () => {
  it('uses the server value for an unchanged-locally field', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget',
        server: 'Widget v3',
        localChanged: false,
        serverChanged: true,
        conflict: false,
      },
    ];
    expect(reapplyLocal(diffs)).toEqual({ title: 'Widget v3' });
  });

  it('overrides the server value with local for a locally changed field', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget',
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
    ];
    expect(reapplyLocal(diffs)).toEqual({ title: 'Widget v2' });
  });

  it('lets local win a real conflict', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget v3',
        localChanged: true,
        serverChanged: true,
        conflict: true,
      },
    ];
    expect(reapplyLocal(diffs)).toEqual({ title: 'Widget v2' });
  });

  it('omits a key whose resulting value is undefined', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'note',
        base: 'hello',
        local: undefined,
        server: undefined,
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget',
        server: 'Widget',
        localChanged: false,
        serverChanged: false,
        conflict: false,
      },
    ];
    expect(reapplyLocal(diffs)).toEqual({ title: 'Widget' });
  });

  it('reconstructs a full record across multiple fields, mixing unchanged/changed/conflicting', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget',
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
      {
        key: 'status',
        base: 'DRAFT',
        local: 'DRAFT',
        server: 'PUBLISHED',
        localChanged: false,
        serverChanged: true,
        conflict: false,
      },
      {
        key: 'notes',
        base: 'x',
        local: 'local-notes',
        server: 'server-notes',
        localChanged: true,
        serverChanged: true,
        conflict: true,
      },
    ];
    expect(reapplyLocal(diffs)).toEqual({
      title: 'Widget v2',
      status: 'PUBLISHED',
      notes: 'local-notes',
    });
  });

  it('does not mutate the diffs array passed to it', () => {
    const diffs: FieldDiff[] = [
      {
        key: 'title',
        base: 'Widget',
        local: 'Widget v2',
        server: 'Widget',
        localChanged: true,
        serverChanged: false,
        conflict: false,
      },
    ];
    const copy = JSON.parse(JSON.stringify(diffs));

    reapplyLocal(diffs);

    expect(diffs).toEqual(copy);
  });
});
