import React from 'react';

import { useViewer } from '../state/store.js';

function formatTime(ms: number): string {
  if (!ms) return '?';
  const d = new Date(ms);
  return d.toLocaleString();
}

export function SessionList() {
  const summaries = useViewer((s) => s.index?.sessionSummaries ?? []);
  const selectedId = useViewer((s) => s.selectedSessionId);
  const setSelected = useViewer((s) => s.setSelectedSession);

  return (
    <aside className="sessions">
      {summaries.map((summary, idx) => (
        <div
          key={summary.sessionId}
          className={`session-card ${summary.sessionId === selectedId ? 'active' : ''}`}
          onClick={() => setSelected(summary.sessionId)}
        >
          <div className="name">
            #{idx + 1} · {summary.sessionId.slice(0, 8)}
          </div>
          <div className="row" style={{ fontSize: 11, color: 'var(--text-dim)' }}>
            <span>{formatTime(summary.startMs)}</span>
          </div>
          <div className="row" style={{ marginTop: 6 }}>
            <span>{summary.eventCount.toLocaleString()} ev</span>
            <span className={summary.errorCount > 0 ? 'errs' : 'ok'}>
              {summary.errorCount > 0 ? `${summary.errorCount} err` : 'ok'}
            </span>
          </div>
        </div>
      ))}
    </aside>
  );
}
