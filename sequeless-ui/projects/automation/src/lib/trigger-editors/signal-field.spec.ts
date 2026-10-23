/**
 * Contract under test (trigger-editors/signal-field.ts): `SqSignalField` (`sq-signal-field`, inputs `value`,
 * `collisions` — names of other transitions using this signal — and optional `registry` + `subjectIri`) is a
 * text input labelled "Signal name". It emits `valueChange(text)` when the input commits (the `change` event,
 * not each keystroke). Collisions show a warning (`[data-warning]`, never an error or block). With a registry
 * and subject it registers its control there and unregisters when destroyed.
 */
import { TestBed } from '@angular/core/testing';

import { ControlRegistry } from '@sequeless/ontology';

import { SqSignalField } from './signal-field';

function setup(over: { value?: string; collisions?: string[]; registry?: ControlRegistry } = {}) {
  const fixture = TestBed.createComponent(SqSignalField);
  fixture.componentRef.setInput('value', over.value ?? '');
  fixture.componentRef.setInput('collisions', over.collisions ?? []);
  if (over.registry) {
    fixture.componentRef.setInput('registry', over.registry);
    fixture.componentRef.setInput('subjectIri', 'ex:t1');
  }
  const emitted: string[] = [];
  fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, emitted };
}

describe('SqSignalField', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the current name and emits on commit', () => {
    const { host, emitted } = setup({ value: 'go' });
    const input = host.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('go');
    input.value = 'stop';
    input.dispatchEvent(new Event('input'));
    expect(emitted).toEqual([]);
    input.dispatchEvent(new Event('change'));
    expect(emitted).toEqual(['stop']);
  });

  it('warns about collisions', () => {
    const { host } = setup({ collisions: ['approve', 'reject'] });
    expect(host.querySelector('[data-warning]')?.textContent).toContain('approve, reject');
  });

  it('has no warning without collisions', () => {
    expect(setup().host.querySelector('[data-warning]')).toBeNull();
  });

  it('registers its control and unregisters on destroy', () => {
    const registry = new ControlRegistry();
    const register = vi.spyOn(registry, 'register');
    const unregister = vi.spyOn(registry, 'unregister');
    const { fixture } = setup({ registry });
    expect(register).toHaveBeenCalledWith('ex:t1', expect.anything(), expect.any(HTMLElement));
    fixture.destroy();
    expect(unregister).toHaveBeenCalledWith(register.mock.calls[0][1]);
  });
});
