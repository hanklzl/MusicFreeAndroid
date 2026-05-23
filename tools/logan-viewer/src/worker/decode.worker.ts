/// <reference lib="webworker" />

import { parseDecodedText } from '../format/eventParser.js';
import { decodeWithAnyKey } from '../format/loganDecoder.js';
import { readFeedbackZip } from '../format/zipReader.js';
import { BUILTIN_KEY_PAIRS } from '../keys/builtin.js';
import { buildIndex, toParsedEvent } from '../model/indexer.js';
import type { ParsedEvent } from '../model/types.js';

import type { WorkerEvent, WorkerRequest } from './protocol.js';

declare const self: DedicatedWorkerGlobalScope;

const PROGRESS_THROTTLE_MS = 30;

function makeProgressReporter(reqId: number) {
  let lastEmit = 0;
  let lastPct = -1;
  return (phase: WorkerEvent['kind'] extends 'progress' ? never : 'unzip' | 'decrypt' | 'parse' | 'index', pct: number, force = false) => {
    const now = performance.now();
    if (!force && now - lastEmit < PROGRESS_THROTTLE_MS && pct - lastPct < 1) return;
    lastEmit = now;
    lastPct = pct;
    const evt: WorkerEvent = { id: reqId, kind: 'progress', phase, pct };
    self.postMessage(evt);
  };
}

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  const req = event.data;
  if (req.kind !== 'load') return;
  const reportProgress = makeProgressReporter(req.id);

  try {
    reportProgress('unzip', 0, true);
    const bytes = new Uint8Array(req.file);
    const { manifest, loganFiles } = await readFeedbackZip(bytes);
    reportProgress('unzip', 20, true);

    const allParsed: ParsedEvent[] = [];
    for (let fileIdx = 0; fileIdx < loganFiles.length; fileIdx++) {
      const file = loganFiles[fileIdx]!;
      const { result } = decodeWithAnyKey(file.bytes, file.name, BUILTIN_KEY_PAIRS);
      let lineIndex = 0;
      for (const block of result.blocks) {
        const parsed = parseDecodedText(block.text);
        for (const ev of parsed.events) {
          allParsed.push(toParsedEvent(ev, file.name, block.blockIndex, lineIndex++));
        }
      }
      const pct = 20 + ((fileIdx + 1) / loganFiles.length) * 70;
      reportProgress('decrypt', pct);
    }

    reportProgress('index', 95, true);
    const index = buildIndex(allParsed);
    reportProgress('index', 100, true);

    const done: WorkerEvent = { id: req.id, kind: 'done', manifest, index };
    self.postMessage(done);
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    const errEvt: WorkerEvent = { id: req.id, kind: 'error', message: msg };
    self.postMessage(errEvt);
  }
};
