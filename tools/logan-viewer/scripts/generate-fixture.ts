// 生成一个 demo 反馈 zip，便于本地手测 CLI / 浏览器。
// 输出：tools/logan-viewer/out/sample-feedback.zip（gitignored）

import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  SAMPLE_APP_START_EVENT,
  SAMPLE_SESSION_ID,
  buildSampleZip,
} from '../src/format/__tests__/helpers/fixtureFactory.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(here, '..', 'out');
await mkdir(outDir, { recursive: true });

const blocks = [
  [
    { ...SAMPLE_APP_START_EVENT },
    {
      ...SAMPLE_APP_START_EVENT,
      event: 'main_activity_create_start',
      timestamp: '2026-05-23T12:00:00.234+08:00',
    },
  ],
  [
    {
      level: 'info' as const,
      category: 'PLUGIN' as const,
      event: 'plugin_install_start',
      timestamp: '2026-05-23T12:00:02.000+08:00',
      sessionId: SAMPLE_SESSION_ID,
      traceId: 'trace-install-1',
      fields: { name: 'bilibili' },
    },
    {
      level: 'info' as const,
      category: 'PLUGIN' as const,
      event: 'plugin_install_success',
      timestamp: '2026-05-23T12:00:03.200+08:00',
      sessionId: SAMPLE_SESSION_ID,
      traceId: 'trace-install-1',
      durationMs: 1200,
      result: 'done',
      fields: { pluginId: 'bilibili', version: '1.0.3' },
    },
    {
      level: 'error' as const,
      category: 'PLUGIN' as const,
      event: 'plugin_install_failed',
      timestamp: '2026-05-23T12:00:08.456+08:00',
      sessionId: SAMPLE_SESSION_ID,
      traceId: 'trace-install-2',
      durationMs: 8123,
      result: 'fail',
      fields: { pluginId: 'broken', url: 'https://example.com/x.js', reason: 'http 404' },
      errorClass: 'java.io.IOException',
      errorMessage: 'HTTP 404',
      stackTrace: 'at example.Plugin.install(Plugin.kt:42)\n',
    },
  ],
];

const { zipBytes } = await buildSampleZip({ blocks });
const outPath = path.join(outDir, 'sample-feedback.zip');
await writeFile(outPath, zipBytes);
console.log(`Wrote fixture zip: ${outPath} (${zipBytes.length} bytes)`);
