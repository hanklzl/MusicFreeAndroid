import React from 'react';

import { useViewer } from '../state/store.js';

export function TopBar() {
  const manifest = useViewer((s) => s.manifest);
  const index = useViewer((s) => s.index);
  const loadZip = useViewer((s) => s.loadZip);
  const reset = useViewer((s) => s.reset);
  const inputRef = React.useRef<HTMLInputElement>(null);

  const onPick = React.useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) loadZip(file);
      e.target.value = '';
    },
    [loadZip],
  );

  const summary = React.useMemo(() => {
    if (!manifest || !index) return null;
    const sessions = index.sessionSummaries.length;
    const events = index.events.length;
    const errors = index.sessionSummaries.reduce((sum, s) => sum + s.errorCount, 0);
    return { sessions, events, errors };
  }, [manifest, index]);

  return (
    <div className="topbar">
      <span className="title">MusicFree Log Viewer</span>
      {manifest && (
        <span className="meta">
          v{manifest.versionName} ({manifest.buildType}) ·{' '}
          {manifest.deviceManufacturer} {manifest.deviceModel} · Android {manifest.androidRelease}
        </span>
      )}
      {summary && (
        <span className="meta">
          · {summary.sessions} sessions · {summary.events.toLocaleString()} events ·{' '}
          <span style={{ color: summary.errors > 0 ? 'var(--error)' : 'inherit' }}>
            {summary.errors} errors
          </span>
        </span>
      )}
      <span className="grow" />
      <input
        ref={inputRef}
        type="file"
        accept=".zip"
        style={{ display: 'none' }}
        onChange={onPick}
      />
      {manifest && (
        <button onClick={reset} style={{ marginRight: 8 }}>
          清空
        </button>
      )}
      <button className="primary" onClick={() => inputRef.current?.click()}>
        + Load .zip
      </button>
    </div>
  );
}
