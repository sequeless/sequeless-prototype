export type PayloadResult =
  | { ok: true; value: Record<string, unknown> | undefined }
  | { ok: false; error: string };

export function parsePayload(text: string): PayloadResult {
  if (!text.trim()) {
    return { ok: true, value: undefined };
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, error: 'The payload is not valid JSON.' };
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return { ok: false, error: 'The payload must be a JSON object.' };
  }
  return { ok: true, value: parsed as Record<string, unknown> };
}
