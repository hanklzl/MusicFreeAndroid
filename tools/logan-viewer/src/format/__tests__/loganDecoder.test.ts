import { describe, expect, it } from 'vitest';

import { BUILTIN_KEY_PAIRS, DEBUG_KEY_PAIR, RELEASE_KEY_PAIR } from '../../keys/builtin.js';
import {
  LoganDecodeError,
  decodeLoganFile,
  decodeWithAnyKey,
} from '../loganDecoder.js';
import {
  SAMPLE_APP_START_EVENT,
  SAMPLE_SESSION_ID,
  buildLoganBlockBytes,
  buildSampleZip,
} from './helpers/fixtureFactory.js';
import { readFeedbackZip } from '../zipReader.js';

const PLAYER_EVENT = {
  level: 'trace' as const,
  category: 'PLAYER' as const,
  event: 'cache_hit',
  timestamp: '2026-05-23T12:00:05.000+08:00',
  sessionId: SAMPLE_SESSION_ID,
  fields: { source: 'local' as const },
};

describe('decodeLoganFile', () => {
  it('decodes a single block produced by the fixture factory', async () => {
    const { zipBytes } = await buildSampleZip({ blocks: [[SAMPLE_APP_START_EVENT]] });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeLoganFile(loganFiles[0]!.bytes, loganFiles[0]!.name, {
      key: DEBUG_KEY_PAIR.key,
      iv: DEBUG_KEY_PAIR.iv,
    });
    expect(result.blocks).toHaveLength(1);
    expect(result.blocks[0]!.text.trim()).toBe(JSON.stringify(SAMPLE_APP_START_EVENT));
    expect(result.truncatedBlockIndices).toEqual([]);
  });

  it('decodes multiple concatenated blocks', async () => {
    const { zipBytes } = await buildSampleZip({
      blocks: [[SAMPLE_APP_START_EVENT], [PLAYER_EVENT]],
    });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeLoganFile(loganFiles[0]!.bytes, loganFiles[0]!.name, {
      key: DEBUG_KEY_PAIR.key,
      iv: DEBUG_KEY_PAIR.iv,
    });
    expect(result.blocks).toHaveLength(2);
    expect(result.blocks[0]!.text).toContain('app_start');
    expect(result.blocks[1]!.text).toContain('cache_hit');
  });

  it('skips non-magic prefix bytes (position++ tolerance)', async () => {
    const { zipBytes } = await buildSampleZip({
      blocks: [[SAMPLE_APP_START_EVENT]],
      prependGarbageBytes: new Uint8Array([0x00, 0xff, 0x7e]),
    });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeLoganFile(loganFiles[0]!.bytes, loganFiles[0]!.name, {
      key: DEBUG_KEY_PAIR.key,
      iv: DEBUG_KEY_PAIR.iv,
    });
    expect(result.blocks).toHaveLength(1);
    expect(result.blocks[0]!.text).toContain('app_start');
  });

  it('throws when the wrong key is supplied', async () => {
    const block = buildLoganBlockBytes([SAMPLE_APP_START_EVENT], DEBUG_KEY_PAIR.key, DEBUG_KEY_PAIR.iv);
    const wrongKey = new TextEncoder().encode('wrong-key-1234ab');
    const wrongIv = new TextEncoder().encode('wrong-iv-1234abc');
    expect(() =>
      decodeLoganFile(block, 'logan/test', { key: wrongKey, iv: wrongIv }),
    ).toThrow(LoganDecodeError);
  });

  it('recovers partial bytes when the last block gzip tail is truncated', async () => {
    // 用足够大的 plaintext，让 fflate Gunzip 在抛错前已 emit 多个 chunk。
    const manyEvents = Array.from({ length: 8000 }, (_, i) => ({
      ...SAMPLE_APP_START_EVENT,
      event: `event_${i}`,
      fields: { i, payload: `${i}-${i.toString(16)}` },
    }));
    const { zipBytes } = await buildSampleZip({
      blocks: [manyEvents],
      truncateLastBlockBytes: 200,
    });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeLoganFile(loganFiles[0]!.bytes, loganFiles[0]!.name, {
      key: DEBUG_KEY_PAIR.key,
      iv: DEBUG_KEY_PAIR.iv,
    });
    expect(result.blocks).toHaveLength(1);
    expect(result.truncatedBlockIndices).toEqual([0]);
    expect(result.blocks[0]!.text.endsWith('\n')).toBe(true);
    expect(result.blocks[0]!.text.split('\n').length).toBeGreaterThan(100);
  });

  it('throws when gzip cannot even emit a partial chunk', async () => {
    // 极小 plaintext + 截断到底，导致 fflate 一字都没 emit 就报错。
    const { zipBytes } = await buildSampleZip({
      blocks: [[SAMPLE_APP_START_EVENT]],
      truncateLastBlockBytes: 30,
    });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    expect(() =>
      decodeLoganFile(loganFiles[0]!.bytes, loganFiles[0]!.name, {
        key: DEBUG_KEY_PAIR.key,
        iv: DEBUG_KEY_PAIR.iv,
      }),
    ).toThrow(LoganDecodeError);
  });
});

describe('decodeWithAnyKey', () => {
  it('returns the debug pair as the successful key', async () => {
    const { zipBytes } = await buildSampleZip({ blocks: [[SAMPLE_APP_START_EVENT]] });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeWithAnyKey(
      loganFiles[0]!.bytes,
      loganFiles[0]!.name,
      BUILTIN_KEY_PAIRS,
    );
    expect(result.keyName).toBe('debug');
    expect(result.result.blocks).toHaveLength(1);
  });

  it('skips placeholder pairs without throwing', async () => {
    const { zipBytes } = await buildSampleZip({ blocks: [[SAMPLE_APP_START_EVENT]] });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const result = decodeWithAnyKey(loganFiles[0]!.bytes, loganFiles[0]!.name, [
      RELEASE_KEY_PAIR,
      DEBUG_KEY_PAIR,
    ]);
    expect(result.keyName).toBe('debug');
  });

  it('throws when no real key works', async () => {
    const { zipBytes } = await buildSampleZip({ blocks: [[SAMPLE_APP_START_EVENT]] });
    const { loganFiles } = await readFeedbackZip(zipBytes);
    const wrongKey = new TextEncoder().encode('wrong-key-1234ab');
    const wrongIv = new TextEncoder().encode('wrong-iv-1234abc');
    expect(() =>
      decodeWithAnyKey(loganFiles[0]!.bytes, loganFiles[0]!.name, [
        { name: 'wrong', key: wrongKey, iv: wrongIv, isPlaceholder: false },
        RELEASE_KEY_PAIR,
      ]),
    ).toThrow(LoganDecodeError);
  });
});
