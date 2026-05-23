import type { EventIndex, ManifestJson } from '../model/types.js';

export type ReqId = number;

export type WorkerRequest = {
  id: ReqId;
  kind: 'load';
  file: ArrayBuffer;
};

export type WorkerEvent =
  | { id: ReqId; kind: 'progress'; phase: 'unzip' | 'decrypt' | 'parse' | 'index'; pct: number }
  | { id: ReqId; kind: 'done'; manifest: ManifestJson; index: EventIndex }
  | { id: ReqId; kind: 'error'; message: string };
