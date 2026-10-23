import { Routes } from '@angular/router';

import { SqStateMachineView } from './state-machine-view/state-machine-view';
import { SqAutomationTypeList } from './state-machine-view/type-list';

/** Mounted by the shell under `/automation`: the type picker, then one type's state machine. */
export const automationRoutes: Routes = [
  { path: '', component: SqAutomationTypeList },
  { path: ':type', component: SqStateMachineView },
];
