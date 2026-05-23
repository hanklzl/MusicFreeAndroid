import { create } from 'zustand';

import type {
  EventIndex,
  FilterState,
  ManifestJson,
} from '../model/types.js';
import type { WorkerEvent, WorkerRequest } from '../worker/protocol.js';

export type ViewerStatus = 'idle' | 'parsing' | 'ready' | 'error';

export interface ViewerProgress {
  phase: 'unzip' | 'decrypt' | 'parse' | 'index';
  pct: number;
}

const DEFAULT_FILTER: FilterState = {
  levels: new Set(),
  categories: new Set(),
  eventQuery: '',
  onlyErrors: false,
  onlyWithTrace: false,
};

export interface ViewerStore {
  status: ViewerStatus;
  progress: ViewerProgress | null;
  errorMessage: string | null;
  manifest: ManifestJson | null;
  index: EventIndex | null;
  selectedSessionId: string | null;
  selectedEventId: string | null;
  expandedGroupIds: Set<string>;
  filter: FilterState;
  loadZip: (file: File) => Promise<void>;
  setSelectedSession: (id: string) => void;
  setSelectedEvent: (id: string | null) => void;
  toggleGroup: (groupId: string) => void;
  setFilter: (patch: Partial<FilterState>) => void;
  reset: () => void;
}

function freshFilter(): FilterState {
  return {
    ...DEFAULT_FILTER,
    levels: new Set(),
    categories: new Set(),
  };
}

let nextReqId = 1;

function createDecodeWorker(): Worker {
  return new Worker(new URL('../worker/decode.worker.ts', import.meta.url), {
    type: 'module',
  });
}

export const useViewer = create<ViewerStore>((set, get) => ({
  status: 'idle',
  progress: null,
  errorMessage: null,
  manifest: null,
  index: null,
  selectedSessionId: null,
  selectedEventId: null,
  expandedGroupIds: new Set(),
  filter: freshFilter(),

  loadZip(file) {
    return new Promise((resolve) => {
      const reqId = nextReqId++;
      const worker = createDecodeWorker();
      set({
        status: 'parsing',
        progress: { phase: 'unzip', pct: 0 },
        errorMessage: null,
      });

      const cleanup = () => {
        worker.terminate();
        resolve();
      };

      worker.onmessage = (e: MessageEvent<WorkerEvent>) => {
        const msg = e.data;
        if (msg.id !== reqId) return;
        switch (msg.kind) {
          case 'progress':
            set({ progress: { phase: msg.phase, pct: msg.pct } });
            break;
          case 'done':
            set({
              status: 'ready',
              progress: null,
              manifest: msg.manifest,
              index: msg.index,
              selectedSessionId: msg.index.sessionSummaries[0]?.sessionId ?? null,
              selectedEventId: null,
              expandedGroupIds: new Set(),
              filter: freshFilter(),
            });
            cleanup();
            break;
          case 'error':
            set({ status: 'error', errorMessage: msg.message, progress: null });
            cleanup();
            break;
        }
      };

      worker.onerror = (event) => {
        set({
          status: 'error',
          errorMessage: event.message || 'Worker crashed',
          progress: null,
        });
        cleanup();
      };

      file.arrayBuffer().then((buffer) => {
        const req: WorkerRequest = { id: reqId, kind: 'load', file: buffer };
        worker.postMessage(req, [buffer]);
      });
    });
  },

  setSelectedSession(id) {
    set({ selectedSessionId: id, selectedEventId: null, expandedGroupIds: new Set() });
  },

  setSelectedEvent(id) {
    set({ selectedEventId: id });
  },

  toggleGroup(groupId) {
    const next = new Set(get().expandedGroupIds);
    if (next.has(groupId)) next.delete(groupId);
    else next.add(groupId);
    set({ expandedGroupIds: next });
  },

  setFilter(patch) {
    set({ filter: { ...get().filter, ...patch } });
  },

  reset() {
    set({
      status: 'idle',
      progress: null,
      errorMessage: null,
      manifest: null,
      index: null,
      selectedSessionId: null,
      selectedEventId: null,
      expandedGroupIds: new Set(),
      filter: freshFilter(),
    });
  },
}));
