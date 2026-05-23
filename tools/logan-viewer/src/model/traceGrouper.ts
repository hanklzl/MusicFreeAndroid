import type { ParsedEvent, TraceGroup } from './types.js';

export type TimelineRow =
  | { kind: 'group'; group: TraceGroup }
  | { kind: 'single'; event: ParsedEvent };

/**
 * 把单个 session 的事件序列按相邻同 traceId 合并为操作组。
 * 无 traceId 的事件保持为独立 single 行（不合并相邻散件）。
 * 同一 traceId 的事件如果中间被其它 traceId 事件打断，会被切成多个相邻组。
 */
export function groupBySession(sessionId: string, events: ParsedEvent[]): TimelineRow[] {
  const rows: TimelineRow[] = [];
  let currentGroup: ParsedEvent[] | null = null;
  let currentTraceId: string | null = null;

  const flush = () => {
    if (currentGroup !== null && currentTraceId !== null) {
      rows.push({ kind: 'group', group: buildTraceGroup(sessionId, currentTraceId, currentGroup) });
    }
    currentGroup = null;
    currentTraceId = null;
  };

  for (const event of events) {
    if (event.sessionId !== sessionId) continue;
    const traceId = event.traceId ?? null;
    if (traceId === null) {
      flush();
      rows.push({ kind: 'single', event });
      continue;
    }
    if (traceId !== currentTraceId) {
      flush();
      currentGroup = [event];
      currentTraceId = traceId;
    } else {
      currentGroup!.push(event);
    }
  }
  flush();

  return rows;
}

function buildTraceGroup(sessionId: string, traceId: string, events: ParsedEvent[]): TraceGroup {
  const head = events[0]!;
  const tail = events[events.length - 1]!;
  const startMs = head.timestampMs;
  const endMs = tail.timestampMs;
  const hasError = events.some((e) => e.level === 'error' || e.errorClass != null);
  return {
    kind: 'group',
    id: `${sessionId}::${traceId}`,
    sessionId,
    traceId,
    category: head.category,
    headEvent: head,
    tailEvent: tail,
    startMs,
    endMs,
    durationMs: Math.max(0, endMs - startMs),
    events,
    hasError,
    result: tail.result ?? null,
  };
}
