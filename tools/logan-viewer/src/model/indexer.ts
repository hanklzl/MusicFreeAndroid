import type {
  EventIndex,
  LogCategory,
  ParsedEvent,
  RawLogEvent,
  SessionSummary,
} from './types.js';

export interface BuildIndexInput {
  raw: RawLogEvent[];
  sourceFile: string;
  blockIndex: number;
}

export function parseTimestamp(iso: string): number {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) {
    return 0;
  }
  return ms;
}

function makeId(sourceFile: string, blockIndex: number, lineIndex: number): string {
  return `${sourceFile}#${blockIndex}#${lineIndex}`;
}

export function toParsedEvent(
  raw: RawLogEvent,
  sourceFile: string,
  blockIndex: number,
  lineIndex: number,
): ParsedEvent {
  const timestampMs = parseTimestamp(raw.timestamp);
  const groupId = raw.traceId ? `${raw.sessionId}::${raw.traceId}` : null;
  return {
    ...raw,
    id: makeId(sourceFile, blockIndex, lineIndex),
    timestampMs,
    groupId,
    sourceFile,
  };
}

export interface IndexInput {
  events: ParsedEvent[];
}

export function buildIndex(events: ParsedEvent[]): EventIndex {
  const sorted = [...events].sort((a, b) => a.timestampMs - b.timestampMs);
  const bySession = new Map<string, ParsedEvent[]>();
  const byTrace = new Map<string, ParsedEvent[]>();
  const byCategory = new Map<LogCategory, number>();

  for (const event of sorted) {
    const sessionBucket = bySession.get(event.sessionId);
    if (sessionBucket) {
      sessionBucket.push(event);
    } else {
      bySession.set(event.sessionId, [event]);
    }

    if (event.groupId !== null) {
      const traceBucket = byTrace.get(event.groupId);
      if (traceBucket) {
        traceBucket.push(event);
      } else {
        byTrace.set(event.groupId, [event]);
      }
    }

    byCategory.set(event.category, (byCategory.get(event.category) ?? 0) + 1);
  }

  const sessionSummaries: SessionSummary[] = [];
  for (const [sessionId, list] of bySession.entries()) {
    const startMs = list[0]!.timestampMs;
    const endMs = list[list.length - 1]!.timestampMs;
    let errorCount = 0;
    const categorySet = new Set<LogCategory>();
    for (const event of list) {
      if (event.level === 'error') errorCount += 1;
      categorySet.add(event.category);
    }
    sessionSummaries.push({
      sessionId,
      startMs,
      endMs,
      eventCount: list.length,
      errorCount,
      categories: [...categorySet].sort(),
    });
  }
  sessionSummaries.sort((a, b) => a.startMs - b.startMs);

  return {
    events: sorted,
    bySession,
    byTrace,
    byCategory,
    sessionSummaries,
  };
}
