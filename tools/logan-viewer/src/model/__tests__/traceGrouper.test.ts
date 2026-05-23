import { describe, expect, it } from 'vitest';

import { toParsedEvent } from '../indexer.js';
import { groupBySession } from '../traceGrouper.js';
import type { RawLogEvent } from '../types.js';

function ev(overrides: Partial<RawLogEvent> = {}): RawLogEvent {
  return {
    level: 'trace',
    category: 'APP',
    event: 'evt',
    timestamp: '2026-05-23T12:00:00.000+08:00',
    sessionId: 's1',
    ...overrides,
  };
}

function parsedSeq(...raws: RawLogEvent[]) {
  return raws.map((r, i) => toParsedEvent(r, 'logan/f', 0, i));
}

describe('groupBySession', () => {
  it('returns singles for events without traceId', () => {
    const rows = groupBySession('s1', parsedSeq(ev(), ev({ event: 'app_resume' })));
    expect(rows).toHaveLength(2);
    expect(rows.every((r) => r.kind === 'single')).toBe(true);
  });

  it('merges adjacent same-trace events into one group', () => {
    const rows = groupBySession(
      's1',
      parsedSeq(
        ev({ traceId: 't1', event: 'start', timestamp: '2026-05-23T12:00:01.000+08:00' }),
        ev({ traceId: 't1', event: 'progress', timestamp: '2026-05-23T12:00:01.500+08:00' }),
        ev({
          traceId: 't1',
          event: 'done',
          timestamp: '2026-05-23T12:00:02.000+08:00',
          result: 'done',
        }),
      ),
    );
    expect(rows).toHaveLength(1);
    expect(rows[0]!.kind).toBe('group');
    if (rows[0]!.kind !== 'group') return;
    expect(rows[0]!.group.events).toHaveLength(3);
    expect(rows[0]!.group.durationMs).toBe(1000);
    expect(rows[0]!.group.result).toBe('done');
    expect(rows[0]!.group.hasError).toBe(false);
  });

  it('splits a trace if interrupted by a different trace', () => {
    const rows = groupBySession(
      's1',
      parsedSeq(
        ev({ traceId: 't1' }),
        ev({ traceId: 't2' }),
        ev({ traceId: 't1' }),
      ),
    );
    expect(rows.map((r) => r.kind)).toEqual(['group', 'group', 'group']);
  });

  it('marks hasError when any event has level=error', () => {
    const rows = groupBySession(
      's1',
      parsedSeq(
        ev({ traceId: 't1' }),
        ev({ traceId: 't1', level: 'error', event: 'oops' }),
      ),
    );
    expect(rows[0]!.kind).toBe('group');
    if (rows[0]!.kind !== 'group') return;
    expect(rows[0]!.group.hasError).toBe(true);
  });

  it('marks hasError when any event has errorClass', () => {
    const rows = groupBySession(
      's1',
      parsedSeq(
        ev({ traceId: 't1' }),
        ev({ traceId: 't1', errorClass: 'java.io.IOException' }),
      ),
    );
    expect(rows[0]!.kind).toBe('group');
    if (rows[0]!.kind !== 'group') return;
    expect(rows[0]!.group.hasError).toBe(true);
  });

  it('ignores events from other sessions', () => {
    const rows = groupBySession(
      's1',
      parsedSeq(ev({ sessionId: 's2', traceId: 't1' }), ev({ traceId: 't1' })),
    );
    expect(rows).toHaveLength(1);
    expect(rows[0]!.kind).toBe('group');
  });
});
