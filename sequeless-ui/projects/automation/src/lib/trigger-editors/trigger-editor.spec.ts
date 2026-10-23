/**
 * Contract under test (trigger-editors/trigger-editor.ts): `SqTriggerEditor` (`sq-trigger-editor`) honours
 * `[ONT-EXT]` for the `transition-trigger` slot. A kind select (`mat-select`) shows the selected transition's
 * trigger kind and, below it, the kind's field: `sq-on-change-field`, `sq-timer-field` (echo uses the from
 * state's label) or `sq-signal-field` (collisions from the type's other signal transitions, registered with
 * `context.registry`). Every valid change applies exactly one `setTransitionTrigger` delta through
 * `session.apply` (undo restores). A change that is not yet valid (a timer with no duration, a blank signal
 * name) applies nothing and shows `[data-error]`. Without a selected transition it says so.
 */
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TypesClient } from '@sequeless/core-api';
import { ControlRegistry } from '@sequeless/ontology';
import { of } from 'rxjs';

import { loadSession, mount, triggerOf } from '../testing/editor-fixture';
import { SqTriggerEditor } from './trigger-editor';

async function setup(transition: string) {
  const session = await loadSession();
  const apply = vi.spyOn(session, 'apply');
  const m = mount(SqTriggerEditor, session, transition);
  const chooseKind = async (label: string) => {
    (m.host.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    m.fixture.detectChanges();
    await m.fixture.whenStable();
    const options = [...TestBed.inject(OverlayContainer).getContainerElement().querySelectorAll<HTMLElement>('mat-option')];
    options.find((o) => o.textContent?.trim() === label)!.click();
    m.fixture.detectChanges();
  };
  return { session, apply, chooseKind, ...m };
}

describe('SqTriggerEditor', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        {
          provide: TypesClient,
          useValue: { describe: () => of({ iri: 'x', properties: [{ kind: 'relationship', iri: 'http://ex.org/parent', label: 'Parent' }] }) },
        },
      ],
    }),
  );
  afterEach(() => TestBed.resetTestingModule());

  it('shows the kind and the kind field', async () => {
    const timer = await setup('expire');
    expect(timer.host.querySelector('sq-timer-field')).not.toBeNull();
    expect(timer.host.querySelector('[data-echo]')?.textContent).toContain('fires 1h after entering Open');
  });

  it('shows the signal field with collisions and registers it', async () => {
    const register = vi.spyOn(ControlRegistry.prototype, 'register');
    const { host } = await setup('approve');
    expect(host.querySelector('sq-signal-field')).not.toBeNull();
    expect(host.querySelector('[data-warning]')?.textContent).toContain('also-approve');
    expect(register).toHaveBeenCalled();
    register.mockRestore();
  });

  it('changes a valid kind with exactly one delta, and undo restores it', async () => {
    const { session, apply, chooseKind, host, refresh } = await setup('complete');
    await chooseKind('On change');
    refresh();
    expect(apply).toHaveBeenCalledTimes(1);
    expect(triggerOf(session, 'complete').trigger).toEqual({ kind: 'ON_CHANGE', watchIris: [] });
    expect(host.querySelector('sq-on-change-field')).not.toBeNull();
    session.undo();
    expect(triggerOf(session, 'complete').trigger).toEqual({ kind: 'USER_ACTION' });
  });

  it('waits for a duration before switching to a timer', async () => {
    const { session, apply, chooseKind, host, fixture, refresh } = await setup('complete');
    await chooseKind('Timer');
    expect(apply).not.toHaveBeenCalled();
    expect(host.querySelector('[data-error]')?.textContent).toContain('positive duration');
    expect(triggerOf(session, 'complete').trigger).toEqual({ kind: 'USER_ACTION' });

    const hours = host.querySelector('input[data-part="hours"]') as HTMLInputElement;
    hours.value = '2';
    hours.dispatchEvent(new Event('input'));
    refresh();
    fixture.detectChanges();
    expect(apply).toHaveBeenCalledTimes(1);
    expect(triggerOf(session, 'complete').trigger).toEqual({ kind: 'TIMER', after: 'PT2H' });
  });

  it('applies a signal rename', async () => {
    const { session, apply, host, refresh } = await setup('approve');
    const input = host.querySelector('sq-signal-field input') as HTMLInputElement;
    input.value = 'confirm';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
    refresh();
    expect(apply).toHaveBeenCalledTimes(1);
    expect(triggerOf(session, 'approve').trigger).toEqual({ kind: 'EXTERNAL_SIGNAL', signalName: 'confirm' });
  });

  it('rejects a blank signal name', async () => {
    const { apply, host, refresh } = await setup('approve');
    const input = host.querySelector('sq-signal-field input') as HTMLInputElement;
    input.value = ' ';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
    refresh();
    expect(apply).not.toHaveBeenCalled();
    expect(host.querySelector('[data-error]')?.textContent).toContain('needs a name');
  });

  it('says so without a selected transition', async () => {
    const { host } = await setup('missing');
    expect(host.textContent).toContain('Select a transition');
  });
});
