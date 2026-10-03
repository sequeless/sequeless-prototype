/*
 * Dependency-injection tokens for the sequeless-core-api slots.
 * Each token mirrors a swappable adapter slot on the backend, where the
 * runtime exposes sequeless.<port>.adapter properties; on the frontend the
 * InjectionToken is the swap point instead. Slots that are not yet designed
 * (editor, layout) are typed as unknown on purpose.
 */
import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken } from '@angular/core';
import type { RdfStore } from '@sequeless/rdf';

export const SEQUELESS_API_BASE_URL = new InjectionToken<string>('Sequeless api base URL');

export const SEQUELESS_HTTP_INTERCEPTORS = new InjectionToken<readonly HttpInterceptorFn[]>(
  'Sequeless http interceptors',
);

export const RDF_STORE_FACTORY = new InjectionToken<() => RdfStore>(
  'Sequeless RDF store factory',
);

export const TURTLE_EDITOR = new InjectionToken<unknown>('Sequeless Turtle editor');

export const GRAPH_LAYOUT = new InjectionToken<unknown>('Sequeless graph layout');

export const SEQUELESS_ERROR_MAPPER = new InjectionToken<(error: unknown) => unknown>(
  'Sequeless error mapper',
);

export const FIELD_RENDERERS = new InjectionToken<Readonly<Record<string, unknown>>>(
  'Sequeless field renderers',
);

export const SEQUELESS_CLOCK = new InjectionToken<() => Date>('Sequeless clock');

export const SEQUELESS_FEATURES = new InjectionToken<Readonly<SequelessFeatures>>(
  'Sequeless feature flags',
);

export interface SequelessFeatures {
  serverSideOntologyDiff: boolean;
}
