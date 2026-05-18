// 从同源 release/version.json 拉最新版本号 + 各 ABI 下载链接
(async () => {
  const placeholder = document.getElementById('version-info');
  const arm = document.getElementById('dl-arm64');
  const x64 = document.getElementById('dl-x86_64');
  try {
    const r = await fetch('/MusicFreeAndroid/release/version.json', { cache: 'no-store' });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const v = await r.json();
    if (placeholder) {
      const date = new Date(v.releasedAt).toLocaleDateString('zh-CN');
      placeholder.textContent = `最新版本 v${v.version}（${date}）`;
    }
    if (arm && v.variants?.['arm64-v8a']?.download?.[0]) {
      arm.href = v.variants['arm64-v8a'].download[0];
      arm.textContent = `下载 arm64 APK · v${v.version}`;
    }
    if (x64 && v.variants?.['x86_64']?.download?.[0]) {
      x64.href = v.variants['x86_64'].download[0];
      x64.textContent = `下载 x86_64 APK · v${v.version}`;
    }
  } catch (e) {
    if (placeholder) {
      placeholder.textContent = '无法获取最新版本信息，请直接到 GitHub Releases 选择对应 ABI。';
    }
    console.warn('version.json 加载失败', e);
  }
})();
