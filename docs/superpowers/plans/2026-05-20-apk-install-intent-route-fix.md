# APK Install Intent Route Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复更新 APK 下载完成后安装报错的问题，让安装请求稳定进入系统 PackageInstaller，而不是泛化 `ACTION_VIEW` 文件打开链路。

**Architecture:** 保留私有 FileProvider 与现有下载/校验状态机，只把安装 intent 主 action 改为 `ACTION_INSTALL_PACKAGE`，并扩展 URI 授权目标发现；授权对象只保留系统 / 更新系统应用包，避免把 APK content URI 暴露给第三方 MIME handler。测试用 Robolectric + MockK 覆盖 intent action、ClipData 和授权去重。

**Tech Stack:** Kotlin、Android Intent/FileProvider、Robolectric、MockK、Gradle `:updater:testDebugUnitTest`。

---

### Task 1: 安装 Intent 路由与授权 helper

**Files:**
- Modify: `updater/src/main/java/com/hank/musicfree/updater/installer/InstallIntents.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/installer/ApkInstaller.kt`

- [ ] **Step 1: 把安装 action 改为 ACTION_INSTALL_PACKAGE**

在 `InstallIntents.installApk(uri)` 中把：

```kotlin
Intent(Intent.ACTION_VIEW).apply {
```

改为：

```kotlin
Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
```

保留：

```kotlin
setDataAndType(uri, APK_MIME_TYPE)
clipData = ClipData.newRawUri("MusicFree update APK", uri)
addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
```

- [ ] **Step 2: 扩展授权目标收集**

在 `InstallIntents.grantReadPermissionToInstallers(...)` 中将收集逻辑改为合并三路结果：

```kotlin
val targetPackages = linkedSetOf<String>()

fun addTarget(resolveInfo: ResolveInfo?) {
    val activityInfo = resolveInfo?.activityInfo ?: return
    val appFlags = activityInfo.applicationInfo?.flags ?: 0
    val isSystemInstaller =
        appFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    if (!isSystemInstaller) return

    activityInfo.packageName
        ?.takeIf { it.isNotBlank() }
        ?.let(targetPackages::add)
    resolveInfo.resolvePackageName
        ?.takeIf { it.isNotBlank() }
        ?.let(targetPackages::add)
}

addTarget(context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY))
context.packageManager
    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    .forEach(::addTarget)
context.packageManager
    .queryIntentActivities(intent, 0)
    .forEach(::addTarget)

targetPackages.forEach { packageName ->
    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
return targetPackages.size
```

- [ ] **Step 3: 补充安装准备日志字段**

在 `ApkInstaller.install(apkFile)` 的 `apk_install_intent_prepared` 日志字段中新增：

```kotlin
"intentAction" to intent.action,
```

保留 `authority`、`grantTargetCount`、`hasClipData`。

### Task 2: Intent 与授权回归测试

**Files:**
- Modify: `updater/src/test/java/com/hank/musicfree/updater/installer/InstallIntentsTest.kt`

- [ ] **Step 1: 更新安装 Intent action 断言**

把 `build install intent has correct action data flags` 中的 action 断言改为：

```kotlin
assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action)
```

继续断言 data、type、read grant、new task 与 ClipData。

- [ ] **Step 2: 扩展授权 helper 测试**

将 `grant read permission grants every resolved installer package once` 改为覆盖 resolve/default/wide 三路去重，并加入一个非系统第三方 handler 验证不会被授权：

```kotlin
every {
    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
} returns resolveInfo("com.google.android.packageinstaller")
every {
    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
} returns listOf(
    resolveInfo("com.miui.packageinstaller"),
    resolveInfo("com.google.android.packageinstaller"),
)
every {
    packageManager.queryIntentActivities(intent, 0)
} returns listOf(
    resolveInfo("com.android.packageinstaller"),
    resolveInfo("com.miui.packageinstaller"),
    resolveInfo("com.example.thirdparty.viewer", flags = 0),
)
```

期望返回 `3`，且三个包各授权一次。

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

Expected: 成功退出。

- [ ] **Step 2: 运行 Debug 构建**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 做代码审查**

检查 diff 满足：

- Provider 仍为 `exported=false`。
- APK 安装 Intent 使用 `ACTION_INSTALL_PACKAGE`。
- 安装 URI 同时出现在 `data` 与 `clipData`。
- 授权 helper 不硬编码单一厂商包名，仅授权系统 / 更新系统应用包，且对结果去重。
- catch 路径仍记录结构化日志。

- [ ] **Step 4: squash 合并回 main**

在主工作区执行：

```bash
git merge --squash fix/apk-update-install
git commit -m "fix(updater): 修复 APK 更新安装入口"
```

Expected: 单个 conventional commit 落在 `main`，worktree 可清理。
