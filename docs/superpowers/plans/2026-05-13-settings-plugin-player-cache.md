# 基础设置插件、播放与缓存接入实施计划

文档状态：当前实施计划  
适用范围：`docs/superpowers/specs/2026-05-13-settings-plugin-player-cache-design.md`

## 约束

- 开发目录：`.worktrees/settings-plugin-player-cache`
- 参考 RN：`../../../MusicFree/src/pages/setting/basicSetting.tsx`、`../../../MusicFree/src/entry/bootstrap/bootstrap.ts`
- 必读守门：`../../dev-harness/ui/rules.md`、`../../dev-harness/plugin/rules.md`、`../../dev-harness/player/rules.md`、`../../dev-harness/test/rules.md`
- 默认收尾验证使用 Debug 构建，不执行 Release 构建。

## 实施步骤

### 1. 偏好与公共模型

- 在 `AppPreferences` 增加插件、播放、缓存相关 DataStore key、Flow 和 setter。
- 将 `lazyLoadPlugins` 默认值调整为关闭。
- 在 core 层增加音频临时打断处理枚举。
- 扩展 `PlaybackRuntimeSettings` 与 `AppPlaybackRuntimeSettings`。
- 更新 `AppPreferencesTest` 覆盖新增默认值与写入读取。

### 2. 基础设置 UI 与 ViewModel

- 扩展 `BasicSettingsUiState`，接入新增设置状态和缓存操作状态。
- 为插件、播放、缓存新增 ViewModel setter 与 action 方法。
- 将基础设置页插件、播放、缓存占位行替换为真实 row。
- 更新 `BasicSettingsContentTest`、`SettingsViewModelTest` 和相关 row 文案测试。

### 3. 插件功能

- 新增启动期插件自动更新协调器，应用启动后异步触发。
- 在插件版本门禁处接入 `skipPluginVersionCheck`。
- 修改懒加载设置后调用插件管理器 reload。
- 增加插件自动更新与版本门禁跳过的单元测试。

### 4. 播放功能

- 在 `PlaybackService` 接入允许同时播放的音频焦点策略。
- 新增启动播放协调器，恢复队列并按设置决定是否自动播放。
- 在 `PlayerController` 接入播放失败刷新音源、自动暂停和自动切下一首策略。
- 更新播放器相关 fake 与单元测试。

### 5. 缓存功能

- 扩展媒体缓存 DAO / Repository，支持全量清理和按上限裁剪。
- 扩展歌词缓存 DAO / Repository，支持全量清理。
- 增加图片缓存清理服务。
- 设置页 action 调用对应缓存服务，并记录结果日志。
- 增加 Repository 与 ViewModel 单元测试。

### 6. 验证

- 运行受影响模块单元测试：
  - `./gradlew :data:testDebugUnitTest`
  - `./gradlew :plugin:testDebugUnitTest`
  - `./gradlew :player:testDebugUnitTest`
  - `./gradlew :feature:settings:testDebugUnitTest`
  - `./gradlew :app:testDebugUnitTest`
- 运行开发守门：
  - `python3 scripts/dev-harness/grep-check.py`
  - `git diff --check`
- 运行默认构建闸门：
  - `./gradlew :app:assembleDebug --no-daemon`
