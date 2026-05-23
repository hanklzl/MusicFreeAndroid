# 下载更新模块

> 文档状态：当前规范
> 适用范围：`:updater` 模块的软件更新检查、APK 下载缓存、断点续传、前台服务、静默下载与安装入口。
> 直接执行：是
> 当前入口：[AGENTS](../../AGENTS.md) ｜ [DOCS_STATUS](../DOCS_STATUS.md)
> 最后校验：2026-05-23

## 模块边界

`:updater` 只负责软件自身更新，不负责歌曲下载。依赖方向保持 `:app` / `:feature:* -> :updater -> :core, :logging`，不得反向依赖 `:data`、`:player`、`:plugin` 或具体页面模块。

核心对象：

| 文件 / 类型 | 职责 |
|---|---|
| `UpdateCheckCoordinator` | 应用启动后的后台检查入口；Debug 构建跳过；不阻塞启动首屏。 |
| `UpdateChecker` / `UpdateState` | 拉取 `version.json`、版本比较、ABI 选择、维护全局更新状态。 |
| `UpdateDownloadManager` | 统一下载编排：启动前台服务、更新进度状态、处理手动下载与静默下载策略。 |
| `ApkDownloader` / `OkHttpApkDownloader` | 执行 APK 下载、缓存命中、断点续传、校验 size / sha256。 |
| `UpdateDownloadService` / `UpdateDownloadNotifier` | 前台服务与下载通知，保障后台下载不被系统轻易暂停。 |
| `UpdatePreferences` | 保存跳过版本、最后检查时间、静默下载开关、静默下载取消版本。 |
| `ApkInstaller` / `InstallStatusReceiver` | 通过 `PackageInstaller.Session` 交给系统安装器，不使用 FileProvider 安装 Intent。 |
| `UpdateDialogHost` / `ManualUpdateDialog` | 根层启动更新状态 UI 与手动检查更新 UI。 |

## 状态流

`UpdateState` 是更新模块的唯一 UI 状态源：

1. `Idle`：未检查或已回到空闲。
2. `Checking`：正在拉取远端 `version.json`。
3. `UpToDate`：当前版本不低于远端版本。
4. `Available(update, skipped, source)`：存在可用更新。
   - `source = Launch` 表示启动后台检查产物，默认不弹"发现新版本"窗口，而是尝试静默下载。
   - `source = Manual` 表示用户主动检查更新，继续展示确认下载弹窗。
5. `Downloading(update, progress, bytes, total)`：APK 下载中。手动和静默下载都走同一状态。
6. `ReadyToInstall(update, apkFile)`：APK 已缓存并校验通过。UI 此时提示用户安装。
7. `Failed(update, cause)`：检查、下载或安装失败。

启动检查不能在首屏路径同步等待网络、APK 下载或安装准备。`UpdateCheckCoordinator.start()` 只派发后台任务，具体下载由 `UpdateDialogHost` 看到 `Available(source = Launch)` 后交给 `UpdateDownloadManager.startSilentIfAllowed()`。

## APK 缓存与断点续传

缓存目录固定为 `context.cacheDir/updates`，文件名包含版本号和 ABI：

```text
musicfree-${versionCode}-${abi}.apk
musicfree-${versionCode}-${abi}.apk.part
```

下载规则：

- 若完整 APK 已存在，且 size 与 sha256 都匹配，直接返回缓存命中，不访问网络。
- 若 `.part` 存在且长度小于远端 size，下一次下载携带 `Range: bytes=<partLength>-` 断点续传。
- 若服务器返回 `206 Partial Content`，继续追加写入 `.part`。
- 若服务器忽略 Range 返回 `200 OK`，删除旧 `.part` 后从头写入，避免拼接损坏文件。
- 若服务器返回 `416 Range Not Satisfiable`，删除旧 `.part` 后从头重试当前镜像。
- 取消下载时保留 `.part`，供下次继续；size / sha256 / rename 等硬失败会删除 `.part`。
- 每次下载当前版本前，会清理 `updates` 目录中其他版本或其他 ABI 的 APK / `.part`，避免缓存长期膨胀。

完整 APK 必须通过 size 和 sha256 校验后才会从 `.part` rename 成 `.apk` 并进入 `ReadyToInstall`。

## 前台服务

所有手动下载和静默下载都通过 `UpdateDownloadManager` 启动 `UpdateDownloadService`：

- manifest 声明 `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_DATA_SYNC` 权限。
- service 类型为 `dataSync`。
- service 启动后立即 `startForeground()` 展示下载通知。
- 通知进度订阅 `UpdateChecker.state`，随 `Downloading` 更新。
- 通知取消按钮调用 `UpdateDownloadManager.cancelActiveDownload("notification_cancel")`。
- 下载完成后停止前台状态，并保留"软件更新已下载"通知；安装确认仍由应用内 `ReadyToInstallDialog` 触发。

如果前台服务无法启动，`UpdateDownloadManager` 会记录 `update_download_service_start_failed` 并进入失败状态。不要绕过 `UpdateDownloadManager` 直接调用 `ApkDownloader.download()`，否则会丢失前台服务和统一状态流。

## 静默下载策略

静默下载是用户配置项，入口在 `设置 -> 基本 -> 网络 -> Wi-Fi 下静默下载软件更新`，默认开启。

启动检查发现更新后的行为：

1. 不立即弹出"发现新版本"窗口。
2. 若静默下载开关开启、当前网络为 `NetworkType.WIFI`、该版本没有被用户取消过静默下载，则启动前台服务静默下载。
3. 下载完成并校验通过后进入 `ReadyToInstall`，此时弹出安装提示。
4. 用户取消静默下载后，`UpdatePreferences` 记录 `silent_download_canceled_version`，本版本后续启动检查不再自动静默下载。
5. 用户主动点"检查更新"或手动下载时，忽略静默下载开关与当前网络类型，并清除静默取消记录。

当前策略只允许 Wi-Fi。若未来要把以太网也视为允许网络，需要先扩展 `NetworkTypeDetector` 语义并补对应测试。

## 安装入口

下载完成后不要使用 `ACTION_VIEW`、`ACTION_INSTALL_PACKAGE` 或 FileProvider handoff。当前安装链路固定为：

1. `ReadyToInstallDialog` 调用 `ApkInstaller.install(apkFile)`。
2. `ApkInstaller` 创建 `PackageInstaller.Session`，把 APK 写入 session。
3. `InstallStatusReceiver` 接收安装结果广播。
4. 用户在系统安装器取消时回到 `Available`，再次安装时复用已缓存 APK，不重新下载。

这条链路保留了系统安装确认 UI，不能做静默安装。

## 日志与测试

关键日志事件：

| event | 场景 |
|---|---|
| `update_check_start` / `update_check_complete` / `update_check_failed` | 更新检查生命周期 |
| `update_silent_download_skipped` | 静默下载因配置、网络或版本取消记录被跳过 |
| `update_download_start` | 手动或静默下载启动 |
| `update_download_cancel_requested` | 用户或通知请求取消下载 |
| `update_download_service_start_failed` | 前台服务启动失败 |
| `apk_download_cache_hit` | 完整 APK 缓存命中 |
| `apk_download_complete` | APK 下载并校验完成，含 `resumedFromBytes` |
| `apk_download_failed` | 下载失败，含失败原因 |

当前守门测试：

```bash
./gradlew :updater:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest
```

涉及前台服务、设置注入、根层 UI 注入或跨模块构造器时，收尾还需要跑：

```bash
./gradlew :app:assembleDebug
```

## 修改清单

后续修改下载更新模块时至少检查：

- 是否仍通过 `UpdateDownloadManager` 编排下载。
- 是否保持启动检查不弹可用更新弹窗、静默下载完成后再提示安装。
- 是否保留 `.part` 取消续传能力。
- 是否仍用 `PackageInstaller.Session` 安装。
- 是否同步更新本文件和 [DOCS_STATUS](../DOCS_STATUS.md)。
