// 所有 viewer 内部使用的数据契约。
//
// 单行 JSON schema 与 logging/src/main/java/com/hank/musicfree/logging/LogEventFormatter.kt
// 严格对齐；新增字段时两边同步。

export type LogLevel = 'verbose' | 'debug' | 'info' | 'warn' | 'error';

export type LogCategory =
  | 'APP'
  | 'PLUGIN'
  | 'SEARCH'
  | 'PLAYER'
  | 'PLAYLIST_IMPORT'
  | 'FEEDBACK'
  | 'DATA'
  | 'FILE_IO'
  | 'DOWNLOAD'
  | 'SETTINGS'
  | 'HOME'
  | 'LYRICS'
  | 'UPDATE'
  | 'RUNTIME'
  | (string & {});

export interface ManifestFileEntry {
  path: string;
  sizeBytes: number;
  lastModified: number;
}

export interface ManifestJson {
  generatedAt: string;
  sessionId: string;
  applicationId: string;
  versionName: string;
  versionCode: number;
  buildType: string;
  androidSdk: number;
  androidRelease: string;
  deviceManufacturer: string;
  deviceModel: string;
  supportedAbis: string[];
  logStartLastModified: number | null;
  logEndLastModified: number | null;
  files: ManifestFileEntry[];
}

export interface RawLogEvent {
  level: LogLevel;
  category: LogCategory;
  event: string;
  timestamp: string;
  sessionId: string;
  traceId?: string;
  durationMs?: number;
  result?: string;
  fields?: Record<string, unknown>;
  errorClass?: string;
  errorMessage?: string;
  stackTrace?: string;
}

export interface ParsedEvent extends RawLogEvent {
  id: string;
  timestampMs: number;
  groupId: string | null;
  sourceFile: string;
}

export interface TraceGroup {
  kind: 'group';
  id: string;
  sessionId: string;
  traceId: string;
  category: LogCategory;
  headEvent: ParsedEvent;
  tailEvent: ParsedEvent;
  startMs: number;
  endMs: number;
  durationMs: number;
  events: ParsedEvent[];
  hasError: boolean;
  result: string | null;
}

export interface SessionSummary {
  sessionId: string;
  startMs: number;
  endMs: number;
  eventCount: number;
  errorCount: number;
  categories: LogCategory[];
}

export interface FilterState {
  levels: Set<LogLevel>;
  categories: Set<LogCategory>;
  eventQuery: string;
  onlyErrors: boolean;
  onlyWithTrace: boolean;
}

export interface EventIndex {
  events: ParsedEvent[];
  bySession: Map<string, ParsedEvent[]>;
  byTrace: Map<string, ParsedEvent[]>;
  byCategory: Map<LogCategory, number>;
  sessionSummaries: SessionSummary[];
}
