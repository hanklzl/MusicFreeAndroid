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
    // logan/ 目录下除 Logan 加密文件（文件名是 timestamp 数字）外，还会有
    // ReadableLogStore 的明文 logan/readable-errors.log。后者不是 Logan 协议，
    // 不能走 AES-CBC + gunzip 解码，跳过避免误判。
    if (!/^\d+$/.test(relativePath)) return;
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
