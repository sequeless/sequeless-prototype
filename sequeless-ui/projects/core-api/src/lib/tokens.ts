/*
 * Dependency-injection tokens for the sequeless-core-api slots.
 * Each token mirrors a swappable adapter slot on the backend, where the
 * runtime exposes sequeless.<port>.adapter properties; on the frontend the
 * InjectionToken is the swap point instead. The editor, layout, and field
 * renderer slots are typed by ui-contracts.ts, which ui-kit implements.
 */
import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken } from '@angular/core';
import type { RdfStore } from '@sequeless/rdf';

import type { FieldRendererMap, GraphLayout, TextEditorFactory } from './ui-contracts';

export const SEQUELESS_API_BASE_URL = new InjectionToken<string>('Sequeless api base URL');

export const SEQUELESS_HTTP_INTERCEPTORS = new InjectionToken<readonly HttpInterceptorFn[]>(
  'Sequeless http interceptors',
);

export const RDF_STORE_FACTORY = new InjectionToken<() => RdfStore>(
  'Sequeless RDF store factory',
);

export const TURTLE_EDITOR = new InjectionToken<TextEditorFactory | null>(
  'Sequeless Turtle editor',
);

export const GRAPH_LAYOUT = new InjectionToken<GraphLayout | null>('Sequeless graph layout');

export const SEQUELESS_ERROR_MAPPER = new InjectionToken<(error: unknown) => unknown>(
  'Sequeless error mapper',
);

export const FIELD_RENDERERS = new InjectionToken<FieldRendererMap>(
  'Sequeless field renderers',
);

export const SEQUELESS_CLOCK = new InjectionToken<() => Date>('Sequeless clock');

export const SEQUELESS_FEATURES = new InjectionToken<Readonly<SequelessFeatures>>(
  'Sequeless feature flags',
);

export interface SequelessFeatures {
  serverSideOntologyDiff: boolean;
}
