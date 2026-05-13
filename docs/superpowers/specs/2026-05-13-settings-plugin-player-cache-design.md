# 基础设置插件、播放与缓存接入规格

文档状态：当前规格  
适用范围：基础设置页中插件、播放、缓存相关待接入项  
参考来源：`../../DOCS_STATUS.md`、`../../dev-harness/INDEX.md`、`../../dev-harness/ui/rules.md`、`../../dev-harness/plugin/rules.md`、`../../dev-harness/player/rules.md`、`../../dev-harness/test/rules.md`、`../../../MusicFree/src/pages/setting/basicSetting.tsx`、`../../../MusicFree/src/entry/bootstrap/bootstrap.ts`

## 背景

基础设置页已经接入搜索、歌曲详情、下载、歌词等部分能力，但插件、播放和缓存分区仍有多项占位。RN 原版在这些设置上提供启动期插件自动更新、插件版本校验开关、插件懒加载、音频焦点策略、启动自动播放、失败恢复策略、缓存上限和缓存清理能力。Android 侧需要把这些条目从 UI 占位推进到可持久化、可执行、可验证的功能。

## 目标

1. 插件分区接入：
   - 软件启动时自动更新插件。
   - 安装插件时不校验版本。
   - 启用插件懒加载。
2. 播放分区接入：
   - 允许与其他应用同时播放。
   - 软件启动时自动播放歌曲。
   - 播放失败时尝试更换音源。
   - 播放失败时自动暂停。
   - 播放被暂时打断时的处理方式与降低音量比例。
3. 缓存分区接入：
   - 音乐缓存上限设置。
   - 清理音乐缓存、歌词缓存、图片缓存。
4. 所有新增设置必须持久化到 DataStore，并通过现有 ViewModel + Compose 设置页展示和修改。
5. 涉及插件、播放、缓存的失败、降级或批量操作必须补结构化日志。

## 非目标

1. 不重写插件加载架构或 QuickJS 执行模型。
2. 不引入新的播放器内核。
3. 不实现完整 RN 相似歌曲跨插件搜索兜底；本次播放失败恢复优先做 Android 当前播放项的可执行音源刷新与队列策略。
4. 不新增 Release 验收闸门；本次不是签名、R8 或发布链路改动。

## RN 对齐点

### 插件

- `basic.autoUpdatePlugin`：默认关闭；启动时若开启且距离上次更新超过 24 小时，遍历可更新插件并静默更新。
- `basic.notCheckPluginVersion`：默认关闭；开启后跳过插件兼容版本校验。
- `basic.lazyLoadPlugin`：默认关闭；开启后启动时优先装载插件缓存，按需或延迟加载真实插件。

### 播放

- `basic.notInterrupt`：默认关闭；开启后不主动抢占音频焦点。
- `basic.autoPlayWhenAppStart`：默认关闭；开启后应用启动恢复队列并开始播放。
- `basic.tryChangeSourceWhenPlayFail`：默认关闭；播放失败后尝试刷新当前曲目可播放音源。
- `basic.autoStopWhenError`：默认关闭；关闭时播放失败后尝试切到下一首，开启时停在当前错误状态。
- `basic.tempRemoteDuck`：默认暂停；可选暂停或降低音量。
- `basic.tempRemoteDuckVolume`：默认 0.5；降低音量时生效。

### 缓存

- `basic.maxCacheSize`：默认 512 MB，输入范围 100 MB 到 8192 MB。
- 清理音乐缓存、歌词缓存、图片缓存应提供用户可触发动作，并在完成后刷新展示状态。

## Android 设计

### 持久化模型

在 `AppPreferences` 增加以下设置项：

- 插件：`autoUpdatePlugins`、`skipPluginVersionCheck`、`lazyLoadPlugins`。
- 插件内部状态：`pluginAutoUpdateLastAtEpochMs`。
- 播放：`allowConcurrentPlayback`、`autoPlayWhenAppStart`、`tryChangeSourceWhenPlayFail`、`autoStopWhenError`、`audioInterruptionAction`、`audioInterruptionDuckVolume`。
- 缓存：`maxMusicCacheSizeBytes`。

`lazyLoadPlugins` 默认值改为与 RN 一致的关闭。播放与插件新增开关默认关闭；缓存上限默认 512 MB。

### 设置页

基础设置页移除相关 `PendingValueRow`，改为真实可点击或可切换条目：

- 开关型设置使用 `SettingsSwitchRow`。
- 枚举或数值设置沿用 `BasicSettingsDialog`。
- 缓存清理使用 `SettingsActionRow`，执行期间由 ViewModel 暴露状态并给出结果提示。

### 插件运行时

- 启动期新增插件自动更新协调器，在应用启动后异步检查 `autoUpdatePlugins` 与 24 小时间隔，满足条件时调用插件更新能力。
- `skipPluginVersionCheck` 仅跳过 Android 现有插件兼容版本门禁，不屏蔽插件解析、安装、更新的其他错误。
- 修改懒加载开关后触发插件管理器 reload，使运行态尽快与设置一致。

### 播放运行时

- `PlaybackRuntimeSettings` 扩展播放设置读取接口，由 `AppPlaybackRuntimeSettings` 从 `AppPreferences` 提供。
- `PlaybackService` 根据 `allowConcurrentPlayback` 配置 Media3 音频焦点策略。
- 新增启动播放协调器：恢复已持久化队列；若 `autoPlayWhenAppStart` 开启则自动播放当前曲目。
- `PlayerController` 在播放错误时：
  - 先保留现有过期 URL 刷新逻辑。
  - 若开启 `tryChangeSourceWhenPlayFail`，尝试重新解析当前曲目音源并替换播放。
  - 若仍失败，`autoStopWhenError` 开启则暂停；关闭则在队列存在下一首时切歌。

### 缓存运行时

- 音乐缓存上限以字节持久化，UI 以 MB 展示和编辑。
- 媒体缓存写入后按配置上限进行轻量裁剪，按最旧条目优先删除。
- 歌词缓存提供全量清理。
- 图片缓存清理走 Coil 磁盘缓存清理能力；失败时记录日志并向 UI 返回失败结果。

## 验收标准

1. 基础设置页相关占位项消失，插件、播放、缓存设置均可交互并持久化。
2. 重启 ViewModel 后新增设置保持用户选择。
3. 插件自动更新开关开启后，启动协调器按 24 小时间隔触发更新；关闭时不触发。
4. 插件版本校验跳过开关只影响版本门禁，不影响其他安装失败路径。
5. 播放失败策略可由单元测试覆盖：刷新音源、暂停、切下一首。
6. 缓存清理动作可通过 Repository 或 ViewModel 单元测试覆盖。
7. 通过相关模块单元测试、`git diff --check` 和 `:app:assembleDebug`。
