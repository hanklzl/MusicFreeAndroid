import type { RawLogEvent } from '../model/types.js';

export interface SkippedLine {
  line: number;
  raw: string;
  reason: string;
}

export interface ParseResult {
  events: RawLogEvent[];
  skipped: SkippedLine[];
}

const REQUIRED_KEYS: Array<keyof RawLogEvent> = [
  'level',
  'category',
  'event',
  'timestamp',
  'sessionId',
];

/**
 * Logan 单行原始格式（clogan envelope）：
 *   {"c":"<inner>","f":1,"l":<ms>,"n":"<thread>","i":<id>,"m":true}
 * `c` 字段是真实业务 payload（我们的 LogEventFormatter JSON 字符串）。
 * 第一条 envelope 通常是 `{"c":"clogan header",...}` 这种 logan 内部元数据。
 *
 * 测试 fixture 直接喂裸 RawLogEvent JSON，所以这里同时兼容两种格式：
 * - 如果 parsed 是 clogan envelope（含 c/f/l/n），unwrap 一层；
 * - 否则视为裸 RawLogEvent。
 */
function extractRawEvent(parsed: unknown): unknown {
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return parsed;
  const obj = parsed as Record<string, unknown>;
  if (typeof obj.c === 'string' && 'f' in obj && 'l' in obj && 'n' in obj) {
    const c = obj.c;
    if (!c.startsWith('{')) return null;
    try {
      return JSON.parse(c);
    } catch {
      return null;
    }
  }
  return obj;
}

export function parseDecodedText(text: string): ParseResult {
  const events: RawLogEvent[] = [];
  const skipped: SkippedLine[] = [];

  let cursor = 0;
  let lineNumber = 0;
  while (cursor < text.length) {
    const newlineIndex = text.indexOf('\n', cursor);
    const end = newlineIndex === -1 ? text.length : newlineIndex;
    const rawLine = text.slice(cursor, end).replace(/\r$/, '');
    cursor = newlineIndex === -1 ? text.length : newlineIndex + 1;
    lineNumber += 1;

    const trimmed = rawLine.trim();
    if (trimmed.length === 0) continue;

    let envelope: unknown;
    try {
      envelope = JSON.parse(trimmed);
    } catch (err) {
      skipped.push({ line: lineNumber, raw: rawLine, reason: `invalid_json: ${(err as Error).message}` });
      continue;
    }

    const parsed = extractRawEvent(envelope);

    if (parsed === null) {
      skipped.push({ line: lineNumber, raw: rawLine, reason: 'clogan_meta_or_unwrap_failed' });
      continue;
    }

    if (typeof parsed !== 'object' || Array.isArray(parsed)) {
      skipped.push({ line: lineNumber, raw: rawLine, reason: 'not_object' });
      continue;
    }

    const missing = REQUIRED_KEYS.filter((key) => !(key in (parsed as Record<string, unknown>)));
    if (missing.length > 0) {
      skipped.push({ line: lineNumber, raw: rawLine, reason: `missing_fields: ${missing.join(',')}` });
      continue;
    }

    events.push(parsed as RawLogEvent);
  }

  return { events, skipped };
}
