import type { TriggerKind, TransitionSummaryResponse } from '@sequeless/core-api';

export interface EdgeStyle {
  dash: string | null;
  double: boolean;
  glyph: 'clock' | 'antenna' | null;
}

export function edgeStyle(trigger: TriggerKind): EdgeStyle {
  switch (trigger) {
    case 'USER_ACTION':
      return { dash: null, double: false, glyph: null };
    case 'ON_CHANGE':
      return { dash: '6 4', double: false, glyph: null };
    case 'TIMER':
      return { dash: '2 4', double: false, glyph: 'clock' };
    case 'EXTERNAL_SIGNAL':
      return { dash: null, double: true, glyph: 'antenna' };
  }
}

export function edgeLabel(t: TransitionSummaryResponse): string {
  let detail: string | null = null;
  switch (t.trigger) {
    case 'USER_ACTION':
      detail = null;
      break;
    case 'TIMER':
      detail = t.after ?? null;
      break;
    case 'EXTERNAL_SIGNAL':
      detail = t.signalName ?? null;
      break;
    case 'ON_CHANGE':
      detail = t.watch.length > 0 ? t.watch.join(', ') : null;
      break;
  }
  if (detail !== null && detail.length > 0) {
    return `${t.name} (${detail})`;
  }
  return t.name;
}

export function pointsToPath(points: readonly { x: number; y: number }[]): string {
  if (points.length === 0) {
    return '';
  }
  const parts: string[] = [`M ${points[0].x} ${points[0].y}`];
  for (let i = 1; i < points.length; i++) {
    parts.push(`L ${points[i].x} ${points[i].y}`);
  }
  return parts.join(' ');
}
