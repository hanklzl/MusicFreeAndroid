import JSZip from 'jszip';

import type { ManifestJson } from '../model/types.js';

export interface LoganFileEntry {
  name: string;
  bytes: Uint8Array;
}

export interface ZipEntries {
  manifest: ManifestJson;
  loganFiles: LoganFileEntry[];
}

export class FeedbackZipError extends Error {}

export async function readFeedbackZip(input: Blob | ArrayBuffer | Uint8Array): Promise<ZipEntries> {
  const zip = await JSZip.loadAsync(input as ArrayBuffer | Uint8Array);

  const manifestFile = zip.file('manifest.json');
  if (!manifestFile) {
    throw new FeedbackZipError('Feedback zip is missing manifest.json at the root.');
  }
  const manifestText = await manifestFile.async('string');
  let manifest: ManifestJson;
  try {
    manifest = JSON.parse(manifestText) as ManifestJson;
  } catch (cause) {
    throw new FeedbackZipError(
      `manifest.json is not valid JSON: ${(cause as Error).message}`,
    );
  }

  const loganFiles: LoganFileEntry[] = [];
  const loganDir = zip.folder('logan');
  if (!loganDir) {
    throw new FeedbackZipError('Feedback zip is missing the logan/ directory.');
  }

  const loganEntries: Array<{ relativePath: string; file: JSZip.JSZipObject }> = [];
  loganDir.forEach((relativePath, file) => {
    if (file.dir) return;
    loganEntries.push({ relativePath, file });
  });

  for (const { relativePath, file } of loganEntries) {
    const bytes = await file.async('uint8array');
    loganFiles.push({ name: `logan/${relativePath}`, bytes });
  }

  if (loganFiles.length === 0) {
    throw new FeedbackZipError('logan/ directory contains no files.');
  }

  loganFiles.sort((a, b) => a.name.localeCompare(b.name));

  return { manifest, loganFiles };
}
