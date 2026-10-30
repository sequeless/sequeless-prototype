import type { Provider } from '@angular/core';
import { provideOntologyEditorPanel } from '@sequeless/ontology';

import { SqGuardEditor } from './guard-editor';
import { SqTriggerEditor } from './trigger-editor';

/** Contributes the trigger and guard editors to the ontology editor's transition slots (D4). */
export function provideAutomationEditors(): Provider[] {
  return [
    provideOntologyEditorPanel({ id: 'automation.trigger', slot: 'transition-trigger', component: SqTriggerEditor }),
    provideOntologyEditorPanel({ id: 'automation.guard', slot: 'transition-guard', component: SqGuardEditor }),
  ];
}
