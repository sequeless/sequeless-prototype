/*
 * Contract [ONT-TEXTDIFF]: SqTextDiff (selector `sq-text-diff`) with inputs `base` and `working` hosts a
 * view made by the TEXT_DIFF_VIEW factory. The factory is called once with the host element, the view
 * receives update(base, working) after the first render and on every input change, and dispose() runs
 * on destroy.
 */
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SqTextDiff } from './text-diff';
import { TEXT_DIFF_VIEW } from './text-diff-view';
import type { TextDiffFactory } from './text-diff-view';

@Component({
  imports: [SqTextDiff],
  template: `<sq-text-diff [base]="base()" [working]="working()" />`,
})
class Host {
  readonly base = signal('one');
  readonly working = signal('two');
}

function setup() {
  const updates: [string, string][] = [];
  const hosts: HTMLElement[] = [];
  let disposed = 0;
  const factory: TextDiffFactory = (host) => {
    hosts.push(host);
    return {
      update: (base, working) => updates.push([base, working]),
      dispose: () => disposed++,
    };
  };
  TestBed.configureTestingModule({ providers: [{ provide: TEXT_DIFF_VIEW, useValue: factory }] });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  return { fixture, updates, hosts, disposed: () => disposed };
}

describe('SqTextDiff', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('creates one view on a host element inside the component', () => {
    const { fixture, hosts } = setup();
    expect(hosts).toHaveLength(1);
    expect(fixture.nativeElement.contains(hosts[0])).toBe(true);
  });

  it('pushes the inputs to the view after the first render', () => {
    const { updates } = setup();
    expect(updates.at(-1)).toEqual(['one', 'two']);
  });

  it('pushes new input values to the view', () => {
    const { fixture, updates } = setup();
    fixture.componentInstance.working.set('three');
    fixture.detectChanges();
    expect(updates.at(-1)).toEqual(['one', 'three']);
  });

  it('disposes the view on destroy', () => {
    const { fixture, disposed } = setup();
    fixture.destroy();
    expect(disposed()).toBe(1);
  });
});
