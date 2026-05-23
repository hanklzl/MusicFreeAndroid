import { describe, expect, it } from 'vitest';

import { parseDecodedText } from '../eventParser.js';

const VALID_LINE = JSON.stringify({
  level: 'trace',
  category: 'APP',
  event: 'app_start',
  timestamp: '2026-05-23T12:00:00.000+08:00',
  sessionId: 'session-1',
});

describe('parseDecodedText', () => {
  it('parses well-formed lines and skips empty lines', () => {
    const text = `${VALID_LINE}\n\n${VALID_LINE}\n`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(2);
    expect(result.skipped).toEqual([]);
  });

  it('skips lines that fail JSON parsing', () => {
    const text = `${VALID_LINE}\n{not json\n${VALID_LINE}`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(2);
    expect(result.skipped).toHaveLength(1);
    expect(result.skipped[0]!.line).toBe(2);
    expect(result.skipped[0]!.reason).toMatch(/^invalid_json/);
  });

  it('skips JSON values that are not plain objects', () => {
    const text = `${VALID_LINE}\n[1,2,3]\n"a string"`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(1);
    expect(result.skipped.map((s) => s.reason)).toEqual(['not_object', 'not_object']);
  });

  it('skips objects missing required fields', () => {
    const text = `${VALID_LINE}\n${JSON.stringify({ level: 'trace' })}`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(1);
    expect(result.skipped).toHaveLength(1);
    expect(result.skipped[0]!.reason).toMatch(/^missing_fields/);
  });

  it('handles \\r\\n line endings', () => {
    const text = `${VALID_LINE}\r\n${VALID_LINE}\r\n`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(2);
  });

  it('handles trailing content without final newline', () => {
    const text = `${VALID_LINE}\n${VALID_LINE}`;
    const result = parseDecodedText(text);
    expect(result.events).toHaveLength(2);
  });
});
