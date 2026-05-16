# 分 ABI APK 发布与更新链路改造设计

- **状态**：当前规范（草案）
- **日期**：2026-05-16
- **适用范围**：`app/build.gradle.kts`、`app/proguard-rules.pro`、`:updater` 模块、`:feature:home` 抽屉、`:feature:settings` 检查更新行、`.github/workflows/android-release-apk.yml`、`scripts/release/`、`gh-pages/release/version.json`、`RELEASE.md`
- **关系**：是 [2026-05-13-android-release-pipeline-design.md](2026-05-13-android-release-pipeline-design.md) 的增量改造。前者搭出「tag → 自动发包 → 客户端拉取并安装」闭环（schemaVersion=1，universal APK），本 spec 把它升级为 per-ABI 发布 + R8 mapping 永久归档 + 侧栏检查更新实接通。

## 1. 背景与目标

### 1.1 现状

- `app/build.gradle.kts` 无 `splits.abi`，release 产物是单一 universal APK（`app-release.apk`），同时打入 arm64-v8a / armeabi-v7a / x86 / x86_64 四套 .so，包体偏大且分发无差异化。
- `app/proguard-rules.pro` 的 `-keepattributes SourceFile,LineNumberTable` 与 `-renamesourcefileattribute SourceFile` 均处于注释状态；R8 走 minify + shrink，但 mapping 不归档，**线上崩溃堆栈无法反混淆**。
- `.github/workflows/android-release-apk.yml` 只产出 1 个 APK；`publish-version-manifest` 写出 `version.json` schemaVersion=1，`download/size/sha256` 在顶层、与 ABI 解耦。
- `:feature:home` 抽屉「检查更新」条目挂的是 `HomeDrawerAction.ShowUpdateCheckDialog`，handler 仅打开一个空 `InfoDialog` 显示「当前版本：xxx」；`UpdateBadgeViewModel` 已存在但没有任何 UI 消费——**侧栏检查更新实际未接通**，也没有红点提示。
- `:feature:settings` 的 `CheckUpdateRow` 已接 `UpdateChecker.checkManually()`、有红点逻辑，可作为参考实现。

### 1.2 目标

1. **分 ABI 构建**：release 构建只产出 `arm64-v8a` 和 `x86_64` 两份 APK，不再有 universal。
2. **分 ABI 发布**：CI 一次性把 2 APK + 1 份 mapping zip 作为 release asset 上传；`version.json` 升 v2 表达「ABI → URL/size/sha256」映射。
3. **客户端按 ABI 拉取**：客户端按 `Build.SUPPORTED_ABIS` 顺序匹配 manifest variant，下载对应 APK 安装。
4. **侧栏真接通**：抽屉「检查更新」点击 → `UpdateChecker.checkManually()` + 状态式 Dialog；条目末端红点同步 `Available` 状态。
5. **R8 mapping 永久归档**：每次 release 把 `mapping.txt` zip 后作为 GitHub Release asset 上传，按 tag 永久回溯；同时保留行号属性，反混淆后能定位到行。

### 1.3 不在范围

- AAB（Android App Bundle）/ Play Store 上架链路。
- 强制升级最低版本、灰度 / 渐进发布、差分 / 增量 patch。
- 32-bit only 设备（armeabi-v7a）的兼容；本次明确不发 32-bit APK，落到这类设备走 `UnsupportedAbi` 引导手动下载。
- native debug symbols（`.so.debug` 系列）单独打包；仅打包 R8 Java mapping。`quickjs-kt` 等 NDK 库的 native 崩溃定位若需要再做后续 spec。
- 应用商店一键升级（GitHub Release 仍为唯一渠道）。

## 2. 当前状态（事实基线）

- `version.properties`：`versionCode=10001`、`versionName=1.0.1`，已正式发布。
- `app/build.gradle.kts`：无 splits、无 archivesName、`isMinifyEnabled = true`、`isShrinkResources = true`。
- `app/proguard-rules.pro`：仅模板内容，全部规则被注释。
- `updater/model/UpdateInfo.kt`：`SUPPORTED_SCHEMA_VERSION = 1`；顶层 `download: List<String>`、`size: Long`、`sha256: String`。
- `updater/checker/UpdateChecker.kt`：已实现 checkOnLaunch / checkManually、状态机；ABI 概念缺失。
- `updater/downloader/OkHttpApkDownloader.kt`：消费 `info.download / info.size / info.sha256`。
- `updater/checker/UpdateState.kt`：`UpdateError` 已包含 `Network / SchemaUnsupported / SizeMismatch / Sha256Mismatch / Canceled / InstallBlocked`，**无 `UnsupportedAbi`**。
- `feature/home/HomeDrawerNavigation.kt`：「检查更新」`action = ShowUpdateCheckDialog`、`trailingText = currentVersion`，无红点字段。
- `feature/home/component/HomeDrawerDialogs.kt`：`isUpdateCheckVisible` 时只渲染静态 `InfoDialog`，不调 checker。
- `feature/settings/CheckUpdateRow.kt`：已接 checker，UI 状态映射已具备，可复用为模板。
- `.github/workflows/android-release-apk.yml`：单 APK 路径、`apk-sha256/apk-size` job outputs 是单值；`build-version-json.sh` 接受单 sha256/size。

## 3. 设计

### 3.1 架构与改动面

不新增模块。改动文件清单：

| 文件 | 改动 |
|---|---|
| `app/build.gradle.kts` | 加 `splits.abi { include("arm64-v8a", "x86_64"); isUniversalApk = false }`；`base.archivesName = "MusicFreeAndroid"` |
| `app/proguard-rules.pro` | 启用 `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile` |
| `updater/model/UpdateInfo.kt` | `SUPPORTED_SCHEMA_VERSION = 2`；移除顶层 `download/size/sha256`；新增 `variants: Map<String, ApkVariant>` + 可选 `mapping: MappingRef?` |
| `updater/checker/UpdateState.kt` | `UpdateError` 新增 `UnsupportedAbi`；`Available/Downloading/ReadyToInstall/Failed` 字段从 `UpdateInfo` 替换为 `ResolvedUpdate` |
| `updater/checker/AbiResolver.kt` | 新建：按 `Build.SUPPORTED_ABIS` 顺序匹配 `info.variants` 的第一个 key |
| `updater/checker/UpdateChecker.kt` | 解析完 schemaVersion 后调 `AbiResolver`；无命中 → `Failed(UnsupportedAbi)` |
| `updater/downloader/OkHttpApkDownloader.kt` | 接 `ResolvedUpdate`；cacheDir 文件名加 `-<abi>` 后缀；URL/size/sha256 取 variant 字段 |
| `updater/ui/UpdateDialogs.kt` | 现 Available/Downloading/ReadyToInstall 子组件不变，字段访问路径改 `update.info.*` 与 `update.variant.*` |
| `updater/ui/ManualUpdateDialog.kt` | 新建：状态式 dialog，覆盖 Checking/UpToDate/Available/Downloading/ReadyToInstall/Failed 全部分支 |
| `core/ui/UpdateBadgeDot.kt` | 新建：8.dp 红色圆点公共 composable |
| `feature/home/HomeDrawerNavigation.kt` | 「检查更新」`HomeDrawerItemUiModel` 加 `hasBadge`、`trailingText` 来自 state 派生；action 改 `TriggerManualUpdateCheck` |
| `feature/home/component/HomeDrawerDialogs.kt` | 删空 `InfoDialog` 分支，渲染 `ManualUpdateDialog`（`:updater` 提供） |
| `feature/home/component/HomeDrawerContent.kt` | 注入 `UpdateBadgeViewModel`，把 state 投到「检查更新」item 的 `hasBadge` 与 `trailingText`；trailing slot 多支持 hasBadge 渲染 |
| `feature/settings/CheckUpdateRow.kt` | 现 `RedDot()` 改用公共 `UpdateBadgeDot` |
| `.github/workflows/android-release-apk.yml` | 双 APK rename / sha256 / size、mapping zip 打包、release 上传 3 个 asset、`build-version-json.sh` 用 `--variant` |
| `scripts/release/build-version-json.sh` | 接受任意多 `--variant <abi>=<apk-name>,<sha256>,<size>` + `--mapping-name` + `--mapping-sha256`；输出 v2 schema |
| `scripts/release/preflight.sh` | 干跑双 APK / mapping zip |
| `RELEASE.md` | 「日常发布」「故障排查」「本地干跑」三节同步 |
| `docs/dev-harness/INDEX.md` | 「发布流程」一条加本 spec 链接 |

依赖方向不变：`:app → :feature:* → :data, :player, :plugin, :updater → :core`。

### 3.2 构建侧

#### 3.2.1 ABI splits

`app/build.gradle.kts` 的 `android { ... }` 内：

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "x86_64")
        isUniversalApk = false
    }
}
```

`./gradlew :app:assembleRelease` 产物：

- `app/build/outputs/apk/release/MusicFreeAndroid-arm64-v8a-release.apk`
- `app/build/outputs/apk/release/MusicFreeAndroid-x86_64-release.apk`

不再生成 `app-release.apk`（universal）。`assembleDebug` 不启用 splits（保持原 universal 行为，方便本地 debug 装机）。

#### 3.2.2 archivesName

```kotlin
android {
    base.archivesName = "MusicFreeAndroid"
}
```

让输出名稳定为 `MusicFreeAndroid-<abi>-release.apk`，CI 的 rename step 可直接 glob。

#### 3.2.3 versionCode 策略

两 ABI **共用** `versionCode`（来自 `version.properties`）。GitHub 直发场景下客户端按 manifest 显式 ABI 取 URL，不依赖 Play Store 的 `versionCode + offset` 兜底排序协议。共用 versionCode 也避免「同设备装错 ABI 后再装新版本被系统判为旧版本」。

#### 3.2.4 R8 mapping & 行号属性

`app/proguard-rules.pro`：

```proguard
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

- `SourceFile,LineNumberTable`：保留行号 attribute，ReTrace 能从堆栈帧恢复到具体行。
- `-renamesourcefileattribute SourceFile`：配套混淆，把原始 `.kt` 文件名抹成 `SourceFile`，避免泄漏文件路径与堆栈定位无关的信息。

R8 输出固定路径：`app/build/outputs/mapping/release/mapping.txt`。**splits.abi 不会影响 R8**：R8 在 dex 阶段之前完成 minify，splits 在 packaging 阶段按 ABI 过滤 `.so` + 资源，因此两个 APK 共用同一份 mapping。

### 3.3 manifest schema（v2）

#### 3.3.1 `gh-pages/release/version.json` 模板

```json
{
  "schemaVersion": 2,
  "version": "1.2.3",
  "versionCode": 10203,
  "releasedAt": "2026-05-16T18:00:00Z",
  "releaseNotesUrl": "https://github.com/hanklzl/MusicFreeAndroid/releases/tag/v1.2.3",
  "changeLog": [
    "feat: 启动后台日志压缩",
    "fix: 播放队列偶发丢失"
  ],
  "variants": {
    "arm64-v8a": {
      "download": [
        "https://github.com/hanklzl/MusicFreeAndroid/releases/download/v1.2.3/MusicFreeAndroid-v1.2.3-arm64-v8a.apk",
        "https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@v1.2.3/release/MusicFreeAndroid-v1.2.3-arm64-v8a.apk"
      ],
      "size": 23456789,
      "sha256": "f3a8...c901"
    },
    "x86_64": {
      "download": [
        "https://github.com/hanklzl/MusicFreeAndroid/releases/download/v1.2.3/MusicFreeAndroid-v1.2.3-x86_64.apk"
      ],
      "size": 25123456,
      "sha256": "b1f7...a2dd"
    }
  },
  "mapping": {
    "url": "https://github.com/hanklzl/MusicFreeAndroid/releases/download/v1.2.3/mapping-v1.2.3.zip",
    "sha256": "9c4e...11ab"
  }
}
```

#### 3.3.2 字段语义

| 字段 | 必选 | 说明 |
|---|---|---|
| `schemaVersion` | ✅ | 固定 `2`；客户端 `SUPPORTED_SCHEMA_VERSION` 不匹配走 `SchemaUnsupported` |
| `version` | ✅ | `MAJOR.MINOR.PATCH`，与 `versionName` 一致 |
| `versionCode` | ✅ | 客户端比较首选字段 |
| `releasedAt` | ✅ | ISO 8601 UTC |
| `releaseNotesUrl` | ✅ | 「请前往 GitHub」按钮跳转目标 |
| `changeLog` | ✅ | 字符串数组，最多 8 行 |
| `variants` | ✅ | 至少含一个 key；当前 release 固定 `arm64-v8a` + `x86_64` 两 key |
| `variants.<abi>.download` | ✅ | URL 数组，**首项必须是 GitHub Releases 直链**，可选附加 jsdelivr 镜像 |
| `variants.<abi>.size` | ✅ | 字节数，下载前 Content-Length 预校验 |
| `variants.<abi>.sha256` | ✅ | 流式校验 |
| `mapping` | ❌ | 客户端不消费；保留供后续诊断工具或人工查 release page |
| `mapping.url` | ✅（mapping 存在时） | 必须 GitHub Releases 直链（jsdelivr 不代理大文件） |
| `mapping.sha256` | ✅（mapping 存在时） | 反混淆前可选校验 |

#### 3.3.3 v1 → v2 兼容矩阵

| 客户端版本 | 看到 v2 manifest | 行为 |
|---|---|---|
| v1.0.1（在野，`SUPPORTED_SCHEMA_VERSION=1`） | schemaVersion=2 | `Failed(SchemaUnsupported)`：启动 dialog 不弹通常更新；侧栏 / 设置主动检查显示「请前往 GitHub 下载新版」+ `releaseNotesUrl` |
| v1.x 本次新版（`SUPPORTED_SCHEMA_VERSION=2`） | schemaVersion=2 | 正常解析、ABI 解析、下载、安装 |
| 未来 v3 manifest | 本次 v2 客户端 | 同样 `SchemaUnsupported` 兜底 |

老 v1.0.1 客户端首次升级需手动下载 ABI 对应 APK，**release notes 首段会写明这一点**。

### 3.4 客户端 `:updater` 改造

#### 3.4.1 数据模型

```kotlin
@Serializable
data class ApkVariant(
    val download: List<String>,
    val size: Long,
    val sha256: String,
)

@Serializable
data class MappingRef(val url: String, val sha256: String)

@Serializable
data class UpdateInfo(
    val schemaVersion: Int,
    val version: String,
    val versionCode: Long,
    val releasedAt: String,
    val releaseNotesUrl: String,
    val changeLog: List<String>,
    val variants: Map<String, ApkVariant>,
    val mapping: MappingRef? = null,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 2
    }
}
```

旧顶层 `download / size / sha256` 字段从 model 完全移除。

#### 3.4.2 ABI 解析

```kotlin
data class ResolvedUpdate(val info: UpdateInfo, val abi: String, val variant: ApkVariant)

class AbiResolver(
    private val supportedAbis: () -> List<String> = { Build.SUPPORTED_ABIS.toList() },
) {
    fun resolve(info: UpdateInfo): ResolvedUpdate? =
        supportedAbis().firstOrNull { it in info.variants }
            ?.let { abi -> ResolvedUpdate(info, abi, info.variants.getValue(abi)) }
}
```

`Build.SUPPORTED_ABIS` 系统已按 CPU 优先级排序（64-bit 设备先 `arm64-v8a` 再 `armeabi-v7a, armeabi`；x86_64 设备先 `x86_64` 再 `x86`），命中即用。注入 lambda 让单测可控。

通过 Hilt 在 `UpdaterModule` 内 `@Provides @Singleton` 注入。

#### 3.4.3 状态机

```kotlin
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Instant) : UpdateState
    data class Available(val update: ResolvedUpdate, val skipped: Boolean) : UpdateState
    data class Downloading(val update: ResolvedUpdate, val progress: Float, val bytes: Long, val total: Long) : UpdateState
    data class ReadyToInstall(val update: ResolvedUpdate, val apkFile: File) : UpdateState
    data class Failed(val update: ResolvedUpdate?, val cause: UpdateError) : UpdateState
}

enum class UpdateError {
    Network,
    SchemaUnsupported,
    UnsupportedAbi,
    SizeMismatch,
    Sha256Mismatch,
    Canceled,
    InstallBlocked,
}

val UpdateState.hasUnreadAvailableUpdate: Boolean
    get() = this is UpdateState.Available && !skipped
```

#### 3.4.4 UpdateChecker 流程

```kotlin
suspend fun check(ignoreSkip: Boolean) {
    _state.value = Checking
    val info = client.fetchLatest()
        ?: return fail(cause = Network, resolved = null)
    if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION || info.variants.isEmpty()) {
        return fail(cause = SchemaUnsupported, resolved = null)
    }
    if (versionCompare.isUpToDate(local, info)) {
        prefs.clearSkipVersion()
        _state.value = UpToDate(now())
        return
    }
    val resolved = abiResolver.resolve(info)
        ?: return fail(cause = UnsupportedAbi, resolved = null)
    val skipped = !ignoreSkip && prefs.skipVersion == info.version
    _state.value = Available(resolved, skipped)
}
```

- `checkOnLaunch()` → `check(ignoreSkip = false)`，启动 dialog 仅在 `Available(skipped=false)` 触发。
- `checkManually()` → `check(ignoreSkip = true)`，侧栏 / 设置主动检查总是显示 dialog。

#### 3.4.5 下载器

`OkHttpApkDownloader` 接 `ResolvedUpdate`：

- cacheDir 文件名：`<cacheDir>/updates/musicfree-<versionCode>-<abi>.apk{.part}`，加 `<abi>` 后缀防止极端场景（用户换设备 / 改 ROM）下复用旧文件。
- URL 列表：`resolved.variant.download`，按序尝试。
- 大小校验：HEAD / Content-Length 与 `resolved.variant.size` 严格相等，否则 `Failed(SizeMismatch)`。
- sha256：流式累计校验 `resolved.variant.sha256`。
- 取消、`Sha256Mismatch`、`SizeMismatch` 等行为与 v1 一致。

#### 3.4.6 错误 UI 文案

| Error | 文案 |
|---|---|
| `Network` | 「网络错误，请检查网络后重试」 |
| `SchemaUnsupported` | 「请前往 GitHub 下载最新版本」+ 按钮跳 `info?.releaseNotesUrl ?: "https://github.com/hanklzl/MusicFreeAndroid/releases/latest"`（fallback URL 提到 `UpdaterConfig` 常量） |
| `UnsupportedAbi` | 「您的设备架构（${Build.SUPPORTED_ABIS.firstOrNull()}）未受支持，请前往 GitHub 手动确认设备适配」+ 按钮跳 `releaseNotesUrl` |
| `SizeMismatch` | 「安装包大小异常，请稍后重试」 |
| `Sha256Mismatch` | 「安装包校验失败，请稍后重试」 |
| `Canceled` | 不显示（用户主动） |
| `InstallBlocked` | 「请前往设置允许安装未知应用」+ 按钮跳 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` |

启动 dialog 只在 `Available(skipped=false)` 触发；`Failed(SchemaUnsupported)` / `Failed(UnsupportedAbi)` **不自动弹**（避免每次冷启动骚扰），仅在用户主动点侧栏 / 设置时显示。

### 3.5 CI 流水线改造

#### 3.5.1 `build-release-apk` job

##### Build step

不变：`./gradlew :app:assembleRelease --no-daemon`，但产物从 1 个变 2 个。

##### Name & rename APKs（替换原 `Name APK` step）

```bash
for abi in arm64-v8a x86_64; do
  src="app/build/outputs/apk/release/MusicFreeAndroid-${abi}-release.apk"
  case "${GITHUB_REF_TYPE}/${GITHUB_EVENT_NAME}" in
    tag/push)      dst="MusicFreeAndroid-${GITHUB_REF_NAME}-${abi}.apk" ;;
    */schedule)    dst="MusicFreeAndroid-nightly-$(date -u +%Y%m%d)-$(git rev-parse --short HEAD)-${abi}.apk" ;;
    *)             dst="MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}-${abi}.apk" ;;
  esac
  cp "$src" "$RUNNER_TEMP/$dst"
  key="${abi//-/_}"
  echo "apk_${key}_name=$dst" >> "$GITHUB_OUTPUT"
done
```

job outputs 暴露 `apk_arm64_v8a_name` 与 `apk_x86_64_name`。

##### Compute per-ABI sha256 + size

显式枚举两 ABI，避免 bash 间接变量展开易错：

```bash
arm64_apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_arm64_v8a_name }}"
x64_apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_x86_64_name }}"
{
  echo "sha256_arm64_v8a=$(sha256sum "$arm64_apk" | awk '{print $1}')"
  echo "size_arm64_v8a=$(wc -c < "$arm64_apk")"
  echo "sha256_x86_64=$(sha256sum "$x64_apk" | awk '{print $1}')"
  echo "size_x86_64=$(wc -c < "$x64_apk")"
} >> "$GITHUB_OUTPUT"
```

未来若加第三个 ABI（如 armeabi-v7a），此 step 需手动追加一组——刻意保留两组直写，避免引入间接展开后被外部输入污染的风险。

##### Pack mapping（仅 tag 路径）

```bash
mkdir -p "$RUNNER_TEMP/mapping"
cp app/build/outputs/mapping/release/mapping.txt "$RUNNER_TEMP/mapping/"
(cd "$RUNNER_TEMP" && zip -9q "mapping-${GITHUB_REF_NAME}.zip" mapping/mapping.txt)
sha=$(sha256sum "$RUNNER_TEMP/mapping-${GITHUB_REF_NAME}.zip" | awk '{print $1}')
echo "mapping_name=mapping-${GITHUB_REF_NAME}.zip" >> "$GITHUB_OUTPUT"
echo "mapping_sha256=$sha" >> "$GITHUB_OUTPUT"
```

`schedule` / `workflow_dispatch` 路径不打包 mapping。

##### Upload artifacts

三个独立 artifact：

- `MusicFreeAndroid-release-apk-arm64-v8a`：对应 arm64 APK，retention 14 天。
- `MusicFreeAndroid-release-apk-x86_64`：对应 x86_64 APK，retention 14 天。
- `MusicFreeAndroid-release-mapping`：mapping zip，retention 90 天（兜底，主存留靠 release asset）。

job outputs：

```yaml
outputs:
  apk-arm64-name: ${{ steps.name-apk.outputs.apk_arm64_v8a_name }}
  apk-arm64-sha256: ${{ steps.apk-meta.outputs.sha256_arm64_v8a }}
  apk-arm64-size: ${{ steps.apk-meta.outputs.size_arm64_v8a }}
  apk-x86_64-name: ${{ steps.name-apk.outputs.apk_x86_64_name }}
  apk-x86_64-sha256: ${{ steps.apk-meta.outputs.sha256_x86_64 }}
  apk-x86_64-size: ${{ steps.apk-meta.outputs.size_x86_64 }}
  mapping-name: ${{ steps.pack-mapping.outputs.mapping_name }}
  mapping-sha256: ${{ steps.pack-mapping.outputs.mapping_sha256 }}
```

#### 3.5.2 `publish-github-release` job

- 三次 `actions/download-artifact` 下载 2 APK + 1 mapping zip 到 `release-apk-arm64/` / `release-apk-x86_64/` / `release-mapping/`。
- Release notes 生成保持原逻辑，末尾追加 ABI 矩阵：

  ```
  ### 构建产物
  - arm64-v8a：{size_human} | sha256 `{sha-short}`
  - x86_64：{size_human} | sha256 `{sha-short}`
  - mapping.zip：{size_human} | sha256 `{sha-short}`
  ```

- 创建 / 更新 release：

  ```bash
  assets=(
    "release-apk-arm64/${{ needs.build-release-apk.outputs.apk-arm64-name }}"
    "release-apk-x86_64/${{ needs.build-release-apk.outputs.apk-x86_64-name }}"
    "release-mapping/${{ needs.build-release-apk.outputs.mapping-name }}"
  )
  if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
    gh release upload "$GITHUB_REF_NAME" "${assets[@]}" --clobber
    gh release edit "$GITHUB_REF_NAME" --notes-file release_notes.md
  else
    gh release create "$GITHUB_REF_NAME" "${assets[@]}" \
      --title "$GITHUB_REF_NAME" \
      --notes-file release_notes.md
  fi
  ```

#### 3.5.3 `publish-version-manifest` job

`build-version-json.sh` 接受任意多 `--variant` flag：

```bash
bash source/scripts/release/build-version-json.sh \
    --version "${GITHUB_REF_NAME#v}" \
    --version-code "$vcode" \
    --tag "$GITHUB_REF_NAME" \
    --variant "arm64-v8a=${{ needs.build-release-apk.outputs.apk-arm64-name }},${{ needs.build-release-apk.outputs.apk-arm64-sha256 }},${{ needs.build-release-apk.outputs.apk-arm64-size }}" \
    --variant "x86_64=${{ needs.build-release-apk.outputs.apk-x86_64-name }},${{ needs.build-release-apk.outputs.apk-x86_64-sha256 }},${{ needs.build-release-apk.outputs.apk-x86_64-size }}" \
    --mapping-name "${{ needs.build-release-apk.outputs.mapping-name }}" \
    --mapping-sha256 "${{ needs.build-release-apk.outputs.mapping-sha256 }}" \
    --notes source/release_notes.md \
    > gh-pages/release/version.json
jq . gh-pages/release/version.json
```

脚本内部：

- 按 v2 schema 拼装；每个 `--variant abi=name,sha256,size` 解析后填进 `.variants[abi]`；`download` 数组：第一个固定 GitHub Releases 直链拼 `https://github.com/${GITHUB_REPOSITORY}/releases/download/${tag}/${name}`，第二个可选 jsdelivr `https://cdn.jsdelivr.net/gh/${GITHUB_REPOSITORY}@${tag}/release/${name}`（脚本以 env flag 控制是否注入 jsdelivr）。
- `--mapping-name` + `--mapping-sha256` 转 `.mapping = { url, sha256 }`。
- 输出经 `jq` 语法校验。

#### 3.5.4 `preflight.sh` 本地干跑

```bash
# [dry] Build Release APK
./gradlew clean :app:assembleRelease --no-daemon
ls -lh app/build/outputs/apk/release/*.apk   # 必须看到至少 2 个

# [dry] Compute APK sha256 + size
for abi in arm64-v8a x86_64; do
  apk="app/build/outputs/apk/release/MusicFreeAndroid-${abi}-release.apk"
  sha256sum "$apk" | awk '{print $1}'
  wc -c < "$apk"
done

# [dry] Pack mapping
mapping="app/build/outputs/mapping/release/mapping.txt"
mkdir -p /tmp/mapping && cp "$mapping" /tmp/mapping/
(cd /tmp && zip -9q "mapping-v0.0.0-dry.zip" mapping/mapping.txt)
sha256sum /tmp/mapping-v0.0.0-dry.zip

# [dry] Build version.json
bash scripts/release/build-version-json.sh \
    --version 1.2.3 --version-code 10203 --tag v1.2.3 \
    --variant "arm64-v8a=MusicFreeAndroid-v1.2.3-arm64-v8a.apk,${sha_arm},${size_arm}" \
    --variant "x86_64=MusicFreeAndroid-v1.2.3-x86_64.apk,${sha_x},${size_x}" \
    --mapping-name "mapping-v1.2.3.zip" --mapping-sha256 "${sha_map}" \
    --notes /tmp/release_notes.md \
    | jq .
```

`scripts/release/preflight.sh` 把上述 4 step 串起；本地若没配 release 签名 env 跳过 Build Release APK 直接断言「skipped」（避免阻塞普通开发者干跑后续步骤）。

### 3.6 抽屉 UX 与红点

#### 3.6.1 抽屉条目改造

`HomeDrawerNavigation.kt`：

```kotlin
HomeDrawerItemUiModel(
    title = "检查更新",
    iconRes = HomeIcons.DrawerCheckUpdate,
    anchorTag = FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
    trailingText = trailingFor(updateState, currentVersion),
    hasBadge = updateState.hasUnreadAvailableUpdate,
    action = HomeDrawerAction.TriggerManualUpdateCheck,
)
```

`trailingFor`（与 settings 共享的 utility）：

| state | trailing |
|---|---|
| `Idle` / `Failed(Network)` | `currentVersion` |
| `Checking` | `"检查中…"` |
| `UpToDate` | `currentVersion` |
| `Available` | `"v${update.info.version} 可用"`（红点已显示时仍保留文案，可视化更明确） |
| 其他 `Failed` | `"检查失败"` |

`HomeDrawerItemUiModel` 增字段 `hasBadge: Boolean = false`；trailing slot 渲染：`hasBadge` 时在文本左侧加 8.dp 红点（设置页同款）。

#### 3.6.2 action handler

```kotlin
HomeDrawerAction.TriggerManualUpdateCheck -> {
    coordinator.launch { updateChecker.checkManually() }
    isManualUpdateDialogVisible = true
}
```

`updateChecker` 与 settings 复用同一 `@Singleton`，state 共享。

#### 3.6.3 ManualUpdateDialog

新 composable `:updater/ui/ManualUpdateDialog.kt`，订阅 `UpdateChecker.state`，按 state 渲染（见 §3.4.6 文案）；下载流程内嵌（Available → Downloading → ReadyToInstall 子组件复用已有 `UpdateDialogs.kt` 实现）。dialog 关闭只置 `isManualUpdateDialogVisible = false`，**不重置 state**——红点和后续启动 dialog 继续按状态机走。

#### 3.6.4 红点接入

抽屉 root composable 注入 `UpdateBadgeViewModel`（已存在），订阅 `viewModel.checker.state`，把 `state.hasUnreadAvailableUpdate` 传到 `HomeDrawerItemUiModel.hasBadge`。

公共红点 `core/ui/UpdateBadgeDot.kt`：

```kotlin
@Composable
fun UpdateBadgeDot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(color = Color(0xFFE53935))
    }
}
```

settings 的 `CheckUpdateRow` 现 `RedDot()` 改用此 composable，避免两份相同实现。

#### 3.6.5 与启动 dialog 互斥

- 启动 dialog 仍只在 `Available(skipped=false)` 一次冷启动单次触发，由独立 `isLaunchDialogVisible` flag 控制。
- 侧栏 / 设置主动检查打开 `isManualUpdateDialogVisible`，与启动 dialog 互不冲突；同一 state 渲染。
- 下载流程归 `UpdateChecker` 单一 state，state machine 天然单写——同一时间只能有一个下载。两个 dialog 都从 state 读，不会出现并发下载。

### 3.7 mapping 反混淆使用流程

```bash
gh release download v1.2.3 --pattern 'mapping-*.zip' --dir /tmp/
unzip /tmp/mapping-v1.2.3.zip -d /tmp/mapping-v1.2.3/
# IDEA → Tools → Re-trace → 选 /tmp/mapping-v1.2.3/mapping.txt + 贴堆栈
# 或命令行：
~/Library/Android/sdk/tools/proguard/bin/retrace.sh /tmp/mapping-v1.2.3/mapping.txt crash.txt
```

`RELEASE.md` 「故障排查」一节会附完整命令链 + IDE 步骤。Logan 日志里的崩溃帧未来若接进 Sentry / Crashlytics，可用同一 mapping 文件批量反混淆——本 spec 不引入云端 ingester，仅保证 mapping 可永久回溯。

### 3.8 数据持久化

不变。`UpdatePreferences` 仍只持久 `skip_version / last_checked_at / last_seen_version`，不持久 ABI（每次启动重新读 `Build.SUPPORTED_ABIS`）。

### 3.9 错误处理与边界情况

| 场景 | 处理 |
|---|---|
| `info.variants` 为空或缺字段 | `Failed(SchemaUnsupported)`；启动 dialog 不弹 |
| `schemaVersion > 2` | `Failed(SchemaUnsupported)` |
| 所有 `variants` key 都不在 `Build.SUPPORTED_ABIS` | `Failed(UnsupportedAbi)`；启动 dialog 不弹；侧栏 / 设置主动检查显示指引 |
| `variants[abi].size = 0` 或 `sha256` 空字符串 | 视为字段缺失，`Failed(SchemaUnsupported)` |
| `variants[abi].download[]` 为空 | `Failed(SchemaUnsupported)` |
| Content-Length ≠ `variant.size` | `Failed(SizeMismatch)`；清 `.part` |
| sha256 不匹配 | `Failed(Sha256Mismatch)`；清文件；`state = Available(skipped=false)` |
| 用户取消下载 | 清 `.part`；`state = Available(skipped=false)` |
| 老 v1.0.1 客户端读 v2 manifest | `Failed(SchemaUnsupported)`；侧栏 / 设置显示「请前往 GitHub」 |
| 设备 ABI 切换（例如 ROM 升级） | cacheDir 文件名带 `<abi>` 后缀，旧 ABI 残留 `.part` 不会被复用 |
| `Build.SUPPORTED_ABIS` 在某些厂商魔改 ROM 上返回意外顺序 | 顺序优先策略仍兜底；测试覆盖 |

### 3.10 测试策略

#### 3.10.1 单元测试（`updater/src/test/`）

- `UpdateInfoV2SerializationTest`：
  - 合法 v2（双 variant + mapping）
  - 合法 v2（单 variant、无 mapping）
  - 缺 `variants` 字段
  - `schemaVersion = 3`
  - `variants` 为空
  - `variants[arm64-v8a].size = 0`
- `AbiResolverTest`：
  - 设备 `[arm64-v8a, armeabi-v7a]` + manifest 双 ABI → 选 arm64
  - 设备 `[x86_64, x86]` + manifest 双 ABI → 选 x86_64
  - 设备 `[armeabi-v7a]` + manifest 双 ABI → null
  - manifest 只含 arm64 + 设备 x86_64 → null
  - 设备空 list → null
- `UpdateCheckerTest` 新增 case：
  - v2 manifest 解析成功 → `Available(ResolvedUpdate)`，variant 字段全对
  - `AbiResolver` 返回 null → `Failed(UnsupportedAbi)`
  - 老 fake `client` 返回 `schemaVersion=1` 结构（用 v1 fixture） → `Failed(SchemaUnsupported)`
- `VersionCompareTest` 不变。

#### 3.10.2 仪器测试（`updater/src/androidTest/`）

- `ApkDownloaderV2InstrumentedTest`：MockWebServer 提供假 APK；以 `ResolvedUpdate(variant)` 驱动；验证：
  - cacheDir 路径包含 `<abi>` 后缀
  - 进度回调
  - cancel 清 `.part`
  - Content-Length 不等于 `variant.size` → `Failed(SizeMismatch)`
  - sha256 不匹配 → `Failed(Sha256Mismatch)`
- `ApkInstallerInstrumentedTest` 不动（authority 不变）。

#### 3.10.3 CI 端到端验证

打临时 tag `v0.0.0-test`：

1. `Validate version consistency` 失败 → 修正 `version.properties` → 通过。
2. workflow 完成后：
   - GitHub Release 上传 3 个 asset：2 APK + mapping zip。
   - Release notes 末尾有「构建产物」矩阵。
   - `gh-pages/release/version.json` schemaVersion=2、`variants` 含两 key、`mapping` 字段齐。
3. arm64 测试机装 arm64 APK 冷启动；x86_64 模拟器装 x86_64 APK 冷启动；分别验证启动 dialog → 下载 → 安装链路。
4. **回归**：本地 client 临时把 `SUPPORTED_SCHEMA_VERSION=1` build，跑一遍 → 验证 `Failed(SchemaUnsupported)` 引导路径成立。
5. 验毕：删 tag + 删 release + revert CHANGELOG commit + 删 `gh-pages` 对应 commit + 删 release/version.json 历史（force-push 上一 commit）。

#### 3.10.4 不在测试范围

- mapping zip 的反混淆正确性（人工 retrace 抽样一次足够）。
- 各 ROM 安装路径（在 release 发布前手测 Pixel + 国产至少 1 台）。
- jsdelivr 缓存延迟与 purge 行为。

## 4. 文档产出

| 路径 | 类型 | 内容 |
|---|---|---|
| `docs/superpowers/specs/2026-05-16-per-abi-release-and-update-design.md` | 设计 spec | 即本份 |
| `RELEASE.md` | runbook | 「日常发布」加双 APK + mapping 输出；「故障排查」加反混淆命令链 + v1 客户端兼容说明；「本地干跑」更新双 APK 步骤 |
| `docs/dev-harness/INDEX.md` | 入口索引 | 「发布流程」一条加本 spec 链接 |

不新增 dev-harness rules.md / incidents.md。实施期间若出现新踩坑（splits.abi 与 R8 的某项交互、ProGuard mapping 大小异常等），按既有 [harness-curator-skill] 流程补条目。

## 5. 验证

### 5.1 本地

- `:updater:testDebugUnitTest` 全绿（含新增 V2 / AbiResolver 测试）。
- `:updater:connectedDebugAndroidTest` 全绿（MockWebServer + cacheDir 命名）。
- `./gradlew clean :app:assembleRelease` 产出双 APK + mapping.txt 行号属性正常（用 `proguard.bin.retrace.sh` 抽样反混淆一帧验证）。
- `bash scripts/release/preflight.sh v0.0.0-test` 成功输出 release_notes.md、双 APK 哈希、mapping zip 哈希、模拟 version.json（jq 通过）。
- arm64 release 包冷启动：触发 dialog → 下载 → 安装链路打通。
- x86_64 模拟器装 x86_64 APK：同上。
- 老 v1.0.1 客户端（本地降 schemaVersion build 一份）读到 v2 manifest → `SchemaUnsupported` 引导成立。

### 5.2 GitHub Actions

- 临时 tag `v0.0.0-test` 推送完成全套 workflow：
  - Release 含 3 个 asset。
  - `gh-pages/release/version.json` v2 schema 全字段齐。
  - jsdelivr 镜像在 24h 内可拉到。
- 临时 tag 与相关 commit / branch 历史在验证完成后清理。

## 6. 风险与回滚

### 6.1 风险

| 风险 | 缓解 |
|---|---|
| `splits.abi` 与 R8 行号 attribute 同时启用未在本仓库验证过 | CI 端到端先用 `v0.0.0-test` tag 验过再上线；本地 dry-run 必须看到 mapping.txt 带行号 |
| 客户端 `Build.SUPPORTED_ABIS` 顺序异常（厂商魔改） | 测试覆盖 + 兜底 `UnsupportedAbi` 引导 + release notes 写明老客户端首次升级路径 |
| 老 v1.0.1 客户端在 v2 上线后大量看到「SchemaUnsupported」 | release notes 顶部说明「老版本客户端首次升级请前往 GitHub 手动下载对应架构 APK」 |
| 双 APK 上传偶发 `gh release upload` 失败 | 单 step 内 3 个 asset 一起传，原子失败重跑 workflow |
| jsdelivr 不缓存 mapping zip / 大文件 | mapping URL 不走 jsdelivr，只走 GitHub Releases |
| R8 启用行号属性后 mapping 体积膨胀 | 接受，~+20%；mapping zip 仍在合理范围 |
| splits.abi 影响 `connectedAndroidTest` 仪器测试 | 仪器测试走 `assembleDebugAndroidTest`，未启用 splits.abi，不受影响 |
| `bash` step 间接变量展开易错 | rename / hash step 内显式枚举两次 ABI，避免 `${!key}` 间接展开 |
| `cacheDir/updates/musicfree-<versionCode>-<abi>.apk` 跨升级残留 | `UpdateChecker.checkOnLaunch()` 启动后清理与当前 versionCode 不一致的旧 part / 旧 APK；与既有清理逻辑沿用同一 helper |
| 用户在 Settings 关掉「未知来源安装」 | `canRequestPackageInstalls()` 检测；`Failed(InstallBlocked)` 引导跳系统设置 |
| `archivesName` 修改影响其他 module artifact 命名 | 仅作用 `:app`；其他 module 不引用 `app-*.apk` 名 |

### 6.2 R8 与反射保留

- `ApkVariant` / `MappingRef` / `UpdateInfo` 都是 `@Serializable data class`，编译期生成 serializer，类名不作为运行时协议——**无需 `@Keep`**。
- `AbiResolver` 普通类，无反射使用。
- `Build.SUPPORTED_ABIS` 是 Android 框架 API，R8 不影响。
- FileProvider authority 不变，无 R8 影响。

### 6.3 回滚

按由后向前顺序：

1. **客户端先回滚**（保住老 v1 fallback）：
   - `:updater` `SUPPORTED_SCHEMA_VERSION` 回 1。
   - 删 `ApkVariant / MappingRef / ResolvedUpdate / AbiResolver`，`UpdateInfo` 回顶层 `download/size/sha256`。
   - `UpdateState` 字段回 `UpdateInfo`，`UpdateError` 移除 `UnsupportedAbi`。
   - 抽屉「检查更新」改回 `ShowUpdateCheckDialog` + 空 `InfoDialog`。
2. **manifest 回滚**：
   - 重新发布一份 v1 schema manifest 覆盖 `gh-pages/release/version.json`，让所有客户端继续可用。
3. **CI 回滚**：
   - `.github/workflows/android-release-apk.yml` 还原单 APK / 单 sha256 / 单 size / 不上 mapping。
   - `scripts/release/build-version-json.sh` 还原 v1 参数。
   - `scripts/release/preflight.sh` 还原。
4. **构建回滚**：
   - 删 `splits.abi` 块、`base.archivesName`。
   - 注释回 `-keepattributes SourceFile,LineNumberTable` 与 `-renamesourcefileattribute SourceFile`。

整体回滚成本 ≈ 一次 `git revert`（含 `:updater` + workflow + 文档）+ 发一个 v1 schema 兜底 manifest，约 1 个工作日内完成。
