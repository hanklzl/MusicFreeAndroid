import { useVirtualizer } from '@tanstack/react-virtual';
import React from 'react';

import { applyFilterToRows } from '../model/filter.js';
import { groupBySession, type TimelineRow } from '../model/traceGrouper.js';
import type { ParsedEvent, TraceGroup } from '../model/types.js';
import { useViewer } from '../state/store.js';

type FlatRow =
  | { kind: 'group-header'; group: TraceGroup; expanded: boolean }
  | { kind: 'group-child'; event: ParsedEvent; groupId: string }
  | { kind: 'single'; event: ParsedEvent };

const ROW_HEIGHT = 22;

function flatten(
  rows: TimelineRow[],
  expanded: Set<string>,
  autoExpandError: boolean,
): FlatRow[] {
  const out: FlatRow[] = [];
  for (const row of rows) {
    if (row.kind === 'single') {
      out.push({ kind: 'single', event: row.event });
    } else {
      const isExpanded = expanded.has(row.group.id) || (autoExpandError && row.group.hasError);
      out.push({ kind: 'group-header', group: row.group, expanded: isExpanded });
      if (isExpanded) {
        for (const event of row.group.events) {
          out.push({ kind: 'group-child', event, groupId: row.group.id });
        }
      }
    }
  }
  return out;
}

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

function GroupHeaderRow({
  group,
  expanded,
  onToggle,
  selected,
  onSelect,
}: {
  group: TraceGroup;
  expanded: boolean;
  onToggle: () => void;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <div
      className={`row group-header ${group.hasError ? 'error' : ''} ${
        selected ? 'selected' : ''
      }`}
      onClick={onSelect}
    >
      <span className="ts">{formatTimestamp(group.startMs)}</span>
      <span
        className="caret"
        onClick={(e) => {
          e.stopPropagation();
          onToggle();
        }}
      >
        {expanded ? '▼' : '▶'}
      </span>
      <CategoryChip category={group.category} />
      <span className="ev">
        {group.headEvent.event}
        {group.headEvent.event !== group.tailEvent.event && ` → ${group.tailEvent.event}`}
      </span>
      <span className="count">└ {group.events.length} events</span>
      <span className="dur">+{group.durationMs}ms</span>
      {group.result && (
        <span className="dur" style={{ color: group.hasError ? 'var(--error)' : 'var(--success)' }}>
          {group.hasError ? '⚠' : '✓'} {group.result}
        </span>
      )}
    </div>
  );
}

export function Timeline() {
  const selectedSessionId = useViewer((s) => s.selectedSessionId);
  const bySession = useViewer((s) => s.index?.bySession);
  const filter = useViewer((s) => s.filter);
  const expandedGroupIds = useViewer((s) => s.expandedGroupIds);
  const toggleGroup = useViewer((s) => s.toggleGroup);
  const selectedEventId = useViewer((s) => s.selectedEventId);
  const setSelectedEvent = useViewer((s) => s.setSelectedEvent);

  const rows = React.useMemo<TimelineRow[]>(() => {
    if (!selectedSessionId || !bySession) return [];
    const events = bySession.get(selectedSessionId) ?? [];
    return groupBySession(selectedSessionId, events);
  }, [selectedSessionId, bySession]);

  const filteredRows = React.useMemo(() => applyFilterToRows(rows, filter), [rows, filter]);
  const flat = React.useMemo(
    () => flatten(filteredRows, expandedGroupIds, true),
    [filteredRows, expandedGroupIds],
  );

  const parentRef = React.useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: flat.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 20,
  });

  return (
    <div ref={parentRef} className="timeline">
      {flat.length === 0 && (
        <div className="empty-state" style={{ height: '100%' }}>
          {rows.length === 0 ? '该 session 无事件' : '当前过滤无匹配'}
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
          const item = flat[vrow.index];
          if (!item) return null;
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
              {item.kind === 'single' && (
                <SingleRow
                  event={item.event}
                  selected={item.event.id === selectedEventId}
                  onSelect={() => setSelectedEvent(item.event.id)}
                />
              )}
              {item.kind === 'group-header' && (
                <GroupHeaderRow
                  group={item.group}
                  expanded={item.expanded}
                  onToggle={() => toggleGroup(item.group.id)}
                  selected={item.group.headEvent.id === selectedEventId}
                  onSelect={() => setSelectedEvent(item.group.headEvent.id)}
                />
              )}
              {item.kind === 'group-child' && (
                <SingleRow
                  event={item.event}
                  selected={item.event.id === selectedEventId}
                  onSelect={() => setSelectedEvent(item.event.id)}
                  childOfGroup
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
