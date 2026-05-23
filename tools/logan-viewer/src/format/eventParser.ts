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

    let parsed: unknown;
    try {
      parsed = JSON.parse(trimmed);
    } catch (err) {
      skipped.push({ line: lineNumber, raw: rawLine, reason: `invalid_json: ${(err as Error).message}` });
      continue;
    }

    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
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
