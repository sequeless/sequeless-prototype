import { InjectionToken } from '@angular/core';
import type { InputSignal, OutputEmitterRef, Provider, Type } from '@angular/core';
import type { StateMachineResponse } from '@sequeless/core-api';

import { SqKitStateMachineDiagram } from './kit-state-machine-diagram';

/** What a component bound to {@link STATE_MACHINE_DIAGRAM} must declare. */
export interface StateMachineDiagramContract {
  stateMachine: InputSignal<StateMachineResponse>;
  selectedTransition: InputSignal<string | null>;
  /** A transition was chosen; the payload is its name. */
  transitionSelect: OutputEmitterRef<string>;
  /** A state was chosen; the payload is its IRI. */
  stateSelect: OutputEmitterRef<string>;
}

/** The component that draws a state machine (D1). */
export const STATE_MACHINE_DIAGRAM = new InjectionToken<Type<unknown>>('State machine diagram', {
  providedIn: 'root',
  factory: () => SqKitStateMachineDiagram,
});

export function provideStateMachineDiagram(component: Type<unknown>): Provider {
  return { provide: STATE_MACHINE_DIAGRAM, useValue: component };
}
