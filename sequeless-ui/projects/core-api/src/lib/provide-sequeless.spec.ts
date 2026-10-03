/*
 * provideSequeless() mirrors the backend's `sequeless.<port>.adapter` swap:
 * every injectable slot (API base URL, HTTP interceptors, RDF store factory,
 * Turtle editor, graph layout, error mapper, field renderers, clock, feature
 * flags) ships with a default, and any of them can be replaced through
 * SequelessConfig without touching library code.
 */
import { HttpInterceptorFn } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { RdfStore } from '@sequeless/rdf';

import { provideDefaultApiBaseUrl, provideSequeless } from './provide-sequeless';
import {
  FIELD_RENDERERS,
  RDF_STORE_FACTORY,
  SEQUELESS_API_BASE_URL,
  SEQUELESS_CLOCK,
  SEQUELESS_ERROR_MAPPER,
  SEQUELESS_FEATURES,
  SEQUELESS_HTTP_INTERCEPTORS,
} from './tokens';

@Component({ selector: 'sq-test-widget', template: '' })
class CustomWidget {}

describe('provideSequeless', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('provides "/api" as the default SEQUELESS_API_BASE_URL when no config is given', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });

    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('/api');
  });

  it('exports provideDefaultApiBaseUrl as a standalone default helper that provides "/api"', () => {
    TestBed.configureTestingModule({ providers: [provideDefaultApiBaseUrl()] });

    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('/api');
  });

  it('overrides SEQUELESS_API_BASE_URL with config.apiBaseUrl', () => {
    TestBed.configureTestingModule({
      providers: [provideSequeless({ apiBaseUrl: 'https://api.example.test/v1' })],
    });

    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('https://api.example.test/v1');
  });

  it('defaults SEQUELESS_FEATURES.serverSideOntologyDiff to false (client-side diff until the Phase 7 server dry-run exists)', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });

    expect(TestBed.inject(SEQUELESS_FEATURES).serverSideOntologyDiff).toBe(false);
  });

  it('merges a partial features override over the defaults, keeping unspecified flags at their default values', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    const defaults = { ...TestBed.inject(SEQUELESS_FEATURES) };
    TestBed.resetTestingModule();

    TestBed.configureTestingModule({
      providers: [provideSequeless({ features: { serverSideOntologyDiff: true } })],
    });
    const features = TestBed.inject(SEQUELESS_FEATURES);

    expect(features.serverSideOntologyDiff).toBe(true);
    expect(features).toEqual({ ...defaults, serverSideOntologyDiff: true });
  });

  it('provides a default SEQUELESS_CLOCK returning a Date, and uses config.clock when given', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    expect(TestBed.inject(SEQUELESS_CLOCK)()).toBeInstanceOf(Date);
    TestBed.resetTestingModule();

    const fixed = new Date('2026-01-02T03:04:05.000Z');
    TestBed.configureTestingModule({
      providers: [provideSequeless({ clock: () => fixed })],
    });
    const now = TestBed.inject(SEQUELESS_CLOCK)();

    expect(now).toBeInstanceOf(Date);
    expect(now.getTime()).toBe(fixed.getTime());
  });

  it('provides an empty SEQUELESS_HTTP_INTERCEPTORS array by default and includes config.authInterceptor when configured', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    const defaults = TestBed.inject(SEQUELESS_HTTP_INTERCEPTORS);
    expect(Array.isArray(defaults)).toBe(true);
    expect(defaults).toHaveLength(0);
    TestBed.resetTestingModule();

    const authInterceptor: HttpInterceptorFn = (req, next) => next(req);
    TestBed.configureTestingModule({
      providers: [provideSequeless({ authInterceptor })],
    });

    expect(TestBed.inject(SEQUELESS_HTTP_INTERCEPTORS)).toContain(authInterceptor);
  });

  it('merges config.fieldRenderers per key over the default FIELD_RENDERERS map', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    const defaults = { ...TestBed.inject(FIELD_RENDERERS) };
    expect(typeof defaults).toBe('object');
    TestBed.resetTestingModule();

    TestBed.configureTestingModule({
      providers: [provideSequeless({ fieldRenderers: { 'custom:widget': CustomWidget } })],
    });
    const renderers = TestBed.inject(FIELD_RENDERERS);

    expect(typeof renderers).toBe('object');
    expect(renderers).not.toBeNull();
    expect(renderers['custom:widget']).toBe(CustomWidget);
    for (const key of Object.keys(defaults)) {
      expect(renderers[key]).toBe(defaults[key]);
    }
  });

  it('always provides SEQUELESS_ERROR_MAPPER as a function, even with no config', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });

    expect(typeof TestBed.inject(SEQUELESS_ERROR_MAPPER)).toBe('function');
  });

  it('provides a default RDF_STORE_FACTORY that produces an empty RdfStore when no override is given', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    const factory = TestBed.inject(RDF_STORE_FACTORY);
    const store = factory();

    expect(store.size).toBe(0);
    expect(typeof store.match).toBe('function');
  });

  it('calls the default RDF_STORE_FACTORY to produce a fresh store instance on every call', () => {
    TestBed.configureTestingModule({ providers: [provideSequeless()] });
    const factory = TestBed.inject(RDF_STORE_FACTORY);

    const first = factory();
    const second = factory();

    expect(first).not.toBe(second);
  });

  it('uses config.rdfStoreFactory for RDF_STORE_FACTORY instead of the default when given', () => {
    const customStore = { size: 0, match: () => [] } as unknown as RdfStore;
    const rdfStoreFactory = () => customStore;
    TestBed.configureTestingModule({
      providers: [provideSequeless({ rdfStoreFactory })],
    });

    const factory = TestBed.inject(RDF_STORE_FACTORY);

    expect(factory).toBe(rdfStoreFactory);
    expect(factory()).toBe(customStore);
  });

  it('does not leak configuration between separate TestBeds', () => {
    const authInterceptor: HttpInterceptorFn = (req, next) => next(req);
    TestBed.configureTestingModule({
      providers: [
        provideSequeless({
          apiBaseUrl: 'https://api.example.test/v1',
          authInterceptor,
          features: { serverSideOntologyDiff: true },
          fieldRenderers: { 'custom:widget': CustomWidget },
        }),
      ],
    });
    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('https://api.example.test/v1');
    TestBed.resetTestingModule();

    TestBed.configureTestingModule({ providers: [provideSequeless()] });

    expect(TestBed.inject(SEQUELESS_API_BASE_URL)).toBe('/api');
    expect(TestBed.inject(SEQUELESS_FEATURES).serverSideOntologyDiff).toBe(false);
    expect(TestBed.inject(SEQUELESS_HTTP_INTERCEPTORS)).toHaveLength(0);
    expect(TestBed.inject(FIELD_RENDERERS)['custom:widget']).toBeUndefined();
  });
});
