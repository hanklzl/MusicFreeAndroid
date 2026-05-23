import React from 'react';

import { useViewer } from '../state/store.js';
import type { ParsedEvent } from '../model/types.js';

function findEventById(events: ParsedEvent[] | undefined, id: string | null): ParsedEvent | null {
  if (!events || !id) return null;
  return events.find((e) => e.id === id) ?? null;
}

export function DetailDrawer() {
  const id = useViewer((s) => s.selectedEventId);
  const allEvents = useViewer((s) => s.index?.events);
  const byTrace = useViewer((s) => s.index?.byTrace);
  const setSelectedEvent = useViewer((s) => s.setSelectedEvent);

  const event = React.useMemo(() => findEventById(allEvents, id), [allEvents, id]);

  if (!event) {
    return <aside className="detail empty">点击事件查看详情</aside>;
  }

  const siblings = event.groupId ? byTrace?.get(event.groupId) ?? [] : [];

  return (
    <aside className="detail">
      <h3>{event.event}</h3>
      <dl>
        <dt>level</dt>
        <dd style={{ color: event.level === 'error' ? 'var(--error)' : undefined }}>{event.level}</dd>
        <dt>category</dt>
        <dd>{event.category}</dd>
        <dt>time</dt>
        <dd>{event.timestamp}</dd>
        <dt>session</dt>
        <dd style={{ fontFamily: 'monospace', fontSize: 11 }}>{event.sessionId}</dd>
        {event.traceId && (
          <>
            <dt>trace</dt>
            <dd style={{ fontFamily: 'monospace', fontSize: 11 }}>{event.traceId}</dd>
          </>
        )}
        {event.durationMs != null && (
          <>
            <dt>duration</dt>
            <dd>{event.durationMs}ms</dd>
          </>
        )}
        {event.result && (
          <>
            <dt>result</dt>
            <dd>{event.result}</dd>
          </>
        )}
      </dl>

      {event.fields && Object.keys(event.fields).length > 0 && (
        <>
          <div className="section-title">Fields</div>
          <pre>{JSON.stringify(event.fields, null, 2)}</pre>
        </>
      )}

      {event.errorClass && (
        <>
          <div className="section-title">Error</div>
          <pre>
            {event.errorClass}
            {event.errorMessage ? `: ${event.errorMessage}` : ''}
          </pre>
        </>
      )}

      {event.stackTrace && (
        <>
          <div className="section-title">Stack trace</div>
          <pre>{event.stackTrace}</pre>
        </>
      )}

      {siblings.length > 1 && (
        <>
          <div className="section-title">Siblings ({siblings.length})</div>
          <div>
            {siblings.map((s) => (
              <div
                key={s.id}
                style={{
                  padding: '4px 6px',
                  borderRadius: 4,
                  cursor: 'pointer',
                  background: s.id === event.id ? 'var(--accent-bg)' : undefined,
                  color: s.level === 'error' ? 'var(--error)' : undefined,
                  fontSize: 11,
                  fontFamily: 'monospace',
                }}
                onClick={() => setSelectedEvent(s.id)}
              >
                {s.event}
              </div>
            ))}
          </div>
        </>
      )}
    </aside>
  );
}
