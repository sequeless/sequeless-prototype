/*
 * Contract [CORE-EXPORTS]: public-api.ts re-exports every runtime symbol from
 * ./lib/query/browse-query, ./lib/query/query-capabilities, ./lib/http/etag-registry,
 * ./lib/http/etag.interceptor, ./lib/http/provide-sequeless-http,
 * ./lib/clients/api-error-operator, ./lib/clients/types-client and
 * ./lib/clients/objects-client, in addition to the barrel's existing exports.
 */
import * as api from './public-api';

describe('public-api', () => {
  it('re-exports the pre-existing provideSequeless, toApiError and SEQUELESS_API_BASE_URL symbols', () => {
    expect(api.provideSequeless).toBeDefined();
    expect(api.toApiError).toBeDefined();
    expect(api.SEQUELESS_API_BASE_URL).toBeDefined();
  });

  it('re-exports isAttributeProperty from models', () => {
    expect(api.isAttributeProperty).toBeDefined();
    expect(typeof api.isAttributeProperty).toBe('function');
  });

  it('re-exports toHttpParams, fromQueryParamMap and MAX_PAGE_SIZE from browse-query', () => {
    expect(typeof api.toHttpParams).toBe('function');
    expect(typeof api.fromQueryParamMap).toBe('function');
    expect(api.MAX_PAGE_SIZE).toBe(200);
  });

  it('re-exports queryCapabilities from query-capabilities', () => {
    expect(typeof api.queryCapabilities).toBe('function');
  });

  it('re-exports ETagRegistry and etagInterceptor from the http layer', () => {
    expect(api.ETagRegistry).toBeDefined();
    expect(typeof api.etagInterceptor).toBe('function');
  });

  it('re-exports provideSequelessHttp and sequelessInterceptors from provide-sequeless-http', () => {
    expect(typeof api.provideSequelessHttp).toBe('function');
    expect(typeof api.sequelessInterceptors).toBe('function');
  });

  it('re-exports rethrowAsApiError from api-error-operator', () => {
    expect(typeof api.rethrowAsApiError).toBe('function');
  });

  it('re-exports TypesClient and ObjectsClient from the clients layer', () => {
    expect(api.TypesClient).toBeDefined();
    expect(api.ObjectsClient).toBeDefined();
  });

  it('exposes every expected runtime symbol with no undefined entries', () => {
    const expectedNames = [
      'provideSequeless',
      'toApiError',
      'isAttributeProperty',
      'toHttpParams',
      'fromQueryParamMap',
      'MAX_PAGE_SIZE',
      'queryCapabilities',
      'ETagRegistry',
      'etagInterceptor',
      'provideSequelessHttp',
      'sequelessInterceptors',
      'rethrowAsApiError',
      'TypesClient',
      'ObjectsClient',
      'SEQUELESS_API_BASE_URL',
    ] as const;

    for (const name of expectedNames) {
      expect((api as Record<string, unknown>)[name]).toBeDefined();
    }
  });
});
