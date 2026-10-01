import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: "", redirectTo: "objects", pathMatch: "full" },
  {
    path: "objects",
    loadChildren: () => import("@sequeless/objects").then((m) => m.objectsRoutes),
  },
  {
    path: "ontology",
    loadChildren: () => import("@sequeless/ontology").then((m) => m.ontologyRoutes),
  },
  {
    path: "automation",
    loadChildren: () => import("@sequeless/automation").then((m) => m.automationRoutes),
  },
];
