// 反向构造 Logan 反馈包（in-memory），用于单测与 demo fixture。
// 与 LoganLocalDecoder.java 的解析逻辑对称：每个 block 是
//   [0x01][BE-uint32 length][AES-128-CBC PKCS7 encrypt(gzip(plaintext_lines))]
// 多个 block 顺序 concatenate；manifest.json + logan/<name> 一起进 zip。

import { cbc } from '@noble/ciphers/aes';
import { gzipSync } from 'fflate';
import JSZip from 'jszip';

import { DEBUG_KEY_PAIR } from '../../../keys/builtin.js';
import type { ManifestJson, RawLogEvent } from '../../../model/types.js';

const ENCRYPT_CONTENT_START = 0x01;

export const SAMPLE_SESSION_ID = '11111111-2222-3333-4444-555555555555';

export const SAMPLE_APP_START_EVENT: RawLogEvent = {
  level: 'info',
  category: 'APP',
  event: 'app_start',
  timestamp: '2026-05-23T12:00:00.000+08:00',
  sessionId: SAMPLE_SESSION_ID,
};

export interface BuildSampleZipOptions {
  blocks?: RawLogEvent[][];
  loganFiles?: Array<{ name: string; blocks: RawLogEvent[][] }>;
  manifest?: Partial<ManifestJson>;
  key?: Uint8Array;
  iv?: Uint8Array;
  /** 把最后一个 block 的尾部 gzip 字节截掉若干字节，模拟进程被 kill 截断 */
  truncateLastBlockBytes?: number;
  /** 当 zip 中不放置 manifest.json 时设为 true */
  omitManifest?: boolean;
  /** 当 zip 中不放置 logan/ 目录时设为 true */
  omitLoganDir?: boolean;
  /** 在 logan/ 之外的多余字节填充（模拟坏 magic byte） */
  prependGarbageBytes?: Uint8Array;
}

export interface SampleZipBuildResult {
  zipBytes: Uint8Array;
  manifest: ManifestJson;
}

export function eventToLine(event: RawLogEvent): string {
  return `${JSON.stringify(event)}\n`;
}

export function buildLoganBlockBytes(
  events: RawLogEvent[],
  key: Uint8Array,
  iv: Uint8Array,
  options: { gzipTruncateBytes?: number } = {},
): Uint8Array {
  const plain = new TextEncoder().encode(events.map(eventToLine).join(''));
  let compressed = gzipSync(plain);
  if (options.gzipTruncateBytes && options.gzipTruncateBytes > 0) {
    compressed = compressed.slice(0, Math.max(0, compressed.length - options.gzipTruncateBytes));
  }
  const encrypted = cbc(key, iv).encrypt(compressed);

  const frame = new Uint8Array(1 + 4 + encrypted.length);
  frame[0] = ENCRYPT_CONTENT_START;
  const view = new DataView(frame.buffer, frame.byteOffset + 1, 4);
  view.setUint32(0, encrypted.length, false /* big-endian */);
  frame.set(encrypted, 5);
  return frame;
}

export async function buildSampleZip(
  options: BuildSampleZipOptions = {},
): Promise<SampleZipBuildResult> {
  const key = options.key ?? DEBUG_KEY_PAIR.key;
  const iv = options.iv ?? DEBUG_KEY_PAIR.iv;

  const loganFiles =
    options.loganFiles ??
    [
      {
        name: '1779033600000',
        blocks: options.blocks ?? [[SAMPLE_APP_START_EVENT]],
      },
    ];

  const manifest: ManifestJson = {
    generatedAt: '2026-05-23T12:00:00.000+08:00',
    sessionId: SAMPLE_SESSION_ID,
    applicationId: 'com.hank.musicfree',
    versionName: '1.2.5',
    versionCode: 125,
    buildType: 'debug',
    androidSdk: 34,
    androidRelease: '14',
    deviceManufacturer: 'Google',
    deviceModel: 'Pixel 7',
    supportedAbis: ['arm64-v8a'],
    logStartLastModified: 1779033600000,
    logEndLastModified: 1779120000000,
    files: loganFiles.map((file, i) => ({
      path: `logan/${file.name}`,
      sizeBytes: 0,
      lastModified: 1779033600000 + i * 1000,
    })),
    ...options.manifest,
  };

  const zip = new JSZip();
  if (!options.omitManifest) {
    zip.file('manifest.json', JSON.stringify(manifest, null, 2));
  }

  if (!options.omitLoganDir) {
    for (let fileIndex = 0; fileIndex < loganFiles.length; fileIndex++) {
      const file = loganFiles[fileIndex]!;
      const isLastFile = fileIndex === loganFiles.length - 1;
      const frames: Uint8Array[] = [];
      if (options.prependGarbageBytes && options.prependGarbageBytes.length > 0) {
        frames.push(options.prependGarbageBytes);
      }
      for (let blockIndex = 0; blockIndex < file.blocks.length; blockIndex++) {
        const events = file.blocks[blockIndex]!;
        const isLastBlock = isLastFile && blockIndex === file.blocks.length - 1;
        const gzipTruncateBytes =
          isLastBlock && options.truncateLastBlockBytes
            ? options.truncateLastBlockBytes
            : undefined;
        frames.push(buildLoganBlockBytes(events, key, iv, { gzipTruncateBytes }));
      }
      zip.file(`logan/${file.name}`, concatBytes(frames));
    }
  }

  const zipBytes = await zip.generateAsync({ type: 'uint8array' });
  return { zipBytes, manifest };
}

function concatBytes(parts: Uint8Array[]): Uint8Array {
  const totalLength = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(totalLength);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}
