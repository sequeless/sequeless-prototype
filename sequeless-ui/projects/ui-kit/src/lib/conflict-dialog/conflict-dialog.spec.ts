/**
 * Contract under test (lib/conflict-dialog/conflict-dialog.ts): `SqConflictDialog` (selector
 * `sq-conflict-dialog`) is a `MatDialog` component for the 409 `stale-object` recovery flow
 * (phase-5 spec, "sq-conflict-dialog"). It injects `MAT_DIALOG_DATA` (a `ConflictDialogData`:
 * `local`/`server`/`base` records plus an optional `labels` map) and `MatDialogRef`, and derives
 * `diffs` from `threeWayDiff(local, server, base)` (../util/three-way-diff — see that file's own
 * spec for the diff semantics).
 *
 * The template renders a table with one `<tr data-key="{{key}}">` per diff for which
 * `localChanged || serverChanged` (an unchanged field is omitted entirely), tagged with class
 * `sq-conflict` when that diff's `conflict` is true. Each row's label is `labels[key] ?? key`.
 * Each row has three cells tagged `data-col="base" | "local" | "server"`: `null`/`undefined`
 * render as `—`, plain objects render as their `JSON.stringify`, everything else renders as its
 * plain text value.
 *
 * Two actions, neither of which fires on its own (no auto-resolve): `data-action="reapply"`
 * closes the dialog with `{ action: 'reapply', value: reapplyLocal(diffs) }`; `data-action="discard"`
 * closes it with `{ action: 'discard', value: server }` (the dialog data's own `server` record,
 * unmodified).
 *
 * `openConflictDialog(dialog, data)` opens `SqConflictDialog` via the injected `MatDialog` with
 * `disableClose: true` and returns `dialogRef.afterClosed()`.
 */
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef, type MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { firstValueFrom, of } from 'rxjs';
import type { ConflictDialogData, ConflictResolution } from './conflict-dialog';
import { openConflictDialog, SqConflictDialog } from './conflict-dialog';

const BASE: Record<string, unknown> = {
  title: 'Widget',
  status: 'DRAFT',
  owner: 'Alice',
  notes: 'x',
  assignee: 'Bob',
  meta: { a: 1 },
  dueDate: null,
};

const LOCAL: Record<string, unknown> = {
  title: 'Widget v2',
  status: 'DRAFT',
  owner: 'Alice',
  notes: 'local-notes',
  meta: { a: 1 },
  dueDate: null,
};

const SERVER: Record<string, unknown> = {
  title: 'Widget',
  status: 'PUBLISHED',
  owner: 'Alice',
  notes: 'server-notes',
  assignee: 'Bob',
  meta: { a: 2 },
  dueDate: '2026-01-01',
};

const DATA: ConflictDialogData = { local: LOCAL, server: SERVER, base: BASE };

function rowEl(fixture: { nativeElement: HTMLElement }, key: string): HTMLElement | null {
  return fixture.nativeElement.querySelector(`tr[data-key="${key}"]`);
}

function cellText(
  fixture: { nativeElement: HTMLElement },
  key: string,
  col: 'base' | 'local' | 'server',
): string | undefined {
  return fixture.nativeElement
    .querySelector(`tr[data-key="${key}"] [data-col="${col}"]`)
    ?.textContent?.trim();
}

function createFixture(data: ConflictDialogData, closeSpy: ReturnType<typeof vi.fn> = vi.fn()) {
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close: closeSpy } },
    ],
  });
  return TestBed.createComponent(SqConflictDialog);
}

describe('SqConflictDialog', () => {
  it('omits a row for a field unchanged in both local and server', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(rowEl(fixture, 'owner')).toBeNull();
  });

  it('renders exactly one row per field with a local or server change', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    const rows = Array.from(host.querySelectorAll<HTMLElement>('tr[data-key]'));
    expect(rows.map((r) => r.getAttribute('data-key')).sort()).toEqual(
      ['assignee', 'dueDate', 'meta', 'notes', 'status', 'title'].sort(),
    );
  });

  it('tags a real conflict row with class sq-conflict', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(rowEl(fixture, 'notes')?.classList.contains('sq-conflict')).toBe(true);
  });

  it('does not tag a local-only change row with class sq-conflict', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(rowEl(fixture, 'title')?.classList.contains('sq-conflict')).toBe(false);
  });

  it('does not tag a server-only change row with class sq-conflict', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(rowEl(fixture, 'status')?.classList.contains('sq-conflict')).toBe(false);
  });

  it('renders the base/local/server string values for a conflicting field', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(cellText(fixture, 'notes', 'base')).toBe('x');
    expect(cellText(fixture, 'notes', 'local')).toBe('local-notes');
    expect(cellText(fixture, 'notes', 'server')).toBe('server-notes');
  });

  it('renders — for a null value and — for an undefined (deleted) value', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(cellText(fixture, 'dueDate', 'base')).toBe('—');
    expect(cellText(fixture, 'dueDate', 'local')).toBe('—');
    expect(cellText(fixture, 'dueDate', 'server')).toBe('2026-01-01');
    expect(cellText(fixture, 'assignee', 'local')).toBe('—');
    expect(cellText(fixture, 'assignee', 'base')).toBe('Bob');
    expect(cellText(fixture, 'assignee', 'server')).toBe('Bob');
  });

  it('renders an object value as its JSON.stringify form', async () => {
    const fixture = createFixture(DATA);
    await fixture.whenStable();

    expect(cellText(fixture, 'meta', 'base')).toBe('{"a":1}');
    expect(cellText(fixture, 'meta', 'local')).toBe('{"a":1}');
    expect(cellText(fixture, 'meta', 'server')).toBe('{"a":2}');
  });

  it('labels a row with labels[key] when provided, falling back to the raw key otherwise', async () => {
    const dataWithLabels: ConflictDialogData = {
      ...DATA,
      labels: { title: 'Title', notes: 'Notes' },
    };
    const fixture = createFixture(dataWithLabels);
    await fixture.whenStable();

    expect(rowEl(fixture, 'title')?.textContent).toContain('Title');
    expect(rowEl(fixture, 'notes')?.textContent).toContain('Notes');
    expect(rowEl(fixture, 'status')?.textContent).toContain('status');
  });

  it('never calls dialogRef.close on its own', async () => {
    const closeSpy = vi.fn();
    const fixture = createFixture(DATA, closeSpy);

    await fixture.whenStable();

    expect(closeSpy).not.toHaveBeenCalled();
  });

  it('closes with the reapply action and reapplyLocal(diffs) as the value when reapply is clicked', async () => {
    const closeSpy = vi.fn();
    const fixture = createFixture(DATA, closeSpy);
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    host.querySelector<HTMLButtonElement>('[data-action="reapply"]')?.click();
    await fixture.whenStable();

    const expected: ConflictResolution = {
      action: 'reapply',
      value: {
        title: 'Widget v2',
        status: 'PUBLISHED',
        owner: 'Alice',
        notes: 'local-notes',
        meta: { a: 2 },
        dueDate: '2026-01-01',
      },
    };
    expect(closeSpy).toHaveBeenCalledTimes(1);
    expect(closeSpy).toHaveBeenCalledWith(expected);
  });

  it('closes with the discard action and the server record unmodified as the value when discard is clicked', async () => {
    const closeSpy = vi.fn();
    const fixture = createFixture(DATA, closeSpy);
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    host.querySelector<HTMLButtonElement>('[data-action="discard"]')?.click();
    await fixture.whenStable();

    const expected: ConflictResolution = { action: 'discard', value: SERVER };
    expect(closeSpy).toHaveBeenCalledTimes(1);
    expect(closeSpy).toHaveBeenCalledWith(expected);
  });
});

describe('openConflictDialog', () => {
  it('opens SqConflictDialog with the given data and disableClose: true', () => {
    const openSpy = vi.fn(() => ({ afterClosed: () => of(undefined) }));
    const dialog = { open: openSpy } as unknown as MatDialog;

    openConflictDialog(dialog, DATA);

    expect(openSpy).toHaveBeenCalledTimes(1);
    expect(openSpy).toHaveBeenCalledWith(
      SqConflictDialog,
      expect.objectContaining({ data: DATA, disableClose: true }),
    );
  });

  it('resolves with the value the dialog is closed with', async () => {
    const resolution: ConflictResolution = { action: 'discard', value: SERVER };
    const openSpy = vi.fn(() => ({ afterClosed: () => of(resolution) }));
    const dialog = { open: openSpy } as unknown as MatDialog;

    const result = await firstValueFrom(openConflictDialog(dialog, DATA));

    expect(result).toEqual(resolution);
  });
});
