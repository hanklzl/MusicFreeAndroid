# 发布流程

本仓库的发布流水线由 `.github/workflows/android-release-apk.yml` 驱动，对外只暴露一个触发点：**推送 `vX.Y.Z` tag 到 GitHub**。

## 一次性配置

### GitHub release environment secrets

在仓库 Settings → Environments → `release` 内配置：

| Secret | 用途 |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | base64 编码的签名 keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_RELEASE_KEY_ALIAS` | key 别名 |
| `ANDROID_RELEASE_KEY_PASSWORD` | key 密码 |
| `LOGAN_AES_KEY` | 16 字节，Logan 日志加密 |
| `LOGAN_AES_IV` | 16 字节，Logan 日志 IV |
| `ANTHROPIC_API_KEY` | Claude API，用于 release notes 摘要；失败时回退到纯 commit 列表，不阻塞 |

### `gh-pages` 分支

首次 tag push 时 CI 自动 `git checkout --orphan gh-pages` 创建分支并写入 `release/version.json`。无需手工初始化。

### 版本号 versionCode 公式

`versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`。例：`v1.2.3` → `10203`。

## 日常发布步骤

1. 决定语义化版本号 vX.Y.Z。
2. 修改 `version.properties`：
   ```properties
   versionCode=10203
   versionName=1.2.3
   ```
3. **本地干跑 preflight**：
   ```bash
   bash scripts/release/preflight.sh v1.2.3
   ```
   通过后再继续；任何报错都不要 push。
4. `git add version.properties && git commit -m "chore(release): bump to v1.2.3"`
5. `git tag v1.2.3`
6. `git push origin main && git push origin v1.2.3`
7. 观察 [GitHub Actions](https://github.com/hanklzl/MusicFreeAndroid/actions) 完成；验证：
   - Release 已创建，notes 完整
   - `main` 上有 `docs(changelog): release v1.2.3 [skip ci]` 自动 commit
   - `gh-pages/release/version.json` 已更新
   - jsdelivr 镜像可拉：
     ```bash
     curl -I https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json
     ```
8. 装一台测试机冷启动验证启动 dialog → 下载 → 安装链路（首次 release 时必须做）。

## 本地干跑 CI step

每条 step 与 `.github/workflows/android-release-apk.yml` 内的同名 step 一一对应，命名前缀 `[dry] `。所有命令在仓库根目录执行。

### `[dry] Validate version consistency`

```bash
TAG=v1.2.3 bash -c '
  expected="${TAG#v}"
  actual=$(awk -F= "/^versionName/{print \$2}" version.properties | tr -d "[:space:]")
  [ "$expected" = "$actual" ] || { echo "::error::tag $TAG vs versionName $actual mismatch"; exit 1; }
  echo "OK: $TAG ↔ versionName=$actual"
'
```

### `[dry] Build Release APK`

在本机配置一份**未入库** `.env.release.local`（`.gitignore` 已排除）：

```bash
export ANDROID_RELEASE_KEYSTORE_PATH=/abs/path/release.jks
export ANDROID_RELEASE_STORE_PASSWORD=...
export ANDROID_RELEASE_KEY_ALIAS=...
export ANDROID_RELEASE_KEY_PASSWORD=...
export LOGAN_AES_KEY=0123456789abcdef
export LOGAN_AES_IV=abcdef0123456789
```

```bash
source .env.release.local
./gradlew clean :app:assembleRelease --no-daemon
ls -lh app/build/outputs/apk/release/app-release.apk
```

### `[dry] Compute APK sha256 + size`

```bash
APK=app/build/outputs/apk/release/app-release.apk
sha256sum "$APK" | awk '{print $1}'
wc -c < "$APK"
```

### `[dry] Generate release notes`

```bash
PREV=$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)
CURR=HEAD
bash scripts/release/generate-notes.sh "$PREV" "$CURR" > /tmp/release_notes.md
less /tmp/release_notes.md
```

本地不愿调 LLM：`unset ANTHROPIC_API_KEY`，走 fallback。

### `[dry] Prepend CHANGELOG.md`

```bash
bash scripts/release/prepend-changelog.sh /tmp/release_notes.md vX.Y.Z --dry-run \
    | diff CHANGELOG.md -
```

`--dry-run` 输出"假如执行后的 CHANGELOG.md 全文"，与现状 diff 出新插入段。**本地不要去掉 `--dry-run` 真写文件**——这步只在 CI 内执行，避免与 CI 重复提交。

### `[dry] Build version.json`

```bash
bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --apk "$APK" \
    --apk-name "MusicFreeAndroid-v1.2.3.apk" \
    --notes /tmp/release_notes.md \
    > /tmp/version.json
jq . /tmp/version.json   # 语法校验
```

### `[dry] Full pre-flight`

```bash
bash scripts/release/preflight.sh v1.2.3
```

脚本串调上述 6 个 step，任一非 0 即停。**push tag 前跑通 preflight 是硬性约束**。

### 不可本地干跑的 step

| Step | 原因 | 替代验证 |
|---|---|---|
| `gh release create` | 真创建会污染线上 release | 在 fork 上用 `--draft` 跑一次 |
| `git push origin main`（CHANGELOG） | 真 push 污染 main | dry-run diff 已足够 |
| `git push origin gh-pages` | 同上 | 本地切到 gh-pages 看文件结构即可 |

## 回滚

```bash
# 删 tag
git push origin :v1.2.3
git tag -d v1.2.3
# 删 release
gh release delete v1.2.3
# revert CHANGELOG commit
git revert <changelog-commit-sha>
git push origin main
# 删 gh-pages 对应 commit
git push --force-with-lease origin <gh-pages-prev-sha>:gh-pages
```

## 故障排查

| 现象 | 排查 |
|---|---|
| `Validate version consistency` 红色失败 | 校对 `version.properties` 与 tag |
| LLM 摘要为空 | 不阻塞 release；可手工编辑 `CHANGELOG.md` 补摘要 |
| CHANGELOG push 失败 | main 并发推送；按 workflow warning log 手工 cherry-pick |
| 客户端拉不到 `version.json` | 检查 `gh-pages` 分支；jsdelivr 缓存最多 12h；强制刷新 `https://purge.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json` |
| 用户装不上 | 检查 applicationId（release vs debug 不可覆盖）；用户系统设置「允许此应用安装未知来源」未开 |
| 弹窗"安装包校验失败" | sha256 不匹配；通常是 jsdelivr 缓存旧 APK，命令同上强刷 |
