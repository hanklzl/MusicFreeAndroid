import { describe, expect, it } from 'vitest';

import { buildIndex, parseTimestamp, toParsedEvent } from '../indexer.js';
import type { RawLogEvent } from '../types.js';

function rawEvent(overrides: Partial<RawLogEvent> = {}): RawLogEvent {
  return {
    level: 'info',
    category: 'APP',
    event: 'app_start',
    timestamp: '2026-05-23T12:00:00.000+08:00',
    sessionId: 'session-A',
    ...overrides,
  };
}

describe('parseTimestamp', () => {
  it('parses ISO_OFFSET_DATE_TIME', () => {
    expect(parseTimestamp('2026-05-23T12:00:00.000+08:00')).toBe(
      Date.UTC(2026, 4, 23, 4, 0, 0, 0),
    );
  });

  it('returns 0 on unparseable', () => {
    expect(parseTimestamp('not a date')).toBe(0);
  });
});

describe('toParsedEvent', () => {
  it('attaches id / timestampMs / groupId / sourceFile', () => {
    const raw = rawEvent({ traceId: 'trace-x' });
    const parsed = toParsedEvent(raw, 'logan/file-1', 0, 3);
    expect(parsed.id).toBe('logan/file-1#0#3');
    expect(parsed.timestampMs).toBeGreaterThan(0);
    expect(parsed.groupId).toBe('session-A::trace-x');
    expect(parsed.sourceFile).toBe('logan/file-1');
  });

  it('groupId is null when traceId is absent', () => {
    const parsed = toParsedEvent(rawEvent(), 'logan/file-1', 0, 0);
    expect(parsed.groupId).toBeNull();
  });
});

describe('buildIndex', () => {
  it('sorts events by timestamp and buckets by session/trace/category', () => {
    const raws: RawLogEvent[] = [
      rawEvent({ sessionId: 's1', timestamp: '2026-05-23T12:00:02.000+08:00' }),
      rawEvent({
        sessionId: 's1',
        category: 'PLUGIN',
        traceId: 't1',
        timestamp: '2026-05-23T12:00:01.000+08:00',
      }),
      rawEvent({ sessionId: 's2', timestamp: '2026-05-23T12:00:03.000+08:00' }),
      rawEvent({
        sessionId: 's1',
        category: 'PLUGIN',
        traceId: 't1',
        timestamp: '2026-05-23T12:00:01.500+08:00',
      }),
    ];
    const parsed = raws.map((r, i) => toParsedEvent(r, 'logan/f', 0, i));
    const index = buildIndex(parsed);

    expect(index.events.map((e) => e.timestampMs)).toEqual(
      [...index.events.map((e) => e.timestampMs)].sort((a, b) => a - b),
    );
    expect(index.bySession.get('s1')).toHaveLength(3);
    expect(index.bySession.get('s2')).toHaveLength(1);
    expect(index.byTrace.get('s1::t1')).toHaveLength(2);
    expect(index.byCategory.get('APP')).toBe(2);
    expect(index.byCategory.get('PLUGIN')).toBe(2);
  });

  it('summarizes sessions with start/end and error counts', () => {
    const raws: RawLogEvent[] = [
      rawEvent({ sessionId: 's1', timestamp: '2026-05-23T12:00:01.000+08:00' }),
      rawEvent({
        sessionId: 's1',
        level: 'error',
        timestamp: '2026-05-23T12:00:05.000+08:00',
      }),
      rawEvent({
        sessionId: 's1',
        category: 'PLAYER',
        timestamp: '2026-05-23T12:00:03.000+08:00',
      }),
    ];
    const parsed = raws.map((r, i) => toParsedEvent(r, 'logan/f', 0, i));
    const index = buildIndex(parsed);
    const summary = index.sessionSummaries.find((s) => s.sessionId === 's1')!;
    expect(summary.eventCount).toBe(3);
    expect(summary.errorCount).toBe(1);
    expect(summary.startMs).toBeLessThan(summary.endMs);
    expect(summary.categories).toEqual(['APP', 'PLAYER']);
  });
});
