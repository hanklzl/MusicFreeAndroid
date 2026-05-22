# APK 安装入口迁移到 PackageInstaller.Session 设计

> 文档状态：当前规范（本轮修复）
> 适用范围：更新功能下载完成后的 APK 安装入口，取代两轮基于 Intent+FileProvider 的修复。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 日期：2026-05-22

## 背景

### 第一轮修复（2026-05-19，commit `96a79737`）

MIUI 用户报告 APK 下载完成后安装阶段崩溃：

`Permission Denial: opening provider androidx.core.content.FileProvider ... com.miui.packageinstaller ... that is not exported`

修复保留 `ACTION_VIEW + APK MIME`，新增 `ClipData` 与显式 `grantUriPermission`，将权限目标扩展到所有 `queryIntentActivities` 候选包。验收仅靠单测，未经真机验证。

### 第二轮修复（2026-05-20，commit `1b5f5c0b` / `6c462908`）

上轮修复用户反馈仍无效。分析认为 `ACTION_VIEW` 语义过宽，改为 `ACTION_INSTALL_PACKAGE` 精确路由到系统安装器。以"本地 Android 15 emulator 解析通过"作为唯一验收证据。用户再次反馈"安装继续报错"。

### 真机表现

两轮修复均无法在 MIUI 等主流 ROM 上可靠触发系统安装确认 UI。根本原因在于架构选型，而非授权细节。

## 根因

- `Intent.ACTION_INSTALL_PACKAGE` 自 API 25 deprecated，Android 官方文档明确建议使用 `PackageInstaller`。
- 只要安装入口依赖 `content://` URI 跨进程传给厂商安装器，FileProvider 临时授权就会在各家 ROM 安装器包装层内丢失——入口 Activity 拿到授权，实际扫描组件未必能拿到。
- 这是 `superpowers:systematic-debugging` Phase 4.5 的典型信号：连续 2 次针对同一根因的局部修复未收敛，说明需要质疑架构选型本身，而不是继续在 Intent 标志位上打补丁。

## 方案

以 `PackageInstaller.Session` 替代 Intent 路由，彻底消除跨进程 URI 授权问题。

### 核心安装流程

1. **闸门**：安装前检查 `canRequestPackageInstalls()`，未授权则引导到 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，与旧实现保持一致。
2. **创建 Session**：在 `withContext(Dispatchers.IO)` 内调用 `PackageInstaller.createSession(SessionParams(MODE_FULL_INSTALL))`，设置 `setAppPackageName`、`setInstallReason(PackageManager.INSTALL_REASON_USER)`。Session 操作整体 try/catch，异常时调用 `abandonSession` 避免泄漏。
3. **写入 APK**：`openSession().use { session -> session.openWrite("base.apk", 0, fileLength).use { out -> inputStream.copyTo(out); session.fsync(out) } }`。
4. **提交 Session**：`session.commit(pendingIntent.intentSender)`，其中 PendingIntent 用 `PendingIntentCompat.getBroadcast(..., isMutable = true)`（AndroidX SDK 兼容 gate），target 为 `InstallStatusReceiver`。
5. **防重复点击**：UI 在 `ReadyToInstall` 分支加 `installing` 闸门，`SessionApkInstaller.install()` 被调用后立即过渡到中间状态，避免并发开启多个 Session。

### InstallStatusReceiver 与 InstallStatusHandler

- `InstallStatusReceiver`：manifest 注册，`exported=false`，intent-filter action 为 `com.hank.musicfree.updater.INSTALL_STATUS`，显式广播通过 `intent.setPackage(applicationId)` 限定自身。
- `InstallStatusHandler`（`@Singleton`，`@AndroidEntryPoint` thin receiver 委托）：
  - `STATUS_PENDING_USER_ACTION`：从 `Intent.EXTRA_INTENT` 取到系统确认 Intent，加 `FLAG_ACTIVITY_NEW_TASK` 后 `startActivity`，弹系统安装确认 UI。
  - `STATUS_SUCCESS`：记录结构化日志（含 versionCode、abi）。
  - `STATUS_FAILURE_ABORTED`：用户主动取消安装确认，记录 `apk_install_user_canceled` 日志，仅在 `ReadyToInstall` 时回到 `transitionAvailable`，不进入 Failed 状态。
  - 其余 `STATUS_FAILURE_*`：日志记录 status code、statusMessage、packageName；仅在当前 updater 状态为 `ReadyToInstall`（或对应中间态）时调用 `checker.transitionFailed(update, InstallFailed)`，避免误触状态机。

## 关键日志事件

| 事件名 | 触发位置 | 含义 | 关键字段 |
|--------|---------|------|---------|
| `apk_install_trigger` | `ApkInstaller.install()` 入口 | 安装流程开始 | `fileName` |
| `apk_install_start_failed` | session 创建/写入/commit 失败的 typed catch | Session 启动阶段异常 | `fileName`, `authority`, `reason` |
| `apk_install_session_committed` | `session.commit()` 之后 | Session 已成功提交，等待系统回调 | `fileName`, `sessionId`, `bytes` |
| `apk_install_session_abandoned` | session catch 块 abandon 之后 | Session 异常被 abandon，附带原因 | `sessionId`, `reason`, `abandonError` |
| `apk_install_user_action_pending` | handler 收到 `STATUS_PENDING_USER_ACTION` | 系统要求用户确认安装 | `statusMessage` |
| `apk_install_succeeded` | handler 收到 `STATUS_SUCCESS` | 安装成功 | `statusMessage`, `versionCode`, `abi` |
| `apk_install_failed` | handler 收到 `STATUS_FAILURE_*`（不含 ABORTED 已分流） | 系统报告安装失败 | `status`, `statusMessage`, `packageName` |
| `apk_install_user_canceled` | handler 收到 `STATUS_FAILURE_ABORTED` | 用户在系统确认 UI 取消安装 | `statusMessage`, `packageName` |

## 非目标

- 不引入静默/半静默安装（`INSTALL_REASON_UNKNOWN` 或系统权限）。
- 不保留 `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE` 兜底——根因就是 Intent 路由不稳，保留兜底会掩盖问题。
- 不改变 release 发布产物、ABI 解析、SHA256/size 校验逻辑。
- 不引入第三方安装库（如 Ackpine）。
- 不新增对外 FileProvider provider（updater FileProvider 已随本轮重构移除）。

## 验收

### 自动化

- `./gradlew :app:assembleDebug` 通过，无新增警告。
- `./gradlew :updater:testDebugUnitTest` 通过。新增：
  - `SessionApkInstallerTest`：闸门（未授权 → 引导）/ 正常路径（Session 创建 → commit）/ IOException（abandon 被调用）共 3 case，跨 SDK 29/26/API gate 模拟共 9 次运行。
  - `InstallStatusHandlerTest`：PENDING_USER_ACTION / SUCCESS / FAILURE+ReadyToInstall / FAILURE+其他 state 共 4 case。
- `bash scripts/dev-harness/check.sh` 通过。

### 运行态（人工验收，真机必须覆盖）

需在至少一台真机（MIUI / OPPO / 三星任一）和 Pixel/AVD 上验证：

1. **主路径**：检查更新 → 下载 → 点击安装 → 系统弹安装确认 UI → 完成安装。
2. **未知来源闸门**：关闭"安装未知应用"→ 点击安装 → 引导到系统授权页。
3. **用户取消**：在系统确认 UI 点取消 → updater 回到 Available 状态，可重试下载/安装。
4. **损坏 APK / 包冲突**：替换为非法 APK → Session commit 返回 `STATUS_FAILURE_INVALID` → UI 显示 InstallFailedDialog（"安装失败" + "重试" 按钮），重试按钮回到 Available 重新下载。

## 历史 spec 关系

- 取代：[`2026-05-19-apk-install-uri-permission-fix-design.md`](2026-05-19-apk-install-uri-permission-fix-design.md)、[`2026-05-20-apk-install-intent-route-fix-design.md`](2026-05-20-apk-install-intent-route-fix-design.md)（两份 spec 已同步加上"已被取代"标记）。
- 相关 incident：[INC-2026-0023](../../dev-harness/incidents/INC-2026-0023.md)（见 `docs/dev-harness/incidents/index.md`）。
