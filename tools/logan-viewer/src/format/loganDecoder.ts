// 1:1 复刻 tools/logan/LoganLocalDecoder.java 的字节级解析。
//
// Logan 文件结构：连续的 [0x01][BE-uint32 length][encrypted_block] 帧。
// 遇到非 0x01 字节 position++ 跳过（容错），保持与 Java L64-66 等价。
//
// AES-128-CBC：默认 PKCS7（与 Java PKCS5Padding 字节级等价），失败回退 NoPadding，
// 用于恢复末块被截断的日志（进程被 kill 时常见）。
//
// gunzip：默认整体解压；失败时尝试流式解压拿 partial 字节，再截到最后一个 \n，
// 与 Java truncateToLastLine 行为一致。

import { cbc } from '@noble/ciphers/aes';
import { Gunzip } from 'fflate';

const ENCRYPT_CONTENT_START = 0x01;
const LENGTH_BYTES = 4;
const NEWLINE = 0x0a;

export interface DecodedBlock {
  text: string;
  sourceFile: string;
  blockIndex: number;
}

export interface DecodeOptions {
  key: Uint8Array;
  iv: Uint8Array;
}

export interface DecodeResult {
  blocks: DecodedBlock[];
  truncatedBlockIndices: number[];
  /** 单个 block 因解密 / 解压失败被 skip 时记录原因；不影响其它 block。 */
  skippedBlocks: Array<{ blockIndex: number; reason: string }>;
}

export class LoganDecodeError extends Error {}

export function decodeLoganFile(
  bytes: Uint8Array,
  sourceFile: string,
  opts: DecodeOptions,
): DecodeResult {
  if (opts.key.length !== 16) {
    throw new LoganDecodeError(`AES key must be 16 bytes, got ${opts.key.length}`);
  }
  if (opts.iv.length !== 16) {
    throw new LoganDecodeError(`AES IV must be 16 bytes, got ${opts.iv.length}`);
  }

  const blocks: DecodedBlock[] = [];
  const truncatedBlockIndices: number[] = [];
  const skippedBlocks: Array<{ blockIndex: number; reason: string }> = [];
  const decoder = new TextDecoder('utf-8', { fatal: false });
  let position = 0;
  let blockCounter = 0;

  while (position < bytes.length) {
    if (bytes[position++] !== ENCRYPT_CONTENT_START) continue;

    if (bytes.length - position < LENGTH_BYTES) {
      // 尾部不完整的 header 视为文件结束，best-effort 不抛
      break;
    }

    const encryptedLength = readBigEndianInt(bytes, position);
    position += LENGTH_BYTES;
    if (encryptedLength <= 0 || encryptedLength > bytes.length - position) {
      // 长度异常通常是误命中 0x01 magic byte（容错跳过下一个 0x01）
      position -= LENGTH_BYTES; // 回退，让外循环继续扫描
      continue;
    }

    const encrypted = bytes.subarray(position, position + encryptedLength);
    position += encryptedLength;
    const blockIndex = blockCounter++;

    let compressed: Uint8Array;
    try {
      compressed = cbc(opts.key, opts.iv).decrypt(encrypted);
    } catch (pkcs7Err) {
      try {
        compressed = cbc(opts.key, opts.iv, { disablePadding: true }).decrypt(encrypted);
      } catch (noPaddingErr) {
        skippedBlocks.push({
          blockIndex,
          reason: `AES decrypt failed: ${(pkcs7Err as Error).message}; NoPadding: ${(noPaddingErr as Error).message}`,
        });
        continue;
      }
    }

    let plain: Uint8Array;
    let isTruncated = false;
    try {
      plain = gunzipWhole(compressed);
    } catch (gzErr) {
      const partial = partialGunzipUpToLastNewline(compressed);
      if (partial.length === 0) {
        skippedBlocks.push({
          blockIndex,
          reason: `gunzip: ${(gzErr as Error).message}`,
        });
        continue;
      }
      plain = partial;
      isTruncated = true;
    }

    if (plain.length === 0) continue;

    blocks.push({
      text: decoder.decode(plain),
      sourceFile,
      blockIndex,
    });
    if (isTruncated) truncatedBlockIndices.push(blockIndex);
  }

  if (blocks.length === 0) {
    throw new LoganDecodeError(
      `No Logan blocks could be decoded in ${sourceFile}. Total skipped: ${skippedBlocks.length}` +
        (skippedBlocks[0] ? `. First reason: ${skippedBlocks[0].reason}` : ''),
    );
  }
  return { blocks, truncatedBlockIndices, skippedBlocks };
}

function readBigEndianInt(bytes: Uint8Array, offset: number): number {
  const b0 = bytes[offset];
  const b1 = bytes[offset + 1];
  const b2 = bytes[offset + 2];
  const b3 = bytes[offset + 3];
  if (b0 === undefined || b1 === undefined || b2 === undefined || b3 === undefined) {
    throw new LoganDecodeError(`Out-of-bounds read at offset ${offset}`);
  }
  return ((b0 & 0xff) << 24) | ((b1 & 0xff) << 16) | ((b2 & 0xff) << 8) | (b3 & 0xff);
}

function gunzipWhole(compressed: Uint8Array): Uint8Array {
  const chunks: Uint8Array[] = [];
  let errorThrown: unknown = null;
  const gunzip = new Gunzip((chunk) => chunks.push(chunk));
  try {
    gunzip.push(compressed, true);
  } catch (err) {
    errorThrown = err;
  }
  if (errorThrown) throw errorThrown;
  return mergeChunks(chunks);
}

function partialGunzipUpToLastNewline(compressed: Uint8Array): Uint8Array {
  const chunks: Uint8Array[] = [];
  const gunzip = new Gunzip((chunk) => chunks.push(chunk));
  try {
    gunzip.push(compressed, true);
  } catch {
    // 吞掉 - 我们要的是已经 emit 的部分
  }
  const merged = mergeChunks(chunks);
  return truncateToLastNewline(merged);
}

function truncateToLastNewline(bytes: Uint8Array): Uint8Array {
  for (let i = bytes.length - 1; i >= 0; i--) {
    if (bytes[i] === NEWLINE) return bytes.subarray(0, i + 1);
  }
  return new Uint8Array(0);
}

function mergeChunks(chunks: Uint8Array[]): Uint8Array {
  if (chunks.length === 0) return new Uint8Array(0);
  if (chunks.length === 1) return chunks[0]!;
  const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const merged = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return merged;
}

/**
 * 按 KeyPair 顺序尝试解密；isPlaceholder 的 pair 默认跳过。返回第一个能解出至少
 * 一个 block 的 KeyPair 与解码结果。同一文件内单个 block 损坏不会让流程退到下一个
 * key——key 选择以"能否解出第一个有效 block"为准。
 */
export function decodeWithAnyKey(
  bytes: Uint8Array,
  sourceFile: string,
  keyPairs: Array<{ name: string; key: Uint8Array; iv: Uint8Array; isPlaceholder: boolean }>,
): { keyName: string; result: DecodeResult } {
  const errors: string[] = [];
  for (const pair of keyPairs) {
    if (pair.isPlaceholder) {
      errors.push(`${pair.name}: skipped (placeholder)`);
      continue;
    }
    try {
      const result = decodeLoganFile(bytes, sourceFile, { key: pair.key, iv: pair.iv });
      return { keyName: pair.name, result };
    } catch (err) {
      errors.push(`${pair.name}: ${(err as Error).message}`);
    }
  }
  throw new LoganDecodeError(
    `No KeyPair could decode ${sourceFile}. Attempts:\n  ${errors.join('\n  ')}`,
  );
}
