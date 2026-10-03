/**
 * `threeWayDiff(local, server, base)` builds a per-field three-way comparison for
 * `sq-conflict-dialog`'s 409 `stale-object` recovery flow (the phase-5 spec's "Conflict
 * dialog" component) — one `FieldDiff` per field across the three snapshots, keyed by the
 * union of all three objects' keys.
 *
 * Key order: `base`'s own keys first (in `base`'s enumeration order), then any key present in
 * `server` but not `base`, then any key present in `local` but neither `base` nor already
 * counted as a new `server` key — so a key added by both `local` and `server` (and absent from
 * `base`) lands in the "new server key" slot, not duplicated. A key missing from a given
 * snapshot reads as `undefined` in that slot.
 *
 * Equality for `localChanged`/`serverChanged`/`conflict` is structural (deep) — plain objects
 * and arrays are compared by contents, not reference, and array element order matters.
 * `localChanged`/`serverChanged` compare `local`/`server` against `base`; `conflict`
 * additionally requires `local` and `server` to differ from each other, so both sides changing to
 * the *same* value is a (non-conflicting) agreement, not a conflict.
 *
 * `reapplyLocal(diffs)` reconstructs the record the "reapply my changes onto the server version"
 * action produces: it starts from each diff's `server` value and overrides it with `local` for
 * every `localChanged` field (so local wins a real conflict too), omitting any key whose
 * resulting value is `undefined`. Neither function mutates its inputs.
 */

export interface FieldDiff {
  key: string;
  base: unknown;
  local: unknown;
  server: unknown;
  localChanged: boolean;
  serverChanged: boolean;
  conflict: boolean;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

function deepEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) {
    return true;
  }

  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) {
      return false;
    }
    for (let i = 0; i < a.length; i++) {
      if (!deepEqual(a[i], b[i])) {
        return false;
      }
    }
    return true;
  }

  if (isPlainObject(a) && isPlainObject(b)) {
    const keysA = Object.keys(a);
    const keysB = Object.keys(b);
    if (keysA.length !== keysB.length) {
      return false;
    }
    for (const key of keysA) {
      if (!Object.prototype.hasOwnProperty.call(b, key)) {
        return false;
      }
      if (!deepEqual(a[key], b[key])) {
        return false;
      }
    }
    return true;
  }

  return false;
}

function unionKeys(
  base: Record<string, unknown>,
  server: Record<string, unknown>,
  local: Record<string, unknown>,
): string[] {
  const seen = new Set<string>();
  const keys: string[] = [];

  const add = (key: string): void => {
    if (!seen.has(key)) {
      seen.add(key);
      keys.push(key);
    }
  };

  for (const key of Object.keys(base)) {
    add(key);
  }
  for (const key of Object.keys(server)) {
    add(key);
  }
  for (const key of Object.keys(local)) {
    add(key);
  }

  return keys;
}

export function threeWayDiff(
  local: Record<string, unknown>,
  server: Record<string, unknown>,
  base: Record<string, unknown>,
): FieldDiff[] {
  return unionKeys(base, server, local).map((key) => {
    const baseValue = base[key];
    const localValue = local[key];
    const serverValue = server[key];

    const localChanged = !deepEqual(localValue, baseValue);
    const serverChanged = !deepEqual(serverValue, baseValue);
    const conflict =
      localChanged && serverChanged && !deepEqual(localValue, serverValue);

    return {
      key,
      base: baseValue,
      local: localValue,
      server: serverValue,
      localChanged,
      serverChanged,
      conflict,
    };
  });
}

export function reapplyLocal(diffs: readonly FieldDiff[]): Record<string, unknown> {
  const result: Record<string, unknown> = {};

  for (const diff of diffs) {
    const value = diff.localChanged ? diff.local : diff.server;
    if (value !== undefined) {
      result[diff.key] = value;
    }
  }

  return result;
}
