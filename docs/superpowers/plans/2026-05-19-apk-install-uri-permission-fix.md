# APK 安装 URI 授权修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复更新 APK 下载完成后调用系统安装器时，MIUI 因 FileProvider URI 未获读授权而报 `Permission Denial` 的问题。

**Architecture:** 保持 updater FileProvider 私有，强化安装 Intent 的 URI 授权表达：`data/type` + `ClipData` + 对解析到的安装器包显式 `grantUriPermission`。失败路径记录结构化日志并回落到现有 `InstallBlocked` UI 状态。

**Tech Stack:** Kotlin、Android Intent/FileProvider、Robolectric、MockK、Gradle `:updater:testDebugUnitTest`。

---

### Task 1: 安装 Intent 授权修复

**Files:**
- Modify: `updater/src/main/java/com/hank/musicfree/updater/installer/InstallIntents.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/installer/ApkInstaller.kt`

- [ ] **Step 1: 给安装 Intent 添加 ClipData**

在 `InstallIntents.installApk(uri)` 中保留现有 `ACTION_VIEW` 与 MIME，并添加：

```kotlin
clipData = ClipData.newRawUri("MusicFree update APK", uri)
addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
```

- [ ] **Step 2: 增加显式授权 helper**

在 `InstallIntents` 中新增内部函数：

```kotlin
@Suppress("DEPRECATION")
internal fun grantReadPermissionToInstallers(context: Context, intent: Intent, uri: Uri): Int {
    val targetPackages = linkedSetOf<String>()
    context.packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .forEach { resolveInfo ->
            resolveInfo.activityInfo?.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let(targetPackages::add)
            resolveInfo.resolvePackageName
                ?.takeIf { it.isNotBlank() }
                ?.let(targetPackages::add)
        }
    targetPackages.forEach { packageName ->
        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return targetPackages.size
}
```

- [ ] **Step 3: ApkInstaller 使用 helper 并记录日志**

在 `ApkInstaller.install(apkFile)` 中构造 intent 后调用 helper：

```kotlin
val intent = InstallIntents.installApk(uri)
val grantTargetCount = InstallIntents.grantReadPermissionToInstallers(context, intent, uri)
MfLog.detail(
    category = LogCategory.UPDATE,
    event = "apk_install_intent_prepared",
    fields = mapOf(
        "fileName" to apkFile.name,
        "authority" to authority,
        "grantTargetCount" to grantTargetCount,
        "hasClipData" to (intent.clipData != null),
    ),
)
context.startActivity(intent)
```

- [ ] **Step 4: 捕获安装启动失败**

对 FileProvider/授权/启动安装器包裹 `try/catch`，对 `ActivityNotFoundException`、`SecurityException`、`RuntimeException` 记录 `MfLog.error(LogCategory.UPDATE, "apk_install_start_failed", ...)`，字段包含 `fileName`、`authority`、`reason`，并返回 `InstallResult.Blocked(UpdateError.InstallBlocked)`。

### Task 2: 回归测试

**Files:**
- Modify: `updater/src/test/java/com/hank/musicfree/updater/installer/InstallIntentsTest.kt`

- [ ] **Step 1: 扩展安装 Intent 构造测试**

在现有 `build install intent has correct action data flags` 中新增断言：

```kotlin
val clipData = requireNotNull(intent.clipData)
assertEquals(1, clipData.itemCount)
assertEquals(uri, clipData.getItemAt(0).uri)
```

- [ ] **Step 2: 新增显式授权测试**

使用 MockK 构造 `Context` 与 `PackageManager`，返回三个 `ResolveInfo`（两个不同包名、一个重复包名），断言 helper 返回 2，并且对两个包各调用一次：

```kotlin
verify(exactly = 1) {
    context.grantUriPermission("com.miui.packageinstaller", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
verify(exactly = 1) {
    context.grantUriPermission("com.android.packageinstaller", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

- [ ] **Step 3: 运行 updater 单测**

Run:

```bash
./gradlew :updater:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

### Task 3: 收尾验证与合并

**Files:**
- No additional source files.

- [ ] **Step 1: 运行 dev harness**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: `dev-harness check passed` 或等价成功退出。

- [ ] **Step 2: 运行 Debug 构建**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 代码审查**

检查 diff 满足：

- Provider 仍为 `exported=false`。
- 安装 URI 同时出现在 `data` 与 `clipData`。
- 显式授权不硬编码厂商包名。
- catch 路径均记录结构化日志。

- [ ] **Step 4: squash 合并回 main**

在主工作区执行：

```bash
git merge --squash codex/fix-apk-install-permission
git commit -m "fix(updater): 修复 APK 安装 URI 授权"
```

Expected: 单个 conventional commit 落在 `main`，worktree 可清理。
