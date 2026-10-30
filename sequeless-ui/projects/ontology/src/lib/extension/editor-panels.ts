import { InjectionToken } from '@angular/core';
import type { Provider, Type } from '@angular/core';

import type { TransitionView } from '@sequeless/rdf';

import type { ControlRegistry } from '../issues/control-registry';
import type { OntologySession } from '../session/ontology-session';

/** What a contributed panel receives through its required `context` input. */
export interface OntologyEditorPanelContext {
  readonly typeIri: string;
  readonly session: OntologySession;
  readonly registry: ControlRegistry;
  /** Set only when the transition is a named node; most are blank nodes, so prefer `transition`. */
  readonly transitionIri?: string;
  /**
   * The selected transition as read at the current session revision; `transition.node` is what rdf's
   * `setTransitionTrigger` / `setGuard` take. It is only valid for that revision: the editor passes a fresh
   * context after every revision bump.
   */
  readonly transition?: TransitionView;
}

/**
 * A panel another feature contributes to the ontology editor. The component must declare
 * `context = input.required<OntologyEditorPanelContext>()`.
 */
export interface OntologyEditorPanel {
  id: string;
  slot: 'transition-trigger' | 'transition-guard' | 'type-extra';
  order?: number;
  component: Type<unknown>;
}

/** Multi-provider; empty unless another feature (e.g. automation) contributes panels. */
export const ONTOLOGY_EDITOR_PANELS = new InjectionToken<readonly OntologyEditorPanel[]>(
  'Ontology editor panels',
  { providedIn: 'root', factory: () => [] },
);

export function panelsFor(
  all: readonly OntologyEditorPanel[],
  slot: OntologyEditorPanel['slot'],
): OntologyEditorPanel[] {
  return all
    .filter((p) => p.slot === slot)
    .sort(
      (a, b) =>
        (a.order ?? Number.POSITIVE_INFINITY) - (b.order ?? Number.POSITIVE_INFINITY) ||
        (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
    );
}

export function provideOntologyEditorPanel(panel: OntologyEditorPanel): Provider {
  return { provide: ONTOLOGY_EDITOR_PANELS, multi: true, useValue: panel };
}
