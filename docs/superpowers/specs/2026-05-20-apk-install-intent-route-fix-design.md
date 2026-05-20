# APK 安装 Intent 路由修复设计

> 文档状态：当前规范（本轮修复）
> 适用范围：更新功能下载完成后的 APK 安装 Intent、FileProvider URI 授权与安装失败诊断。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 日期：2026-05-20

## 背景

上一轮 `fix(updater): 修复 APK 安装 URI 授权` 已在 `ACTION_VIEW` 安装 Intent 上补充 `ClipData` 与显式 `grantUriPermission`，但用户反馈修复无效。已知失败现象仍发生在 APK 下载完成后调用安装阶段，表现为安装器读取 updater FileProvider 时被系统拒绝，典型错误为 `Permission Denial ... FileProvider ... not exported`。

当前链路事实：

- 下载器把 APK 写入 `context.cacheDir/updates/musicfree-<versionCode>-<abi>.apk`。
- `app/src/main/res/xml/updater_file_paths.xml` 暴露 `cache/updates/`，与下载路径匹配。
- updater FileProvider 使用 `${applicationId}.updater-files`，`exported=false`、`grantUriPermissions=true`，安全边界正确。
- 当前安装 Intent 使用 `ACTION_VIEW + application/vnd.android.package-archive + content://...`。

本地 Android 15 emulator 验证显示：

- `ACTION_VIEW + APK MIME + content://...` 会解析到系统 Resolver，候选包含 `com.google.android.packageinstaller`，也包含第三方已安装的 RN MusicFree `fun.upup.musicfree`。
- `ACTION_INSTALL_PACKAGE + APK MIME + content://...` 只解析到系统 PackageInstaller。

这说明上一版只强化 URI grant，但仍保留了过宽的 `ACTION_VIEW` 路由；在有第三方 APK MIME handler 或 ROM 安装器多阶段分发时，授权可能给到入口 activity，而实际扫描 / 安装阶段读取 FileProvider 的组件并未稳定获得授权。

## 根因

根因不是 FileProvider 路径、authority 或 `exported=false`，也不是 APK 下载文件位置。根因是安装请求使用了泛化文件查看 action：

```kotlin
Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
```

`ACTION_VIEW` 语义是“打开这个文件”，不是“请求系统安装 APK”。它可能走系统 Resolver 或第三方 MIME handler。上一版的 `ClipData` 和显式 grant 只能提高 URI 授权成功率，但不能保证请求路由到 PackageInstaller，也不能保证安装器内部扫描组件就是 `queryIntentActivities(MATCH_DEFAULT_ONLY)` 返回的包。

## 方案

本轮采用小范围根因修复：

1. 安装 APK 的主 Intent 改为 `Intent.ACTION_INSTALL_PACKAGE`，继续设置 APK MIME、`content://` URI、`ClipData`、`FLAG_GRANT_READ_URI_PERMISSION` 和 `FLAG_ACTIVITY_NEW_TASK`。
2. 显式授权 helper 保留，但目标发现从单一 `queryIntentActivities(... MATCH_DEFAULT_ONLY)` 扩展为：
   - `resolveActivity(... MATCH_DEFAULT_ONLY)`；
   - `queryIntentActivities(... MATCH_DEFAULT_ONLY)`；
   - `queryIntentActivities(... 0)`；
   - 仅保留系统 / 更新系统应用包，结果用包名去重后授权，避免把 updater FileProvider URI 授权给第三方 APK MIME handler。
3. `ApkInstaller` 日志补充 `intentAction` 与 `grantTargetCount`，便于确认线上/测试机是否走到了 `ACTION_INSTALL_PACKAGE`，以及是否找到授权目标。
4. 仍然不导出 FileProvider，不硬编码厂商安装器包名，不改变 APK 下载、ABI 解析、校验和发布 manifest。
5. 若系统不允许未知来源安装，继续引导到 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，不把该授权问题与 URI 读取问题混在一起。

## 非目标

- 不改为 `PackageInstaller.Session` 静默/半静默安装；第三方应用仍需系统安装 UI。
- 不导出 updater FileProvider。
- 不绕过 Android “允许安装未知应用”授权。
- 不改 release 发布产物、ABI split、version.json schema。

## 验收

- `InstallIntentsTest` 覆盖：
  - APK 安装 Intent 使用 `ACTION_INSTALL_PACKAGE`，而不是 `ACTION_VIEW`。
  - URI 同时位于 `data` 与 `clipData`。
  - helper 会合并 `resolveActivity`、默认查询和宽松查询结果，仅授权系统安装器包，并对包名去重。
  - 未知来源设置 Intent 保持 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`。
- `:updater:testDebugUnitTest` 通过。
- `bash scripts/dev-harness/check.sh` 通过。
- `:app:assembleDebug` 通过。
- 可用设备上至少用系统解析或实际触发确认安装 Intent 不再走第三方 APK MIME handler。
