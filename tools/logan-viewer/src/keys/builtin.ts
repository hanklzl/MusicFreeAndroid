// AES-128-CBC 解密所用 key / IV。
//
// debug pair 与仓库 app/build.gradle.kts 中 buildConfigField("LOGAN_AES_KEY"...)
// 的 debug 默认值一致，已经公开在仓库中。
//
// release pair 默认是占位字符串（isPlaceholder=true），合并前需要：
//   (a) 由开发者手动改成真实的 LOGAN_AES_KEY / LOGAN_AES_IV 再 commit；或
//   (b) 通过同名环境变量在 CLI / 构建时覆盖（与 tools/logan/decode-logan.sh 行为一致）。
//
// loganDecoder.tryDecodeWithAnyKey() 会按 BUILTIN_KEY_PAIRS 顺序尝试解密，
// isPlaceholder=true 的 pair 默认跳过，避免拿无效 key 浪费时间。

const ASCII_KEY_LENGTH = 16;

function asAscii(label: string, value: string): Uint8Array {
  if (value.length !== ASCII_KEY_LENGTH) {
    throw new Error(
      `${label} must be exactly ${ASCII_KEY_LENGTH} ASCII chars (got ${value.length})`,
    );
  }
  return new TextEncoder().encode(value);
}

export interface KeyPair {
  name: string;
  key: Uint8Array;
  iv: Uint8Array;
  isPlaceholder: boolean;
}

export const DEBUG_KEY_PAIR: KeyPair = {
  name: 'debug',
  key: asAscii('DEBUG_KEY', '0123456789abcdef'),
  iv: asAscii('DEBUG_IV', 'abcdef0123456789'),
  isPlaceholder: false,
};

const RELEASE_KEY_PLACEHOLDER = '****REPLACE_ME**';
const RELEASE_IV_PLACEHOLDER = '****REPLACE_ME**';

export const RELEASE_KEY_PAIR: KeyPair = {
  name: 'release',
  key: asAscii('RELEASE_KEY', RELEASE_KEY_PLACEHOLDER),
  iv: asAscii('RELEASE_IV', RELEASE_IV_PLACEHOLDER),
  isPlaceholder: true,
};

export const BUILTIN_KEY_PAIRS: KeyPair[] = [DEBUG_KEY_PAIR, RELEASE_KEY_PAIR];

export function keyPairFromEnv(name: string, keyEnv: string, ivEnv: string): KeyPair | null {
  const key = process.env[keyEnv];
  const iv = process.env[ivEnv];
  if (!key || !iv) return null;
  return {
    name,
    key: asAscii(keyEnv, key),
    iv: asAscii(ivEnv, iv),
    isPlaceholder: false,
  };
}
