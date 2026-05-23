import { useVirtualizer } from '@tanstack/react-virtual';
import React from 'react';

import { matchActions } from '../actions/matchActions.js';
import type { TimelineItem } from '../actions/types.js';
import type { ParsedEvent } from '../model/types.js';
import { useViewer } from '../state/store.js';
import { ActionCard } from './ActionCard.js';

type FlatRow =
  | { kind: 'action'; item: Extract<TimelineItem, { kind: 'action' }>; expanded: boolean }
  | { kind: 'raw'; event: ParsedEvent };

const ROW_HEIGHT = 22;
const ACTION_CARD_HEIGHT = 64;

function formatTimestamp(ms: number): string {
  if (!ms) return '--:--:--';
  const d = new Date(ms);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  const sss = String(d.getMilliseconds()).padStart(3, '0');
  return `${hh}:${mm}:${ss}.${sss}`;
}

function summarizeFields(event: ParsedEvent): string {
  if (!event.fields || Object.keys(event.fields).length === 0) {
    if (event.errorClass) return `${event.errorClass}: ${event.errorMessage ?? ''}`;
    return '';
  }
  const parts: string[] = [];
  for (const [key, value] of Object.entries(event.fields)) {
    if (value == null) continue;
    let str: string;
    if (typeof value === 'string') str = value;
    else if (typeof value === 'number' || typeof value === 'boolean') str = String(value);
    else {
      try {
        str = JSON.stringify(value);
      } catch {
        str = '?';
      }
    }
    if (str.length > 60) str = str.slice(0, 57) + '…';
    parts.push(`${key}=${str}`);
  }
  return parts.join(' ');
}

function CategoryChip({ category }: { category: string }) {
  const color = `var(--cat-${category}, var(--cat-OTHER))`;
  return (
    <span className="cat" style={{ color, borderLeft: `3px solid ${color}`, paddingLeft: 4 }}>
      {category}
    </span>
  );
}

function SingleRow({
  event,
  selected,
  onSelect,
  childOfGroup,
}: {
  event: ParsedEvent;
  selected: boolean;
  onSelect: () => void;
  childOfGroup?: boolean;
}) {
  const isError = event.level === 'error' || event.errorClass != null;
  return (
    <div
      className={`row ${childOfGroup ? 'group-child' : ''} ${isError ? 'error' : ''} ${
        selected ? 'selected' : ''
      }`}
      onClick={onSelect}
    >
      <span className="ts">{formatTimestamp(event.timestampMs)}</span>
      <span className="level-icon">{isError ? '⚠' : ' '}</span>
      <CategoryChip category={event.category} />
      <span className="ev">{event.event}</span>
      <span className="fields">{summarizeFields(event)}</span>
      {event.durationMs != null && <span className="dur">+{event.durationMs}ms</span>}
    </div>
  );
}

// GroupHeaderRow / TraceGroup-based rendering removed — replaced by ActionCard.

export function Timeline() {
  const selectedSessionId = useViewer((s) => s.selectedSessionId);
  const bySession = useViewer((s) => s.index?.bySession);
  const filter = useViewer((s) => s.filter);
  const expandedGroupIds = useViewer((s) => s.expandedGroupIds);
  const toggleGroup = useViewer((s) => s.toggleGroup);
  const selectedEventId = useViewer((s) => s.selectedEventId);
  const setSelectedEvent = useViewer((s) => s.setSelectedEvent);

  const sessionEvents = React.useMemo<ParsedEvent[]>(() => {
    if (!selectedSessionId || !bySession) return [];
    return bySession.get(selectedSessionId) ?? [];
  }, [selectedSessionId, bySession]);

  const items = React.useMemo<TimelineItem[]>(
    () => matchActions(sessionEvents),
    [sessionEvents],
  );

  // 把 ParsedEvent 索引按 id，便于 ActionCard 展开时找子事件实体。
  const eventsById = React.useMemo(() => {
    const m = new Map<string, ParsedEvent>();
    for (const e of sessionEvents) m.set(e.id, e);
    return m;
  }, [sessionEvents]);

  // 适用现有 filter：levels / categories / eventQuery / onlyErrors 仅过滤 raw 事件，
  // ActionCard 默认全显示——未来可以加 action-type chip。
  const filtered = React.useMemo(() => {
    if (!filter) return items;
    return items.filter((item) => {
      if (item.kind === 'action') return true;
      const ev = item.event;
      if (filter.levels.size > 0 && !filter.levels.has(ev.level)) return false;
      if (filter.categories.size > 0 && !filter.categories.has(ev.category)) return false;
      if (filter.onlyErrors && ev.level !== 'error' && !ev.errorClass) return false;
      if (filter.onlyWithTrace && !ev.traceId) return false;
      if (filter.eventQuery && !ev.event.toLowerCase().includes(filter.eventQuery.toLowerCase())) {
        return false;
      }
      return true;
    });
  }, [items, filter]);

  const flat = React.useMemo<FlatRow[]>(() => {
    const out: FlatRow[] = [];
    for (const item of filtered) {
      if (item.kind === 'action') {
        const cardKey = item.eventIds.join(',');
        out.push({
          kind: 'action',
          item,
          expanded: expandedGroupIds.has(cardKey),
        });
      } else {
        out.push({ kind: 'raw', event: item.event });
      }
    }
    return out;
  }, [filtered, expandedGroupIds]);

  const parentRef = React.useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: flat.length,
    getScrollElement: () => parentRef.current,
    estimateSize: (i) => (flat[i]?.kind === 'action' ? ACTION_CARD_HEIGHT : ROW_HEIGHT),
    overscan: 12,
  });

  return (
    <div ref={parentRef} className="timeline">
      {flat.length === 0 && (
        <div className="empty-state" style={{ height: '100%' }}>
          {items.length === 0 ? '该 session 无事件' : '当前过滤无匹配'}
        </div>
      )}
      <div
        style={{
          height: virtualizer.getTotalSize(),
          position: 'relative',
          width: '100%',
        }}
      >
        {virtualizer.getVirtualItems().map((vrow) => {
          const row = flat[vrow.index];
          if (!row) return null;
          return (
            <div
              key={vrow.key}
              data-index={vrow.index}
              ref={virtualizer.measureElement}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${vrow.start}px)`,
              }}
            >
              {row.kind === 'action' && (
                <ActionCard
                  card={row.item.card}
                  timestampMs={row.item.sortMs}
                  expanded={row.expanded}
                  selected={row.item.eventIds.includes(selectedEventId ?? '')}
                  childEvents={row.item.eventIds
                    .map((id) => eventsById.get(id))
                    .filter((e): e is ParsedEvent => e != null)}
                  onToggle={() => toggleGroup(row.item.eventIds.join(','))}
                  onSelectChild={(id) => setSelectedEvent(id)}
                />
              )}
              {row.kind === 'raw' && (
                <SingleRow
                  event={row.event}
                  selected={row.event.id === selectedEventId}
                  onSelect={() => setSelectedEvent(row.event.id)}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
