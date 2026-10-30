/**
 * Contract under test (trigger-editors/guard-editor.ts): `SqGuardEditor` (`sq-guard-editor`) honours
 * `[ONT-EXT]` for the `transition-guard` slot (`context = input.required<OntologyEditorPanelContext>()`). It
 * shows the selected transition's guard expression and failure message in a textarea and an input, labelled
 * "validated server-side only" (`[data-hint]`). When a field commits (`change`) it applies one `setGuard`
 * delta through `session.apply` (so undo restores it); blank text clears the term; an unchanged value applies
 * nothing. Without a selected transition it says so and edits nothing.
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { loadSession, mount, triggerOf } from '../testing/editor-fixture';
import { SqGuardEditor } from './guard-editor';

async function setup(transition = 'complete') {
  const session = await loadSession();
  const apply = vi.spyOn(session, 'apply');
  const m = mount(SqGuardEditor, session, transition);
  const guard = m.host.querySelector('textarea') as HTMLTextAreaElement;
  const message = m.host.querySelector('input') as HTMLInputElement;
  const commit = (el: HTMLInputElement | HTMLTextAreaElement, value: string) => {
    el.value = value;
    el.dispatchEvent(new Event('input'));
    el.dispatchEvent(new Event('change'));
    m.refresh();
  };
  return { session, apply, guard, message, commit, ...m };
}

describe('SqGuardEditor', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideNoopAnimations()] }));
  afterEach(() => TestBed.resetTestingModule());

  it('shows the guard and its message with the server-side-only hint', async () => {
    const { guard, message, host } = await setup();
    expect(guard.value).toBe('self.title != null');
    expect(message.value).toBe('Needs a title');
    expect(host.querySelector('[data-hint]')?.textContent).toContain('validated server-side only');
  });

  it('applies one setGuard delta when the guard changes, and undo restores it', async () => {
    const { session, apply, guard, commit } = await setup();
    commit(guard, 'self.score > 1');
    expect(apply).toHaveBeenCalledTimes(1);
    expect(triggerOf(session, 'complete').guard).toBe('self.score > 1');
    session.undo();
    expect(triggerOf(session, 'complete').guard).toBe('self.title != null');
  });

  it('applies the message change', async () => {
    const { session, apply, message, commit } = await setup();
    commit(message, 'Title required');
    expect(apply).toHaveBeenCalledTimes(1);
    expect(triggerOf(session, 'complete').guardMessage).toBe('Title required');
  });

  it('clears the guard when the text is blank', async () => {
    const { session, guard, commit } = await setup();
    commit(guard, '  ');
    expect(triggerOf(session, 'complete').guard).toBeNull();
  });

  it('applies nothing when the value is unchanged', async () => {
    const { apply, guard, commit } = await setup();
    commit(guard, 'self.title != null');
    expect(apply).not.toHaveBeenCalled();
  });

  it('edits nothing without a selected transition', async () => {
    const session = await loadSession();
    const { host } = mount(SqGuardEditor, session, 'missing');
    expect(host.querySelector('textarea')).toBeNull();
    expect(host.textContent).toContain('Select a transition');
  });
});
