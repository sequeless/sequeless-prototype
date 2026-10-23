import { Routes } from '@angular/router';

/**
 * Automation extends the ontology editor and the object detail view. It is composed in lazily, on the routes
 * that host those extension points, so none of it is in the initial bundle.
 */
export const routes: Routes = [
  { path: "", redirectTo: "objects", pathMatch: "full" },
  {
    path: "objects",
    loadChildren: async () => {
      const [{ objectsRoutes }, { provideAutomationSignalsPanel }] = await Promise.all([
        import("@sequeless/objects"),
        import("@sequeless/automation"),
      ]);
      return [{ path: "", providers: [provideAutomationSignalsPanel()], children: objectsRoutes }];
    },
  },
  {
    path: "ontology",
    loadChildren: async () => {
      const [{ ontologyRoutes }, { provideAutomationEditors }] = await Promise.all([
        import("@sequeless/ontology"),
        import("@sequeless/automation"),
      ]);
      return [{ path: "", providers: [provideAutomationEditors()], children: ontologyRoutes }];
    },
  },
  {
    path: "automation",
    loadChildren: () => import("@sequeless/automation").then((m) => m.automationRoutes),
  },
];
