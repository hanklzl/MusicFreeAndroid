# 包名迁移前备份与恢复设计

- **状态**：当前规范（草案）
- **日期**：2026-05-17
- **适用范围**：旧包 `com.zili.android.musicfreeandroid` 迁移版的备份导出与恢复导入能力；后续新包 `com.hank.musicfree` 可复用恢复入口导入同一格式的迁移包。
- **不在范围**：本次不修改 `applicationId`、Gradle namespace、Kotlin package 路径，也不实现新包发布。

## 1. 背景与目标

计划将应用的发布包名从旧包 `com.zili.android.musicfreeandroid` 迁移到新包 `com.hank.musicfree`，并在后续把源码与文档引用统一到 `com.hank.musicfree` 包名前缀。Android 会把不同 `applicationId` 视为两个独立应用，新包无法直接读取旧包私有目录下的 Room 数据库、DataStore、插件文件、封面和主题背景。

本次先实现旧包迁移版：保持旧包 `applicationId` 不变，落地“备份与恢复”功能，让在野用户可以在旧包中导出迁移包。后续新包安装后，使用同一恢复入口导入迁移包，完成跨包数据迁移。

目标：

1. 设置页 `备份与恢复` 不再是占位，提供创建迁移包与从迁移包恢复两条用户路径。
2. 迁移包覆盖用户核心数据，排除可重建缓存、日志和临时文件。
3. 导出与恢复使用可校验、可回滚的文件级格式，避免直接操作运行中的 Room/DataStore 导致损坏。
4. 恢复流程明确提示：恢复会覆盖当前应用内数据、需要重启、生效后部分系统权限需要重新授权。

## 2. 迁移内容清单

### 2.1 迁移内容

- Room 数据库：`musicfree.db` 及同名 `-wal` / `-shm` 文件。
- 数据库内的用户歌单、歌单歌曲、默认“我喜欢”、收藏歌单与专辑、播放队列、听歌统计、歌词缓存、媒体源缓存、插件元数据缓存、下载记录和已下载曲目的应用内元数据。
- DataStore 主配置：`app_preferences.preferences_pb`，包括基础设置、播放设置、下载设置、插件排序/开关、主题选择、歌词设置等。
- 用户安装插件目录：`files/plugins/` 下的插件 JS 文件。
- 歌单封面目录：`files/playlist_covers/`。
- 自定义主题背景：`files/theme_background.*`。
- 迁移 manifest：备份格式版本、源包名、源 app 版本、数据库版本、创建时间、文件清单、大小和 SHA-256。

### 2.2 不迁移内容

- Logan 日志：`files/logan/`、`files/logan-cache/`。
- 反馈日志导出缓存：`cache/feedback/`。
- 更新 APK 缓存：`cache/updates/`。
- 下载中的临时分片和下载缓存：`cache/download/`。
- Coil / 图片等可重建缓存。
- 系统授予的权限状态。
- SAF 持久 URI 授权本身。

### 2.3 用户提示

迁移包会保留旧配置里的存储目录 URI 字符串，但 Android 不允许新包继承旧包的持久 URI 授权。新包恢复后，用户可能需要重新授权通知、媒体读取、悬浮窗、存储目录等权限。已下载到公共媒体库的音频文件本身不打进迁移包，只迁移应用内元数据和引用；如果文件仍在设备上，新包获得权限后可继续识别。

## 3. 备份格式

备份文件使用 ZIP 容器，扩展名为 `*.mfbackup`。内部路径使用固定白名单：

```text
manifest.json
db/musicfree.db
db/musicfree.db-wal
db/musicfree.db-shm
datastore/app_preferences.preferences_pb
files/plugins/*.js
files/playlist_covers/*
files/theme_background.*
```

`manifest.json` 至少包含：

- `schemaVersion`：迁移包格式版本，首版为 `1`。
- `sourcePackageName`：导出来源包名，旧包迁移版为 `com.zili.android.musicfreeandroid`。
- `createdAt`：ISO-8601 UTC 时间。
- `appVersionName` / `appVersionCode`：导出来源应用版本。
- `databaseVersion`：当前 Room schema 版本。
- `files[]`：每个条目的相对路径、字节数、SHA-256。

恢复时只接受白名单路径，拒绝绝对路径、`..` 路径穿越、重复 manifest 条目、hash 不匹配、必要文件缺失和不支持的 `schemaVersion`。

## 4. 架构与模块边界

新增备份恢复业务边界，UI 只依赖用例/Repository 风格接口，不直接拼接数据库或私有目录路径。

建议职责划分：

- `BackupRepository`：导出迁移包、登记待恢复包、读取当前恢复状态。
- `BackupArchiveWriter` / `BackupArchiveReader`：ZIP 写入、读取、manifest 生成与校验。
- `BackupFileSetProvider`：枚举允许迁移的数据库、DataStore 和 `files/` 内容。
- `PendingRestoreStore`：记录待恢复 staging 目录和状态，必须在 Room/DataStore 初始化前可读取。
- `BackupRestoreApplier`：冷启动早期执行替换和回滚。
- `SettingsType.Backup` 页面：展示状态和触发系统文件创建/选择器。

业务路径涉及文件 IO、数据库、DataStore、导入导出和错误降级，必须按日志规范补结构化日志。事件命名使用稳定小写 snake_case，例如 `backup_export_started`、`backup_export_succeeded`、`backup_export_failed`、`backup_restore_pending_registered`、`backup_restore_apply_succeeded`、`backup_restore_apply_failed`。

## 5. 导出流程

1. 用户进入 `备份与恢复` 页面，点击“创建备份/迁移包”。
2. App 调起系统文件创建器，默认文件名如 `MusicFree-backup-20260517.mfbackup`。
3. 获得目标 `Uri` 后，导出服务在后台执行：
   - 暂停或串行化导出过程中的写入入口，至少保证导出文件集一致。
   - 对 Room 执行 WAL checkpoint，确保 `musicfree.db` 与 `-wal` / `-shm` 可作为一致快照读取。
   - 枚举迁移白名单文件，逐个计算 SHA-256 并写入 ZIP。
   - 生成并写入 `manifest.json`。
4. UI 展示 `准备中 / 写入中 / 已完成 / 失败` 状态。

导出失败不得留下半成品状态；如果系统文件创建器已经产生了空文件，失败提示需说明可手动删除该文件。

## 6. 恢复流程

恢复分两段执行，避免运行中覆盖 Room/DataStore。

### 6.1 UI 登记阶段

1. 用户点击“从备份恢复”，调起系统文件选择器。
2. 选择 `.mfbackup` 或 zip 后，应用先解压到 `files/restore-staging/<id>/`。
3. 完整校验 manifest、路径白名单、必要文件、大小和 SHA-256。
4. 校验通过后，展示确认弹窗：恢复会覆盖当前应用内数据、需要重启、系统权限不会继承。
5. 用户确认后，写入 pending restore 状态，提示用户重启应用。

### 6.2 冷启动应用阶段

1. `Application.onCreate()` 早期、Room/DataStore 初始化之前检查 pending restore。
2. 将当前目标文件移动到 `files/restore-backup/<id>/`。
3. 将 staging 内文件移动到目标位置。
4. 成功后清理 pending 状态、staging 和 restore-backup，再继续正常启动。
5. 失败时尽量把 `restore-backup` 移回原位置，记录结构化错误，并保留可诊断状态供设置页展示。

该流程不做运行中强杀或静默重启。首版以“登记后提示用户手动重启”为准，避免引入进程生命周期不可控行为。

## 7. UI 设计

`SettingsType.Backup` 替换当前占位内容，页面至少包含：

- 页面说明：用于迁移到新包或备份当前应用内数据。
- 主按钮“创建备份/迁移包”。
- 主按钮“从备份恢复”。
- 最近一次导出/恢复状态。
- 恢复确认弹窗，必须包含“覆盖当前应用内数据”“需要重启”“权限需要重新授权”三类提示。

保留并扩展既有稳定锚点：

- `settings.backup.root`
- `settings.backup.entry`

新增按钮和状态区域需提供稳定 testTag，方便后续 Maestro / Compose 测试验收。

## 8. 错误处理与回滚

错误类型至少覆盖：

- 用户取消系统文件创建/选择。
- 备份包格式不支持。
- manifest 缺失或损坏。
- 必要文件缺失。
- hash / size 不匹配。
- 路径穿越或白名单外路径。
- staging 写入失败。
- 冷启动替换失败。
- 回滚失败。

恢复前必须完整校验 staging。冷启动替换时必须先备份当前目标文件，再替换新文件。若替换失败，尽量恢复旧文件；若回滚也失败，记录 `backup_restore_rollback_failed`，并在设置页展示“恢复失败，请导出日志或联系开发者”的明确状态。

## 9. 测试与验收

### 9.1 单元测试

- 构造临时数据库、DataStore、插件、封面、主题背景，导出后打开 ZIP 校验 `manifest.json`、路径白名单、SHA-256 和排除项。
- 损坏 manifest、缺失数据库、hash 不匹配、路径穿越、白名单外路径必须失败，且不得写入目标目录。
- 模拟替换失败，验证旧数据仍可回滚。

### 9.2 UI / 入口测试

- `SettingsType.Backup` 不再显示“待接入”占位。
- 页面存在“创建备份/迁移包”和“从备份恢复”入口。
- 恢复确认弹窗包含覆盖当前数据、需重启、权限不会继承等关键文案。

### 9.3 本地验收命令

```bash
./gradlew :data:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDebug --no-daemon
bash scripts/dev-harness/check.sh
git diff --check
```

如果实现过程中新增或修改 Compose Screen、测试基建、日志路径，动手前必须读取并遵守对应 Dev Harness rules：

- `docs/dev-harness/ui/rules.md`
- `docs/dev-harness/test/rules.md`

## 10. 后续衔接

本次完成旧包迁移版后，下一阶段再修改 `applicationId`、Gradle namespace、Kotlin package 和文档引用到 `com.hank.musicfree`。新包只需要复用本设计的恢复能力，并在发布说明中要求用户先使用旧包迁移版导出 `.mfbackup`，再安装新包导入。
