import React from 'react';

import { useViewer } from '../state/store.js';
import { DetailDrawer } from './DetailDrawer.js';
import { FilterBar } from './FilterBar.js';
import { SessionList } from './SessionList.js';
import { Timeline } from './Timeline.js';
import { TopBar } from './TopBar.js';

export function App() {
  const status = useViewer((s) => s.status);
  const errorMessage = useViewer((s) => s.errorMessage);
  const progress = useViewer((s) => s.progress);
  const loadZip = useViewer((s) => s.loadZip);
  const [dragging, setDragging] = React.useState(false);

  const onDrop = React.useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      const file = e.dataTransfer.files[0];
      if (file) loadZip(file);
    },
    [loadZip],
  );

  if (status === 'idle' || status === 'parsing') {
    return (
      <div
        className={`app ${dragging ? 'dropping' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <TopBar />
        {progress && (
          <div className="progress-bar">
            <div className="fill" style={{ width: `${Math.round(progress.pct)}%` }} />
          </div>
        )}
        <div className={`empty-state ${dragging ? 'dropping' : ''}`}>
          <div style={{ fontSize: '15px', fontWeight: 600 }}>
            {status === 'parsing'
              ? `解析中… (${progress?.phase ?? ''} ${Math.round(progress?.pct ?? 0)}%)`
              : '拖入 musicfree-feedback-*.zip 开始'}
          </div>
          <div style={{ fontSize: '12px', opacity: 0.7 }}>
            或点击右上角的 [+ Load .zip]
          </div>
        </div>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="app">
        <TopBar />
        <div className="error-banner">载入失败：{errorMessage}</div>
        <div className="empty-state">点击右上角重新载入</div>
      </div>
    );
  }

  return (
    <div className="app">
      <TopBar />
      <FilterBar />
      <main className="main">
        <SessionList />
        <section className="timeline-area">
          <Timeline />
        </section>
        <DetailDrawer />
      </main>
    </div>
  );
}
