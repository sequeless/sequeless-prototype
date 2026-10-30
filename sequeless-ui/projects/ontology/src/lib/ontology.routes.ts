import { Routes } from '@angular/router';

import { ImportFlow } from './import-result/import-flow';
import { ControlRegistry } from './issues/control-registry';
import { OntologySession } from './session/ontology-session';

/**
 * Mounted by the shell under `/ontology`. One session, import flow and control registry serve every child, so
 * edits survive moving between the list, a type and the raw Turtle page. `raw` must precede `:type`.
 */
export const ontologyRoutes: Routes = [
  {
    path: '',
    providers: [OntologySession, ControlRegistry, ImportFlow],
    children: [
      {
        path: '',
        loadComponent: () => import('./type-list/type-list').then((m) => m.SqOntologyTypeList),
      },
      {
        path: 'raw',
        loadComponent: () => import('./raw/raw-turtle-page').then((m) => m.SqRawTurtlePage),
      },
      {
        path: ':type',
        loadComponent: () => import('./type-editor/type-editor').then((m) => m.SqTypeEditor),
      },
    ],
  },
];
