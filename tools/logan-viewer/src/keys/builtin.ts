// AES-128-CBC 解密所用 key / IV。
//
// debug pair 与仓库 app/build.gradle.kts 中 buildConfigField("LOGAN_AES_KEY"...)
// 的 debug 默认值一致，已经公开在仓库中。
//
// release pair 与仓库 musicfree-android-signing 的 LOGAN_AES_KEY/IV 一致。
// 设计上 release key 公开是可接受的（设计 spec "Release key 内置的 trade-off"），
// 业务侧已禁止把 token / 原文输入 / 含用户名的绝对路径写入 fields。
//
// 也可以通过环境变量在 CLI 临时覆盖（见 keyPairFromEnv）。

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

export const RELEASE_KEY_PAIR: KeyPair = {
  name: 'release',
  key: asAscii('RELEASE_KEY', 'KhpnCPdKzFbgOgVe'),
  iv: asAscii('RELEASE_IV', 'QM1eO1qXTzvpuQvf'),
  isPlaceholder: false,
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
