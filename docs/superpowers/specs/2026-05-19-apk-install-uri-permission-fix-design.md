# APK 安装 URI 授权修复设计

> 文档状态：已被取代（2026-05-22 PackageInstaller 迁移），保留作历史快照（见 [`2026-05-22-apk-install-package-installer-migration-design.md`](2026-05-22-apk-install-package-installer-migration-design.md)）
> 适用范围：更新功能下载完成后的 APK 安装 Intent 与 FileProvider URI 授权。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 日期：2026-05-19

## 背景

用户反馈：更新功能下载 APK 后调用系统安装器，MIUI 安装扫描阶段报错：

`Permission Denial: opening provider androidx.core.content.FileProvider ... com.miui.packageinstaller ... that is not exported`

当前实现位于 `:updater`：

- `ApkInstaller` 通过 `FileProvider.getUriForFile()` 生成 `${applicationId}.updater-files` 的 `content://` URI。
- `InstallIntents.installApk()` 使用 `ACTION_VIEW` + `application/vnd.android.package-archive`，并添加 `FLAG_GRANT_READ_URI_PERMISSION`。
- `app/src/main/AndroidManifest.xml` 中 updater FileProvider `exported=false` 且 `grantUriPermissions=true`，这是正确的安全边界。
- `app/src/main/res/xml/updater_file_paths.xml` 暴露 `cache/updates/`，与下载器写入目录匹配。

## 根因

问题不在 FileProvider 路径或 provider 是否应导出，而在安装 Intent 的 URI 临时授权链路不够稳。

只把 URI 放在 `Intent.data` 并添加 `FLAG_GRANT_READ_URI_PERMISSION`，在部分 ROM（本次为 MIUI 安装器）中可能无法稳定传递到安装器扫描阶段的内部组件。安装器随后以自身进程直接打开未导出的 FileProvider，因没有拿到 URI 读授权而被系统拒绝。

因此不能把 provider 改成 `exported=true`。正确修复是保留私有 provider，并在启动安装器前把 URI 授权写得更明确。

## 方案

采用小范围修复：

1. `InstallIntents.installApk()` 继续使用 `ACTION_VIEW` 和 APK MIME，避免改变现有安装入口行为。
2. 在同一个 Intent 上补 `ClipData.newRawUri(...)`，让 URI 授权既存在于 `data`，也存在于 `clipData`，适配只沿 clip grant 传播的安装器实现。
3. 启动安装器前通过 `PackageManager.queryIntentActivities(...)` 找到可处理该 Intent 的安装器 activity，并对每个目标包调用 `Context.grantUriPermission(packageName, uri, FLAG_GRANT_READ_URI_PERMISSION)`。
4. `ApkInstaller.install()` 在准备/启动安装 Intent 时记录稳定结构化日志，包括文件名、authority、显式授权目标数量和失败原因。
5. 捕获 `ActivityNotFoundException`、`SecurityException`、其他 `RuntimeException`，避免安装启动失败直接崩溃；失败时回到现有 `InstallBlocked` 状态，让 UI 给用户可操作入口。

## 非目标

- 不修改 FileProvider 的 `exported=false` 安全边界。
- 不改变 APK 下载、ABI 选择、sha256/size 校验逻辑。
- 不引入应用商店或 AAB 更新路径。
- 不新增 release 构建验收；本轮按仓库规则验证 Debug 构建。

## 验收

- `InstallIntentsTest` 覆盖：
  - 安装 Intent 保留 `ACTION_VIEW`、APK MIME、`FLAG_GRANT_READ_URI_PERMISSION`、`FLAG_ACTIVITY_NEW_TASK`。
  - 安装 Intent 携带指向同一 APK URI 的 `ClipData`。
  - 显式授权 helper 会去重后对所有解析到的安装器包调用 `grantUriPermission(...)`。
- 运行 `:updater:testDebugUnitTest`。
- 运行 `bash scripts/dev-harness/check.sh`。
- 运行 `:app:assembleDebug`。
- 若本机有设备/模拟器，可安装 Debug APK 并观察更新安装链路不再出现 `Permission Denial opening provider ... updater-files`。
