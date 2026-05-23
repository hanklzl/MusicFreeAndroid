import type { FilterState, ParsedEvent } from './types.js';
import type { TimelineRow } from './traceGrouper.js';

export function eventMatchesFilter(event: ParsedEvent, filter: FilterState): boolean {
  if (filter.levels.size > 0 && !filter.levels.has(event.level)) return false;
  if (filter.categories.size > 0 && !filter.categories.has(event.category)) return false;
  if (filter.onlyErrors && event.level !== 'error' && event.errorClass == null) return false;
  if (filter.onlyWithTrace && event.groupId === null) return false;
  if (filter.eventQuery.trim().length > 0) {
    const q = filter.eventQuery.trim().toLowerCase();
    if (!event.event.toLowerCase().includes(q)) return false;
  }
  return true;
}

/**
 * row 级过滤：单条事件按 eventMatchesFilter；组按 OR 语义（任一成员命中则保留）。
 */
export function applyFilterToRows(rows: TimelineRow[], filter: FilterState): TimelineRow[] {
  const empty =
    filter.levels.size === 0 &&
    filter.categories.size === 0 &&
    filter.eventQuery.trim().length === 0 &&
    !filter.onlyErrors &&
    !filter.onlyWithTrace;
  if (empty) return rows;
  const result: TimelineRow[] = [];
  for (const row of rows) {
    if (row.kind === 'single') {
      if (eventMatchesFilter(row.event, filter)) result.push(row);
    } else {
      if (row.group.events.some((e) => eventMatchesFilter(e, filter))) result.push(row);
    }
  }
  return result;
}
