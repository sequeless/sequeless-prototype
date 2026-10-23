import type { InputSignal, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { OntologyClient } from '@sequeless/core-api';
import { ControlRegistry, OntologySession } from '@sequeless/ontology';
import type { OntologyEditorPanelContext } from '@sequeless/ontology';
import { readStateMachine } from '@sequeless/rdf';
import type { TransitionView } from '@sequeless/rdf';
import { of } from 'rxjs';

/** A small ontology with one transition of each trigger kind, shared by the automation editor specs. */
export const EX = 'http://ex.org/';
export const TASK = `${EX}Task`;

export const TTL = `@prefix ex: <${EX}> .
@prefix sq: <https://sequeless.dev/ns/meta#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

ex:Task a owl:Class ; sq:label "Task" .
ex:parent a owl:ObjectProperty ; rdfs:domain ex:Task ; rdfs:range ex:Task ; sq:label "Parent" .

ex:TaskLifecycle a sq:StateMachine ;
  sq:appliesTo ex:Task ; sq:initialState ex:Open ; sq:state ex:Open, ex:Done ;
  sq:transition
    [ a sq:Transition ; sq:name "complete" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:UserAction ; sq:guard "self.title != null" ; sq:guardMessage "Needs a title" ] ,
    [ a sq:Transition ; sq:name "expire" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:Timer ; sq:after "PT1H" ] ,
    [ a sq:Transition ; sq:name "approve" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:ExternalSignal ; sq:signalName "approve" ] ,
    [ a sq:Transition ; sq:name "also-approve" ; sq:from ex:Open ; sq:to ex:Done ;
      sq:trigger sq:ExternalSignal ; sq:signalName "approve" ] .

ex:Open a sq:State ; sq:label "Open" .
ex:Done a sq:State ; sq:label "Done" .
`;

/**
 * Mounts a panel the way the ontology's state-machine editor hosts it: `context` is rebuilt from the session
 * after every revision (call `refresh()` after anything that applies a delta).
 */
export function mount<T extends { context: InputSignal<OntologyEditorPanelContext> }>(
  panel: Type<T>,
  session: OntologySession,
  transitionName: string,
) {
  const registry = TestBed.inject(ControlRegistry);
  const fixture = TestBed.createComponent(panel);
  const refresh = () => {
    const store = session.store();
    const transition = store
      ? readStateMachine(store, TASK)?.transitions.find((t) => t.name === transitionName)
      : undefined;
    fixture.componentRef.setInput('context', { typeIri: TASK, session, registry, transition });
    fixture.detectChanges();
  };
  refresh();
  return { fixture, host: fixture.nativeElement as HTMLElement, refresh, registry };
}

export async function loadSession(): Promise<OntologySession> {
  TestBed.configureTestingModule({
    providers: [OntologySession, ControlRegistry, { provide: OntologyClient, useValue: { load: () => of(TTL) } }],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  return session;
}

export function triggerOf(session: OntologySession, name: string) {
  return readStateMachine(session.store()!, TASK)!.transitions.find((t) => t.name === name)!;
}
