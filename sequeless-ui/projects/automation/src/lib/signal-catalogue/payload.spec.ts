/**
 * Contract under test (signal-catalogue/payload.ts): `parsePayload(text)` turns the signal payload editor's
 * text into the request body. Blank text is a valid "no payload" (`value: undefined`); otherwise the text must
 * be a JSON object — arrays, primitives, null and malformed JSON are rejected with an error message.
 */
import { parsePayload } from './payload';

describe('parsePayload', () => {
  it('treats blank text as no payload', () => {
    expect(parsePayload('')).toEqual({ ok: true, value: undefined });
    expect(parsePayload('  \n ')).toEqual({ ok: true, value: undefined });
  });

  it('accepts a JSON object', () => {
    expect(parsePayload('{"a": 1, "b": {"c": true}}')).toEqual({ ok: true, value: { a: 1, b: { c: true } } });
  });

  it.each(['[1]', '3', '"x"', 'null', 'true'])('rejects %s as not an object', (text) => {
    expect(parsePayload(text)).toEqual({ ok: false, error: 'The payload must be a JSON object.' });
  });

  it('rejects malformed JSON', () => {
    const r = parsePayload('{oops');
    expect(r.ok).toBe(false);
    expect(r.ok === false && r.error).toMatch(/not valid JSON/i);
  });
});
