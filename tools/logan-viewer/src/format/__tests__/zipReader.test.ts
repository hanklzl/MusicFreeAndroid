import { describe, expect, it } from 'vitest';

import { FeedbackZipError, readFeedbackZip } from '../zipReader.js';
import { SAMPLE_APP_START_EVENT, buildSampleZip } from './helpers/fixtureFactory.js';

describe('readFeedbackZip', () => {
  it('parses manifest.json and logan files', async () => {
    const { zipBytes, manifest } = await buildSampleZip();
    const entries = await readFeedbackZip(zipBytes);
    expect(entries.manifest.sessionId).toBe(manifest.sessionId);
    expect(entries.manifest.applicationId).toBe('com.hank.musicfree');
    expect(entries.loganFiles).toHaveLength(1);
    expect(entries.loganFiles[0]!.name).toBe('logan/1779033600000');
    expect(entries.loganFiles[0]!.bytes.length).toBeGreaterThan(0);
  });

  it('returns all logan files sorted by name when there are multiple', async () => {
    const { zipBytes } = await buildSampleZip({
      loganFiles: [
        { name: '1779033600000', blocks: [[SAMPLE_APP_START_EVENT]] },
        { name: '1779120000000', blocks: [[SAMPLE_APP_START_EVENT]] },
      ],
    });
    const entries = await readFeedbackZip(zipBytes);
    expect(entries.loganFiles.map((f) => f.name)).toEqual([
      'logan/1779033600000',
      'logan/1779120000000',
    ]);
  });

  it('throws when manifest.json is missing', async () => {
    const { zipBytes } = await buildSampleZip({ omitManifest: true });
    await expect(readFeedbackZip(zipBytes)).rejects.toBeInstanceOf(FeedbackZipError);
  });

  it('throws when logan/ has no files', async () => {
    const { zipBytes } = await buildSampleZip({ omitLoganDir: true });
    await expect(readFeedbackZip(zipBytes)).rejects.toBeInstanceOf(FeedbackZipError);
  });
});
