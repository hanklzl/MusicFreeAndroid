// 命令行解码工具。输入 feedback zip，stdout 一个 JSON 数组（全部 RawLogEvent）。
// 元数据（manifest 摘要、每个 logan 文件的 block 数等）写到 stderr。
//
// 行为对齐 tools/logan/decode-logan.sh：
//   - 默认尝试 builtin debug pair（release pair 是 placeholder 会被跳过）。
//   - 如果设置了 LOGAN_AES_KEY / LOGAN_AES_IV 环境变量，则优先使用。

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import { parseDecodedText } from '../src/format/eventParser.js';
import { decodeWithAnyKey } from '../src/format/loganDecoder.js';
import { readFeedbackZip } from '../src/format/zipReader.js';
import { BUILTIN_KEY_PAIRS, DEBUG_KEY_PAIR, keyPairFromEnv } from '../src/keys/builtin.js';
import type { RawLogEvent } from '../src/model/types.js';

const input = process.argv[2];
if (!input) {
  console.error('Usage: npm run decode -- <feedback-zip>');
  process.exit(2);
}

const envOverride = keyPairFromEnv('env', 'LOGAN_AES_KEY', 'LOGAN_AES_IV');
const keyPairs = envOverride ? [envOverride, DEBUG_KEY_PAIR] : BUILTIN_KEY_PAIRS;

const bytes = await readFile(path.resolve(input));
const { manifest, loganFiles } = await readFeedbackZip(bytes);

const allEvents: RawLogEvent[] = [];
let totalSkipped = 0;
let totalBlocks = 0;
let totalTruncated = 0;

for (const file of loganFiles) {
  const { keyName, result } = decodeWithAnyKey(file.bytes, file.name, keyPairs);
  for (const block of result.blocks) {
    const parsed = parseDecodedText(block.text);
    totalSkipped += parsed.skipped.length;
    for (const event of parsed.events) {
      allEvents.push(event);
    }
  }
  totalBlocks += result.blocks.length;
  totalTruncated += result.truncatedBlockIndices.length;
  console.error(
    `# ${file.name}: key=${keyName} blocks=${result.blocks.length} truncated=${result.truncatedBlockIndices.length}`,
  );
}

console.error(
  `# manifest: app=${manifest.applicationId} v${manifest.versionName}(${manifest.versionCode}) ` +
    `buildType=${manifest.buildType} session=${manifest.sessionId}`,
);
console.error(
  `# summary: files=${loganFiles.length} blocks=${totalBlocks} events=${allEvents.length} ` +
    `truncatedBlocks=${totalTruncated} skippedLines=${totalSkipped}`,
);

process.stdout.write(JSON.stringify(allEvents, null, 2));
process.stdout.write('\n');
