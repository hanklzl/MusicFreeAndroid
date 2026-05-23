import React from 'react';

import { useViewer } from '../state/store.js';
import type { LogCategory, LogLevel } from '../model/types.js';

const ALL_LEVELS: LogLevel[] = ['trace', 'detail', 'error'];

export function FilterBar() {
  const filter = useViewer((s) => s.filter);
  const setFilter = useViewer((s) => s.setFilter);
  const byCategory = useViewer((s) => s.index?.byCategory);

  const categories = React.useMemo(() => {
    if (!byCategory) return [] as LogCategory[];
    return [...byCategory.keys()].sort();
  }, [byCategory]);

  const toggle = <T,>(set: Set<T>, value: T): Set<T> => {
    const next = new Set(set);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    return next;
  };

  return (
    <div className="subbar">
      <div className="group">
        <span className="label">Category</span>
        {categories.map((cat) => (
          <span
            key={cat}
            className={`chip ${filter.categories.has(cat) ? 'active' : ''}`}
            style={{
              borderColor: filter.categories.has(cat)
                ? `var(--cat-${cat}, var(--cat-OTHER))`
                : undefined,
              color: filter.categories.has(cat)
                ? `var(--cat-${cat}, var(--cat-OTHER))`
                : undefined,
            }}
            onClick={() => setFilter({ categories: toggle(filter.categories, cat) })}
          >
            {cat} ({byCategory!.get(cat)})
          </span>
        ))}
      </div>
      <div className="group">
        <span className="label">Level</span>
        {ALL_LEVELS.map((lvl) => (
          <span
            key={lvl}
            className={`chip ${filter.levels.has(lvl) ? 'active' : ''}`}
            onClick={() => setFilter({ levels: toggle(filter.levels, lvl) })}
          >
            {lvl}
          </span>
        ))}
      </div>
      <div className="group">
        <span className="label">Event</span>
        <input
          type="search"
          placeholder="子串匹配..."
          value={filter.eventQuery}
          onChange={(e) => setFilter({ eventQuery: e.target.value })}
          style={{ width: 200 }}
        />
      </div>
      <div className="group">
        <span
          className={`chip ${filter.onlyErrors ? 'active' : ''}`}
          onClick={() => setFilter({ onlyErrors: !filter.onlyErrors })}
        >
          only errors
        </span>
        <span
          className={`chip ${filter.onlyWithTrace ? 'active' : ''}`}
          onClick={() => setFilter({ onlyWithTrace: !filter.onlyWithTrace })}
        >
          only with trace
        </span>
      </div>
    </div>
  );
}
