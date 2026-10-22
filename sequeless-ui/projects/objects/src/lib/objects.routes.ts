import { Routes } from '@angular/router';

/** Mounted by the shell under `/objects`. `:type/new` must precede `:type/:id`. */
export const objectsRoutes: Routes = [
  {
    path: ':type/new',
    loadComponent: () => import('./create/object-create').then((m) => m.SqObjectCreate),
  },
  {
    path: ':type/:id',
    loadComponent: () => import('./detail/object-detail.component').then((m) => m.SqObjectDetail),
  },
  {
    path: ':type',
    loadComponent: () => import('./browse/object-browse').then((m) => m.SqObjectBrowse),
  },
];
