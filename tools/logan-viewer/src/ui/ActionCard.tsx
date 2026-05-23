import React from 'react';

import type { ActionCardData } from '../actions/types.js';
import type { ParsedEvent } from '../model/types.js';

function fmtClock(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
}

function summarizeFields(fields: Record<string, unknown> | undefined): string {
  if (!fields) return '';
  const keys = Object.keys(fields);
  if (!keys.length) return '';
  return keys.slice(0, 3).map((k) => {
    const v = fields[k];
    const s = v == null ? '' : typeof v === 'object' ? JSON.stringify(v) : String(v);
    return `${k}=${s.length > 40 ? s.slice(0, 37) + '…' : s}`;
  }).join(' · ');
}

const STATUS_TEXT: Record<string, string> = {
  success: '✓ 完成',
  failure: '✕ 失败',
  ongoing: '… 进行中',
  partial: '◐ 部分',
};

export function ActionCard({
  card,
  timestampMs,
  expanded,
  selected,
  childEvents,
  onToggle,
  onSelectChild,
}: {
  card: ActionCardData;
  timestampMs: number;
  expanded: boolean;
  selected: boolean;
  childEvents: ParsedEvent[];
  onToggle: () => void;
  onSelectChild: (eventId: string) => void;
}) {
  const status = STATUS_TEXT[card.result] ?? '';
  return (
    <div
      className={`action-card result-${card.result} ${expanded ? 'open' : ''} ${selected ? 'selected' : ''}`}
      onClick={onToggle}
    >
      <div className="action-card-header">
        <span className="action-card-time">{fmtClock(timestampMs)}</span>
        <span className="action-card-icon">{card.icon}</span>
        <div className="action-card-title-wrap">
          <div className="action-card-title">{card.title}</div>
          {card.subtitle && <div className="action-card-subtitle">{card.subtitle}</div>}
        </div>
        <span className={`action-card-status ${card.result}`}>{status}</span>
        <span className="action-card-chevron">{expanded ? '▼' : '▶'}</span>
      </div>
      {card.followups && card.followups.length > 0 && (
        <div className="action-card-followups">
          {card.followups.map((fu) => (
            <div key={fu.id} className="action-followup">
              <span className="arrow">{fu.arrow}</span> {fu.text}
            </div>
          ))}
        </div>
      )}
      {expanded && (
        <div className="action-card-body">
          {childEvents.map((e) => (
            <div
              key={e.id}
              className={`action-child-event ${e.level === 'error' ? 'error' : ''}`}
              onClick={(ev) => {
                ev.stopPropagation();
                onSelectChild(e.id);
              }}
            >
              <span className="action-child-time">{fmtClock(e.timestampMs)}</span>
              <span className="action-child-cat">{e.category}</span>
              <span className="action-child-event-name">{e.event}</span>
              <span className="action-child-fields">{summarizeFields(e.fields)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
