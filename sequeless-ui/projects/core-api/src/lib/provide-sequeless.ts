/*
 * provideSequeless() wires every swappable slot of sequeless-core-api.
 * It mirrors the backend pattern where adapters are swapped via
 * sequeless.<port>.adapter properties: each token below is a slot that
 * ships with a default and can be replaced through SequelessConfig.
 * Defaults are produced fresh on every provideSequeless() call so that no
 * state ever leaks between separate provider setups.
 */
import { HttpInterceptorFn } from '@angular/common/http';
import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { N3RdfStore, type RdfStore } from '@sequeless/rdf';

import {
  FIELD_RENDERERS,
  GRAPH_LAYOUT,
  RDF_STORE_FACTORY,
  SequelessFeatures,
  SEQUELESS_API_BASE_URL,
  SEQUELESS_CLOCK,
  SEQUELESS_ERROR_MAPPER,
  SEQUELESS_FEATURES,
  SEQUELESS_HTTP_INTERCEPTORS,
  TURTLE_EDITOR,
} from './tokens';

export interface SequelessConfig {
  apiBaseUrl?: string;
  authInterceptor?: HttpInterceptorFn;
  rdfStoreFactory?: () => RdfStore;
  turtleEditor?: unknown;
  graphLayout?: unknown;
  errorMapper?: (error: unknown) => unknown;
  fieldRenderers?: Partial<Record<string, unknown>>;
  clock?: () => Date;
  features?: Partial<SequelessFeatures>;
}

type DefaultFieldRenderers = Readonly<Record<string, unknown>>;

const defaultFieldRenderers: DefaultFieldRenderers = Object.freeze({});

const defaultFeatures: Readonly<SequelessFeatures> = Object.freeze({
  serverSideOntologyDiff: false,
});

function defaultErrorMapper(error: unknown): unknown {
  return error;
}

function defaultRdfStoreFactory(): RdfStore {
  return new N3RdfStore();
}

export function provideDefaultApiBaseUrl(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: SEQUELESS_API_BASE_URL, useValue: '/api' },
  ]);
}

export function provideDefaultClock(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: SEQUELESS_CLOCK, useValue: () => new Date() },
  ]);
}

export function provideDefaultFeatures(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: SEQUELESS_FEATURES, useValue: { ...defaultFeatures } as Readonly<SequelessFeatures> },
  ]);
}

export function provideDefaultErrorMapper(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: SEQUELESS_ERROR_MAPPER, useValue: defaultErrorMapper },
  ]);
}

export function provideDefaultFieldRenderers(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: FIELD_RENDERERS, useValue: { ...defaultFieldRenderers } },
  ]);
}

export function provideSequeless(config: SequelessConfig = {}): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: SEQUELESS_API_BASE_URL,
      useValue: config.apiBaseUrl ?? '/api',
    },
    {
      provide: SEQUELESS_HTTP_INTERCEPTORS,
      useValue: config.authInterceptor ? [config.authInterceptor] : [],
    },
    {
      provide: RDF_STORE_FACTORY,
      useValue: config.rdfStoreFactory ?? defaultRdfStoreFactory,
    },
    {
      provide: TURTLE_EDITOR,
      useValue: config.turtleEditor ?? null,
    },
    {
      provide: GRAPH_LAYOUT,
      useValue: config.graphLayout ?? null,
    },
    {
      provide: SEQUELESS_ERROR_MAPPER,
      useValue: config.errorMapper ?? defaultErrorMapper,
    },
    {
      provide: FIELD_RENDERERS,
      useValue: Object.freeze({ ...defaultFieldRenderers, ...config.fieldRenderers }),
    },
    {
      provide: SEQUELESS_CLOCK,
      useValue: config.clock ?? (() => new Date()),
    },
    {
      provide: SEQUELESS_FEATURES,
      useValue: Object.freeze({ ...defaultFeatures, ...config.features }),
    },
  ]);
}
