import { Routes } from '@angular/router';

import { provideObjectsRelationshipLookup } from './relationship/objects-relationship-lookup';

/**
 * Mounted by the shell under `/objects`. The wrapper route provides the relationship lookup to
 * every child. `:type/new` must precede `:type/:id`.
 */
export const objectsRoutes: Routes = [
  {
    path: '',
    providers: [provideObjectsRelationshipLookup()],
    children: [
      {
        path: '',
        loadComponent: () => import('./type-list/type-list').then((m) => m.SqTypeList),
      },
      {
        path: ':type/new',
        loadComponent: () => import('./create/object-create').then((m) => m.SqObjectCreate),
      },
      {
        path: ':type/:id',
        loadComponent: () =>
          import('./detail/object-detail.component').then((m) => m.SqObjectDetail),
      },
      {
        path: ':type',
        loadComponent: () => import('./browse/object-browse').then((m) => m.SqObjectBrowse),
      },
    ],
  },
];
