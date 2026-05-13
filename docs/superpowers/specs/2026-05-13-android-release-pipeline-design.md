# Android 发布流水线与内置更新检查设计

- **状态**：当前规范（草案）
- **日期**：2026-05-13
- **适用范围**：新模块 `:updater`；`.github/workflows/android-release-apk.yml`；仓库根 `version.properties` / `CHANGELOG.md` / `RELEASE.md`；`scripts/release/`；`gh-pages` orphan 分支
- **关系**：与同日的 [2026-05-13-release-rebrand-and-ci-design.md](2026-05-13-release-rebrand-and-ci-design.md) 平级，互不替换。本 spec 聚焦"打 tag → 自动发包 → 客户端拉取并安装"的发布闭环，前者聚焦品牌化与 CI 收敛。

## 1. 背景与目标

仓库当前 `android-release-apk.yml` 已具备 tag 触发构建 + 签名 + `gh release create --generate-notes` 发布的能力，但只覆盖发布链路的一半，仍缺：

1. tag 与 `versionName/versionCode` 一致性校验（目前 build.gradle 写死 `1.0`）。
2. 有意义的 release notes（`--generate-notes` 在无 PR 流程的项目里输出几乎为空）。
3. 客户端无任何"有新版本"感知；用户必须主动去 GitHub 看 release。

本次改造在不动既有签名/Logan 链路前提下补齐三件事：

1. **CI 强校验 + 自动归档**：`version.properties` ↔ tag 双重校验；CI 在 release 创建后自动追加 `CHANGELOG.md` 到 `main` 并发布 `version.json` 到 `gh-pages` 分支。
2. **客户端内置更新检查**：新模块 `:updater` 在冷启动后台拉取 `version.json`，按既定 UX 弹启动 dialog（含"跳过本版本"）、设置页红点、模态进度下载、FileProvider 安装。
3. **可观测的发布工作流**：根目录新增 `RELEASE.md` runbook 与 `CHANGELOG.md` 历史档；`scripts/release/` 拆出本地与 CI 共享的脚本，本地可干跑全部 CI step。

## 2. 当前状态（事实基线）

- `app/build.gradle.kts`：`versionCode = 1`、`versionName = "1.0"`，硬编码；签名 / Logan 走 env 变量分支，逻辑 OK。
- `.github/workflows/android-release-apk.yml`：tag (`v*`) / manual dispatch 触发；`build-release-apk` job 完成签名构建并上传 artifact；`publish-github-release` job 用 `gh release create … --generate-notes` 创建 release，不带任何摘要计算。
- `app/src/main/AndroidManifest.xml`：已声明 `FileProvider`，authority `${applicationId}.feedback-files`，用于反馈截图分享；XML 资源 `feedback_file_paths.xml` 只暴露 `cache-path: feedback/`。本次新增的 APK 安装 FileProvider 与之并存、authority 独立。
- `app/src/main/res/values/strings.xml`、`app/src/main/res/xml/`：无 `file_paths.xml`、无 `REQUEST_INSTALL_PACKAGES` 权限声明。
- 仓库根：无 `version.properties` / `CHANGELOG.md` / `RELEASE.md` / `scripts/release/` 目录。
- `.gitignore`：未排除 `.env.release.local`（本 spec §6.3 要求新增）。
- 分支：无 `gh-pages` 分支（实施阶段首次 push 时由 CI 切 orphan 创建）。
- 参考实现：`../MusicFree/src/utils/checkUpdate.ts` 与 `../MusicFree/src/hooks/useCheckUpdate.ts`——RN 原版采用"多镜像 version.json + 启动 dialog + 跳过版本"的模式，本 spec 沿用其交互语义但改造为 Kotlin/Compose 实现。

## 3. 设计

### 3.1 架构与模块边界

#### 3.1.1 新增模块 `:updater`

依赖方向：`:app → :updater → :core`，与现有单向依赖一致。`:updater` 不依赖 `:data` / `:player` / `:plugin` / `:feature:*`。

| 子目录 | 职责 |
|---|---|
| `api/UpdateClient.kt` | OkHttp 多镜像顺序拉取 `version.json`，5s connect / 10s read 超时，任一成功即返回 |
| `model/UpdateInfo.kt` | `@Serializable`，对应 §3.3 字段契约 |
| `checker/UpdateChecker.kt` | 比较版本、读写 skipVersion、对外暴露 `StateFlow<UpdateState>`，提供 `checkOnLaunch()` 与 `checkManually()` |
| `downloader/ApkDownloader.kt` | 单任务 OkHttp 下载（可顺序尝试 `info.download[]`）、进度回调、流式 sha256、取消、cacheDir 写入 |
| `installer/ApkInstaller.kt` | FileProvider URI + `ACTION_VIEW` + `REQUEST_INSTALL_PACKAGES` 引导 |
| `store/UpdatePreferences.kt` | DataStore：`skip_version` / `last_checked_at` / `last_seen_version` |
| `di/UpdaterModule.kt` | Hilt `@Singleton` 绑定 |

#### 3.1.2 `:app` 侧挂载点

- `MusicFreeApplication.onCreate()`：`applicationScope.launch(Dispatchers.IO) { updateChecker.checkOnLaunch() }`，不阻塞冷启动。
- `MainActivity`（或根 Composable）：订阅 `updateChecker.state`，第一次进入 `Available(skipped=false)` 时弹启动 dialog，**单次冷启动只弹一次**。
- `:feature:settings` 的"检查更新"行：复用同一 `UpdateChecker`，调 `checkManually()`，**忽略 skipVersion**；行末根据 state 渲染红点或当前 versionName。
- 抽屉「设置」入口红点：订阅 `updateChecker.state.hasUnreadAvailableUpdate` 派生属性，与设置行红点共用同一数据源。

#### 3.1.3 状态机 `UpdateState`

```kotlin
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Instant) : UpdateState
    data class Available(val info: UpdateInfo, val skipped: Boolean) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float, val bytes: Long, val total: Long) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState
    data class Failed(val info: UpdateInfo?, val cause: UpdateError) : UpdateState
}

enum class UpdateError { Network, SchemaUnsupported, SizeMismatch, Sha256Mismatch, Canceled, InstallBlocked }
```

派生：`hasUnreadAvailableUpdate = state is Available && !state.skipped`。

#### 3.1.4 明确不在范围

- 渐进发布 / 灰度（只发布最新 release）
- 增量 patch / 差分包（整包替换）
- 强制升级最低版本（min supported version）
- 应用商店上架链路（GitHub Release 是唯一渠道）
- 后台静默下载、WiFi-only 限制
- 安装失败自动重试（由用户手动重试）

### 3.2 CI 流水线改造

修改 `.github/workflows/android-release-apk.yml`。job 拓扑：

```
build-release-apk          (tag / schedule / dispatch)
  ├─ Validate version consistency      (tag only)
  ├─ Existing setup / decode keystore / build / name APK / upload artifact
  └─ Compute APK sha256 + size         (写 step outputs)
   └─→ publish-github-release          (tag only)
         ├─ Generate notes & summary   (LLM, 失败 fallback)
         ├─ Prepend CHANGELOG.md + push main  (失败仅 warning)
         ├─ Upload APK to release
         └─→ publish-version-manifest  (tag only, gh-pages)
```

#### 3.2.1 `version.properties`（仓库根新增）

```properties
versionCode=10203
versionName=1.2.3
```

`app/build.gradle.kts` 改造：

```kotlin
val versionProps = java.util.Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
defaultConfig {
    versionCode = versionProps.getProperty("versionCode").toInt()
    versionName = versionProps.getProperty("versionName")
}
```

**不接受 CI 注入版本号**——本地 build 与 CI build 必须读同一份文件，避免"本机装的版本号与 release 包不一致"。

放仓库根而不是 `app/`：未来若加 wear/tv variant 可复用同一份。

#### 3.2.2 `Validate version consistency` step（tag 路径前置）

```bash
expected="${GITHUB_REF_NAME#v}"
actual=$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')
[ "$expected" = "$actual" ] || {
    echo "::error::tag $GITHUB_REF_NAME vs versionName $actual mismatch"
    exit 1
}
```

仅 `github.ref_type == 'tag'` 时跑。nightly/dispatch 不触发该校验。

#### 3.2.3 APK sha256 + size 计算

`build-release-apk` job 末尾新增 step `Compute APK sha256 + size`，写入 step outputs：

```yaml
- name: Compute APK sha256 + size
  id: apk-meta
  run: |
    apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_name }}"
    echo "sha256=$(sha256sum "$apk" | awk '{print $1}')" >> "$GITHUB_OUTPUT"
    echo "size=$(wc -c < "$apk")" >> "$GITHUB_OUTPUT"
```

job outputs 暴露 `apk-sha256` 与 `apk-size`，供下游 `publish-version-manifest` 消费。

#### 3.2.4 Release notes 生成

`publish-github-release` job 新增 step `Generate release notes`（在 `Upload APK to release` 之前）：

1. **计算 commit 范围**：`prev=$(git describe --tags --abbrev=0 ${GITHUB_REF_NAME}^ 2>/dev/null || git rev-list --max-parents=0 HEAD)`；范围 `${prev}..${GITHUB_REF_NAME}`。
2. **调用脚本**：`bash scripts/release/generate-notes.sh "$prev" "$GITHUB_REF_NAME" > release_notes.md`。
3. 脚本内部：
   - `git log --pretty=format:'%H%x09%s' "$prev..$head"`，按 conventional commit 前缀分类（`feat:` / `fix:` / `docs:` / `refactor:` / `perf:` / `test:` / `chore:` / `merge:` / 其它）。
   - 若 env `ANTHROPIC_API_KEY` 已设置，POST `https://api.anthropic.com/v1/messages`，model `claude-haiku-4-5`，30s 超时，prompt 固定为中文 system："你是技术 release notes 编辑。基于给定 commit 列表写一段不超过 200 字的中文版本亮点摘要，不重复列出每条 commit，只点出对用户最有感知的变化"。输出注入 markdown 片段头部。
   - 失败 / 超时 / 非 2xx / 空输出 → fallback 为空摘要（保留 commit 分类列表）；脚本通过 `>&2` 写 warning，**返回 0**。
4. **markdown 片段格式**：

   ```markdown
   ## [v1.2.3] - 2026-05-13

   {LLM 摘要，失败时省略此段}

   ### 变更详情
   #### 新功能
   - feat(xxx): ... (abcd123)
   #### 修复
   - fix(xxx): ... (efgh456)
   ...
   ```

5. **作为 artifact 上传**：step 末尾 `actions/upload-artifact@v7` 把 `release_notes.md` 以 name `release-notes` 上传，retention 1 day；下游 `publish-version-manifest` job 通过 `actions/download-artifact@v7` 取回。`publish-github-release` job 自身后续的 CHANGELOG / `gh release create` step 在同一 runner 上下文直接读本地文件，无需重新下载。

#### 3.2.5 CHANGELOG.md 自动追加

紧接着 `Prepend CHANGELOG.md + push main` step：

```bash
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git fetch origin main
git checkout -B main origin/main
bash scripts/release/prepend-changelog.sh release_notes.md "$GITHUB_REF_NAME"
git add CHANGELOG.md
git commit -m "docs(changelog): release $GITHUB_REF_NAME [skip ci]"
git push origin main || {
    git pull --rebase origin main
    git push origin main || echo "::warning::CHANGELOG push failed, manual sync required"
}
```

要点：
- `prepend-changelog.sh` 在 `<!-- next-release -->` 标记之后插入片段，并把当前 `## [Unreleased]` 一节保留在新片段之上。
- `[skip ci]` 防止反向触发 release workflow；其他依赖 `paths` 过滤的 workflow 不受影响（CHANGELOG.md 不在任何现有 workflow 的 paths 过滤里）。
- 并发推送失败 → 重试一次 → 仍失败仅写 warning，**不阻塞 release**（CI 已把 release 建好；CHANGELOG 后续手工 cherry-pick）。
- permissions: `contents: write`。

#### 3.2.6 GitHub Release 创建

把现有 `gh release create … --generate-notes` 替换为 `--notes-file release_notes.md`：

```bash
apk_path="release-apk/${{ needs.build-release-apk.outputs.apk-name }}"
if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
    gh release upload "$GITHUB_REF_NAME" "$apk_path" --clobber
else
    gh release create "$GITHUB_REF_NAME" "$apk_path" \
        --title "$GITHUB_REF_NAME" \
        --notes-file release_notes.md
fi
```

#### 3.2.7 `publish-version-manifest` job（新增，发布到 gh-pages）

```yaml
publish-version-manifest:
  name: Publish version manifest
  needs: [build-release-apk, publish-github-release]
  if: github.event_name == 'push' && github.ref_type == 'tag'
  runs-on: ubuntu-latest
  permissions:
    contents: write
  steps:
    - name: Checkout gh-pages
      uses: actions/checkout@v6
      with:
        ref: gh-pages
      continue-on-error: true   # 首次跑时分支不存在

    - name: Init gh-pages if missing
      run: |
        if ! git rev-parse --verify gh-pages >/dev/null 2>&1; then
            git checkout --orphan gh-pages
            git rm -rf . 2>/dev/null || true
            echo "Release manifest branch" > README.md
            git add README.md
            git -c user.name="github-actions[bot]" \
                -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
                commit -m "chore: init gh-pages"
        fi

    - name: Checkout scripts from default branch
      uses: actions/checkout@v6
      with:
        path: source
        ref: ${{ github.ref }}

    - name: Download release notes artifact
      uses: actions/download-artifact@v7
      with:
        name: release-notes
        path: source

    - name: Build version.json
      run: |
        mkdir -p release
        bash source/scripts/release/build-version-json.sh \
            --version "${GITHUB_REF_NAME#v}" \
            --version-code "$(awk -F= '/^versionCode/{print $2}' source/version.properties | tr -d '[:space:]')" \
            --tag "$GITHUB_REF_NAME" \
            --sha256 "${{ needs.build-release-apk.outputs.apk-sha256 }}" \
            --size "${{ needs.build-release-apk.outputs.apk-size }}" \
            --apk-name "${{ needs.build-release-apk.outputs.apk-name }}" \
            --notes source/release_notes.md \
            > release/version.json

    - name: Commit & push gh-pages
      run: |
        git -c user.name="github-actions[bot]" \
            -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
            add release/version.json
        git -c user.name="github-actions[bot]" \
            -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
            commit -m "chore(release): publish ${GITHUB_REF_NAME}"
        git push origin gh-pages
```

### 3.3 `version.json` 数据契约

发布到 `gh-pages` 分支的 `release/version.json`，UTF-8 无 BOM：

```json
{
  "schemaVersion": 1,
  "version": "1.2.3",
  "versionCode": 10203,
  "releasedAt": "2026-05-13T18:00:00Z",
  "download": [
    "https://github.com/hanklzl/MusicFreeAndroid/releases/download/v1.2.3/MusicFreeAndroid-v1.2.3.apk",
    "https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@v1.2.3/release/MusicFreeAndroid-v1.2.3.apk"
  ],
  "size": 23456789,
  "sha256": "f3a8...c901",
  "changeLog": [
    "新功能 1",
    "修复 2"
  ],
  "releaseNotesUrl": "https://github.com/hanklzl/MusicFreeAndroid/releases/tag/v1.2.3"
}
```

#### 3.3.1 字段语义

| 字段 | 必选 | 说明 |
|---|---|---|
| `schemaVersion` | ✅ | 整数，当前固定 `1`；客户端遇到不识别版本时降级为"提示去 GitHub 下载"，不崩溃。前向兼容钩子 |
| `version` | ✅ | `MAJOR.MINOR.PATCH`，与 `versionName` 一致 |
| `versionCode` | ✅ | 客户端比较的**首选字段**，单调递增 |
| `releasedAt` | ✅ | ISO 8601 UTC，设置页用相对时间展示 |
| `download` | ✅ | URL 数组，客户端按顺序尝试下载 APK |
| `size` | ✅ | 字节数，下载前与 Content-Length 预校验 |
| `sha256` | ✅ | 下载完成后流式校验；失败删文件、报错 |
| `changeLog` | ✅ | 字符串数组（启动 dialog 内展示），最多 8 行；超出由 CI 截断 |
| `releaseNotesUrl` | ✅ | "查看完整说明"按钮跳转目标 |

#### 3.3.2 客户端版本比较

- 首选 `localVersionCode < remote.versionCode` ⇒ 有更新。
- 远端 versionCode 缺失或 0 → fallback 到 semver 比较 `version`。
- 远端 ≤ 本地 → `UpToDate`，并清空 `skip_version`（防止旧 skip 锁死后续）。

#### 3.3.3 镜像区分

- `download[]`：客户端**下载 APK** 时尝试顺序。
- `version.json` 自身镜像列表：**硬编码于 `UpdateClient`**，不进 json：
  1. `https://raw.githubusercontent.com/hanklzl/MusicFreeAndroid/gh-pages/release/version.json`
  2. `https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json`

  逐一尝试，5s connect / 10s read，任一成功即返回。

### 3.4 客户端流程

#### 3.4.1 启动检查

`MusicFreeApplication.onCreate()` 内：

```kotlin
applicationScope.launch(Dispatchers.IO) {
    updateChecker.checkOnLaunch()
}
```

- 不阻塞 UI。
- 频率：每次冷启动一次（`version.json` < 1KB，可接受）。
- 全部镜像失败 → `Failed(Network)`；启动 dialog **不弹**；设置页主动检查才会显式 toast。
- Debug 包（`BuildConfig.DEBUG = true`）跳过启动 dialog 但保留设置页主动检查能力，避免开发期打扰。

#### 3.4.2 启动 Dialog

`MainActivity` 根 Composable：

```kotlin
LaunchedEffect(Unit) {
    updateChecker.state
        .filterIsInstance<UpdateState.Available>()
        .filter { !it.skipped }
        .first()
        .let { available -> showUpdateDialog = available.info }
}
```

Dialog 内容：

- 标题：「发现新版本 v{version}」
- 副标题：`releasedAt` 相对时间（"3 天前"）
- 正文：`changeLog` 滚动列表（最多 8 行）
- 三个按钮：
  - **下载并安装**（primary）→ 进入 `Downloading` → 模态进度对话框
  - **跳过此版本**（secondary）→ `UpdatePreferences.skip_version = remote.version` → 关闭；本次冷启动后不再弹；设置页主动检查仍弹
  - **稍后再说**（text）→ 关闭；下次冷启动会再弹

#### 3.4.3 下载进度对话框

模态 Composable Dialog：

- `LinearProgressIndicator` 绑定 `progress`
- 副文本：`{已下载}/{总大小} · {speed} KB/s`
- **取消**按钮 → `ApkDownloader.cancel()` → 删 `.part` → `state = Available(skipped=false)`，dialog 关闭

下载实现要点：

- 按 `info.download[]` 顺序尝试；前一个超时或 5xx 切下一个。
- 写入 `context.cacheDir / "updates" / "musicfree-${versionCode}.apk.part"`。
- 流式累计 sha256；下载完成后**先校验 sha256**，再原子改名去掉 `.part`。
- Content-Length ≠ `info.size` → `Failed(SizeMismatch)`，toast，删 `.part`。
- sha256 不匹配 → 删 `.part`、toast "安装包校验失败，请稍后重试"，`state = Available(skipped=false)`（不进入 `ReadyToInstall`）。
- 顺序：sha256 校验通过 → 改名为 `musicfree-${versionCode}.apk` → `state = ReadyToInstall(file)`。

#### 3.4.4 安装

`ReadyToInstall` 弹简短确认（"下载完成，立即安装？"），点确认调 `ApkInstaller.install(apkFile)`：

```kotlin
val authority = "${context.packageName}.updater-files"
val uri = FileProvider.getUriForFile(context, authority, apkFile)
if (!context.packageManager.canRequestPackageInstalls()) {
    state = Failed(info, InstallBlocked)   // UI 引导去系统设置授权
    return
}
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
```

`AndroidManifest.xml` 新增：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.updater-files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/updater_file_paths" />
</provider>
```

`app/src/main/res/xml/updater_file_paths.xml` 新增：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="updates" path="updates/" />
</paths>
```

`REQUEST_INSTALL_PACKAGES` 是 normal permission，无需运行时申请；`canRequestPackageInstalls()` 是 Settings 级别 toggle，未授予时引导 `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))`。

#### 3.4.5 设置页"检查更新"行

- 行布局：左标题"检查更新"；右副标题随 state 切换（当前 versionName / "v1.2.3 可用" / "检查中…" / "已是最新"）；行末红点条件 `hasUnreadAvailableUpdate`。
- 点击行 → `checkManually()`（忽略 skipVersion）。
- toast 行为：`Checking` / `UpToDate` / `Failed(Network)` 显式 toast；`Available` 不 toast，转走启动 dialog 流程。

#### 3.4.6 抽屉「设置」入口红点

`HomeDrawer` Composable 订阅 `updateChecker.state.hasUnreadAvailableUpdate`，红点与设置行同步。

### 3.5 数据持久化

`UpdatePreferences` 用单 instance `DataStore<Preferences>`，文件名 `updater_prefs`。

| Key | 类型 | 用途 |
|---|---|---|
| `skip_version` | String? | 用户最近一次"跳过"的版本号；远端版本号 > 它时清空 |
| `last_checked_at` | Long | 上次成功 check 的 epoch millis；设置页"上次检查时间"显示 |
| `last_seen_version` | String? | 最近一次看到的远端版本号；远端撤回时辅助清理 skipVersion |

不持久化：下载进度、APK 文件路径。每次启动重新校验 cacheDir 文件是否存在 + sha256 匹配。

### 3.6 错误处理与边界情况

| 场景 | 处理 |
|---|---|
| 全部 version.json 镜像超时 | `Failed(Network)`；启动 dialog 不弹；设置页主动检查 toast "网络错误，稍后重试" |
| `schemaVersion > 1` | `Failed(SchemaUnsupported)`；启动 dialog 改为"请前往 GitHub 下载新版" + 按钮跳 `releaseNotesUrl` |
| Content-Length ≠ `info.size` | `Failed(SizeMismatch)`；toast "安装包大小异常"；清 `.part` |
| sha256 不匹配 | `Failed(Sha256Mismatch)`；toast "安装包校验失败，请稍后重试"；清文件；`state = Available(skipped=false)` |
| `canRequestPackageInstalls() = false` | `Failed(InstallBlocked)`；dialog 指引"前往设置允许安装未知应用"，按钮启动 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` |
| 用户取消下载 | 删 `.part`；`state = Available(skipped=false)` |
| 远端版本号 < 本地（撤回） | `UpToDate`；清空 `skip_version` |
| `versionCode` 缺失或 0 | 降级 semver 比较 `version`；解析失败 → `Failed(SchemaUnsupported)` |
| Debug 包冷启动 | 不弹启动 dialog；设置页主动检查仍可用 |

### 3.7 测试策略

#### 3.7.1 单元测试

- `UpdateInfoSerializationTest`：合法 / 缺字段 / `schemaVersion > 1` 三种 JSON
- `UpdateCheckerTest`：fake `UpdateClient` 验证 5 个核心分支（无更新 / 有更新 / 跳过版本 / 远端撤回 / 全部镜像失败）
- `VersionCompareTest`：versionCode 优先、semver fallback、缺字段降级

#### 3.7.2 仪器测试 `androidTest`

- `ApkDownloaderInstrumentedTest`：MockWebServer 提供 1MB 假 APK；验证进度回调、cancel 清文件、Content-Length 不匹配、sha256 不匹配。
- `ApkInstallerInstrumentedTest`：验 FileProvider URI 生成 + intent 构造正确（不真正触发安装）。

#### 3.7.3 端到端 CI 验证

打临时 tag `v0.0.0-test`：

1. `version.properties` 与 tag 不一致 → build job fail；改对后通过。
2. GitHub Release 创建成功，notes 内容与 CHANGELOG 同片段一致。
3. `gh-pages/release/version.json` 已发布；`raw.githubusercontent` 与 `jsdelivr` 双镜像可拉到。
4. 验毕：删 tag + 删 release + revert CHANGELOG commit + 删 `gh-pages` 对应 commit。

#### 3.7.4 不在测试范围

- LLM 输出内容质量（人工 review）。
- 真实 GitHub API 限速行为。
- `:updater` 在所有第三方 ROM 上的安装弹窗（重点跑 Pixel / 小米 / 华为）。

### 3.8 文档产出

| 路径 | 类型 | 内容 | 维护方式 |
|---|---|---|---|
| `docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md` | 设计 spec | 即本份 | 手工 |
| `RELEASE.md`（根目录） | runbook | 操作手册、secrets、回滚、本地干跑、故障排查 | 手工 |
| `CHANGELOG.md`（根目录） | 历史 | 历次 release 摘要 + commit 详情 | CI 自动追加，仅初始化时人工 |
| `scripts/release/generate-notes.sh` | 共享脚本 | commit 范围 → 分类 → LLM → markdown | 手工 |
| `scripts/release/prepend-changelog.sh` | 共享脚本 | 把 markdown 片段插入 `<!-- next-release -->` 之后 | 手工 |
| `scripts/release/build-version-json.sh` | 共享脚本 | 拼装 `version.json`，输出 stdout | 手工 |
| `scripts/release/preflight.sh` | 共享脚本 | 串调上述脚本与 gradle build；本地干跑入口 | 手工 |

#### 3.8.1 `RELEASE.md` 结构

```markdown
# 发布流程

## 一次性配置
- GitHub release environment secrets：ANDROID_RELEASE_KEYSTORE_BASE64 / ANDROID_RELEASE_STORE_PASSWORD / ANDROID_RELEASE_KEY_ALIAS / ANDROID_RELEASE_KEY_PASSWORD / LOGAN_AES_KEY / LOGAN_AES_IV / ANTHROPIC_API_KEY
- gh-pages 首次初始化（首次 tag push 时 CI 自动建分支，无须手工）
- 版本号 versionCode 公式：MAJOR*10000 + MINOR*100 + PATCH

## 日常发布步骤
1. 决定版本号（semver）
2. 改 version.properties（versionCode + versionName）
3. 本地干跑 preflight：bash scripts/release/preflight.sh vX.Y.Z
4. commit "chore(release): bump to vX.Y.Z"
5. 打 tag：git tag vX.Y.Z
6. push commit + tag：git push origin main && git push origin vX.Y.Z
7. 观察 GitHub Actions 完成；验证：
   - Release 已创建，notes 完整
   - CHANGELOG.md 自动追加，main 上有 [skip ci] commit
   - gh-pages 上 release/version.json 已更新
   - jsdelivr 镜像可拉（`curl -I https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json`）
8. 装一台测试机冷启动验证启动 dialog → 下载 → 安装链路

## 本地干跑 CI step
（详见下节）

## 回滚
- 删 tag：git push origin :vX.Y.Z + git tag -d vX.Y.Z
- 删 release：gh release delete vX.Y.Z
- revert CHANGELOG commit：git revert <changelog-commit-sha> + push main
- 删 gh-pages 对应 commit：git push origin :gh-pages 后重新初始化，或 force-push 到上一个 commit

## 故障排查
- "version 不一致" → 校对 version.properties 与 tag
- "LLM 摘要为空" → 不阻塞 release；可手工编辑 CHANGELOG.md 补摘要
- "CHANGELOG push 失败" → main 有并发推送；按 warning log 手工 cherry-pick
- "客户端拉不到 version.json" → 检查 gh-pages 分支；jsdelivr 缓存最多 12h，可调 https://purge.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json
- "用户装不上" → 检查 applicationId（release vs debug 不可覆盖安装）；检查 REQUEST_INSTALL_PACKAGES 授权
```

#### 3.8.2 `RELEASE.md` 「本地干跑 CI step」子章节

每条 step 与 `.github/workflows/android-release-apk.yml` 内的同名 step **一一对应**，命名前缀 `[dry] `。所有命令在仓库根目录执行。

##### `[dry] Validate version consistency`

```bash
TAG=v1.2.3 bash -c '
  expected="${TAG#v}"
  actual=$(awk -F= "/^versionName/{print \$2}" version.properties | tr -d "[:space:]")
  [ "$expected" = "$actual" ] || { echo "::error::tag $TAG vs versionName $actual mismatch"; exit 1; }
  echo "OK: $TAG ↔ versionName=$actual"
'
```

##### `[dry] Build Release APK`

需在本机配置一份**未入库** `.env.release.local`（`.gitignore` 已排除）：

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

##### `[dry] Compute APK sha256 + size`

```bash
APK=app/build/outputs/apk/release/app-release.apk
sha256sum "$APK" | awk '{print $1}'
wc -c < "$APK"
```

##### `[dry] Generate release notes`

```bash
PREV=v1.2.2          # 上一个已发布 tag
CURR=HEAD            # 实战值为 vX.Y.Z，干跑用 HEAD
bash scripts/release/generate-notes.sh "$PREV" "$CURR" > /tmp/release_notes.md
less /tmp/release_notes.md
```

本地不愿调 LLM：`unset ANTHROPIC_API_KEY`，走 fallback。

##### `[dry] Prepend CHANGELOG.md`

```bash
bash scripts/release/prepend-changelog.sh /tmp/release_notes.md vX.Y.Z --dry-run \
    | diff CHANGELOG.md -
```

`--dry-run` 输出"假如执行后的 CHANGELOG.md 全文"，与现状 diff 出新插入段。**本地不要去掉 `--dry-run` 真写文件**——这步只在 CI 内执行，避免与 CI 重复提交。

##### `[dry] Build version.json`

```bash
bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --apk "$APK" \
    --notes /tmp/release_notes.md \
    > /tmp/version.json
jq . /tmp/version.json   # 语法校验
```

`--apk` 参数让脚本现场算 sha256 + size，避免与 CI 传值漂移。

##### `[dry] Full pre-flight`

```bash
bash scripts/release/preflight.sh v1.2.3
```

脚本串调上述 6 个 step，任一非 0 即停。**push tag 前跑通 preflight 是硬性约束**。

##### 不可本地干跑的 step

| Step | 原因 | 替代验证 |
|---|---|---|
| `gh release create` | 真创建会污染线上 release | 在 fork 上用 `--draft` 跑一次 |
| `git push origin main`（CHANGELOG） | 真 push 污染 main | dry-run diff 已足够 |
| `git push origin gh-pages` | 同上 | 本地切到 gh-pages 看文件结构即可 |

#### 3.8.3 `CHANGELOG.md` 初始模板

```markdown
# Changelog

本项目所有显著变更记录于此。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

<!-- next-release -->

## [Unreleased]

未发布的工作收录在 commit 历史中。
```

## 4. 不在范围

- 应用商店上架（GitHub Release 唯一渠道）。
- 灰度 / 渐进发布、强制升级最低版本。
- 差分 / 增量 patch。
- 后台静默下载、WiFi-only 限制。
- Logan 日志 + 反馈链路（已由 [2026-05-10-release-settings-feedback-crash-design.md](2026-05-10-release-settings-feedback-crash-design.md) 覆盖）。
- 品牌化（app name / icon）与 CI 收敛（debug workflow / harness gate 删除）—— 见 [2026-05-13-release-rebrand-and-ci-design.md](2026-05-13-release-rebrand-and-ci-design.md)。
- `version.properties` 与 `app/build.gradle.kts` 之外的其他模块版本号（各 module 不暴露独立版本）。

## 5. 验证

### 5.1 本地

- `:updater` 模块单元测试 `./gradlew :updater:testDebugUnitTest` 全绿。
- 仪器测试 `./gradlew :updater:connectedDebugAndroidTest` 全绿（MockWebServer 路径）。
- `bash scripts/release/preflight.sh v0.0.0-test` 成功，输出 release_notes.md + 模拟的 version.json，且 jq 校验通过。
- Debug 包冷启动不弹更新 dialog；设置页"检查更新"行可手动触发。
- Release 包冷启动（在 fixture `version.json` 指向新版本时）：
  - 启动后 2s 内弹出"发现新版本" dialog。
  - 点"跳过此版本"，二次冷启动不再弹；设置页主动检查仍弹。
  - 点"下载并安装"，模态进度对话框出现，取消按钮可终止下载并清理 `.part` 文件。
  - 下载完成 → "立即安装" → 系统安装器拉起 → 装好启动新版本。

### 5.2 GitHub Actions

- 临时 tag `v0.0.0-test` 推送：
  - `Validate version consistency` step 在 `version.properties` 不匹配时 fail；匹配后通过。
  - GitHub Release 创建成功，notes 内 LLM 摘要可见（若 `ANTHROPIC_API_KEY` 配置），fallback 时仅 commit 列表。
  - main 上多了一条 `docs(changelog): release v0.0.0-test [skip ci]`。
  - `gh-pages` 上 `release/version.json` 文件可访问；jsdelivr 镜像 24h 内可拉到。
- 临时 tag 删除后，手工 revert CHANGELOG commit 与 `gh-pages` 对应 commit，仓库回到原状。

## 6. 风险与回滚

### 6.1 风险

| 风险 | 缓解 |
|---|---|
| CI 写 main 触发副作用 | `[skip ci]` 标记；其他 workflow 当前无 `paths` 命中 `CHANGELOG.md`，安全 |
| main branch protection 阻挡 CI push | 当前仓库未启用 protection；启用时需在 RELEASE.md 标注，并改用 bot PAT |
| `ANTHROPIC_API_KEY` 失活 / 滥用 | LLM 失败 fallback；定期 rotate；不阻塞 release |
| jsdelivr 12h 缓存延迟 | 接受；GitHub raw 镜像无延迟，作为客户端首选 |
| `REQUEST_INSTALL_PACKAGES` 用户拒授权 | 引导跳系统设置；UI 文案明确 |
| Release APK 试图装到 Debug 包用户 | `applicationId` 不同（`.debug` suffix），系统拒绝覆盖；RELEASE.md 列入故障排查 |
| 版本号 `versionCode` 公式溢出 | MAJOR*10000+MINOR*100+PATCH 在 MAJOR≤21 时不超 int 上限；当前距离上限远 |
| `:updater` 引入 R8 反射问题 | 仅 `kotlinx.serialization` 走默认 serializer，UpdateInfo 字段是基本类型 + List<String>，不需要 keep |

### 6.2 R8 与反射保留

`:updater` 的 `UpdateInfo` 是 `@Serializable data class`，编译期生成 serializer，类名不作为运行时协议——**无需 `@Keep`**。FileProvider 仍走 `androidx.core.content.FileProvider`，AGP 默认规则覆盖。

### 6.3 `.gitignore` 与文档同步改动

- 新增 `.gitignore` 行：`.env.release.local`。
- AGENTS.md / CLAUDE.md：发布流程相关章节追加一句"发布流程详见根目录 RELEASE.md"。
- `docs/dev-harness/INDEX.md`：在"开发守门总入口"补一条"发布流程：见根目录 RELEASE.md 与 docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md"。

### 6.4 回滚

- 删 `:updater` 模块依赖、删 `settings.gradle.kts` 对应行、删 module 目录。
- 还原 `.github/workflows/android-release-apk.yml` 到 pre-spec 版本（保留签名链路）。
- 删 `version.properties`，把 `app/build.gradle.kts` 的 `versionCode = 1 / versionName = "1.0"` 还原。
- 删 `RELEASE.md` / `CHANGELOG.md` / `scripts/release/`。
- `gh-pages` 分支可保留（无害）或删除。

整体回滚成本 ≈ 一次 `git revert` + 删 module 目录。
