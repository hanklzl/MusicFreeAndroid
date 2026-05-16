# 基本设置运行态接入设计

> 文档状态：当前规范
> 适用范围：基本设置页中已具备 Android 底层能力或可低风险补齐的运行态设置。
> 直接执行：是（作为实现计划输入）
> 最后校验：2026-05-10

## 背景

`2026-05-09-settings-basic-alignment-design.md` 已完成基本设置 RN 分区和低风险下载/歌词/日志项的第一轮可见对齐，但大量行仍停留在 `待接入`。本轮目标是对照 RN 基本设置源码与当前 Android 运行态链路，把已具备底层能力的设置改为真实生效，并明确仍缺底层能力的项目继续禁用展示。

RN 参考：

- `../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx`
- `../MusicFree/src/utils/qualities.ts`
- `../MusicFree/src/pages/searchPage/components/resultPanel/results/musicResultItem.tsx`
- `../MusicFree/src/components/musicSheetPage/components/sheetMusicList.tsx`
- `../MusicFree/src/pages/musicDetail/index.tsx`
- `../MusicFree/src/pages/musicDetail/components/content/index.tsx`
- `../MusicFree/src/core/trackPlayer/index.ts`
- `../MusicFree/src/core/downloader.ts`

Android 当前入口：

- `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`
- `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- `feature/home/src/main/java/com/hank/musicfree/feature/home/*DetailViewModel.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
- `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
- `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- `downloader/src/main/java/com/hank/musicfree/downloader/quality/QualityFallback.kt`

## 目标

1. 基本设置页中以下 RN 项改为 DataStore 持久化并接入真实运行态：
   - 历史记录最多保存条数
   - 打开歌曲详情页时
   - 处于歌曲详情页时常亮
   - 点击搜索结果内单曲时
   - 点击专辑/歌单/榜单/歌手作品内单曲时
   - 新建歌单时默认歌曲排序
   - 默认播放音质
   - 默认播放音质缺失时
   - 默认下载音质缺失时
   - 使用移动网络播放
2. 保留已接入项：下载路径、最大同时下载数、默认下载音质、使用移动网络下载、歌词缺失时自动搜索歌词、日志包分享、清空日志。
3. 对暂缺 Android 底层能力或会改变现有架构的 RN 项继续禁用展示，不宣称运行态生效。
4. 所有新运行态设置补齐最小可回归测试，并以 Debug 构建作为收尾构建闸门。

## 运行态语义

| 设置 | RN key | Android 默认值 | 生效链路 |
|---|---|---:|---|
| 历史记录最多保存条数 | `basic.maxHistoryLen` | `50` | `AppPreferences.addSearchQuery()` 按当前上限裁剪搜索历史 |
| 打开歌曲详情页时 | `basic.musicDetailDefault` | `album` | `PlayerScreen` 首次进入按设置显示封面页或歌词页 |
| 处于歌曲详情页时常亮 | `basic.musicDetailAwake` | `false` | `PlayerScreen` 设置/清除 Activity `FLAG_KEEP_SCREEN_ON` |
| 点击搜索结果内单曲时 | `basic.clickMusicInSearch` | `playMusic` | 搜索结果点歌时只播放单曲，或用搜索结果替换播放队列 |
| 点击专辑内单曲时 | `basic.clickMusicInAlbum` | `playAlbum` | 专辑/歌单/榜单/歌手作品点歌时只播放单曲，或用当前列表替换播放队列 |
| 新建歌单时默认歌曲排序 | `basic.musicOrderInLocalSheet` | `Manual` | 新建用户歌单时写入 `Playlist.sortMode` |
| 默认播放音质 | `basic.defaultPlayQuality` | `standard` | 未显式指定音质解析音源时作为首选音质 |
| 默认播放音质缺失时 | `basic.playQualityOrder` | `asc` | 音源解析按 RN `getQualityOrder()` 候选顺序回退 |
| 默认下载音质缺失时 | `basic.downloadQualityOrder` | `asc` | 下载取源按 RN `getQualityOrder()` 候选顺序回退 |
| 使用移动网络播放 | `basic.useCelluarNetworkPlay` | `false` | 移动网络下阻断非本地曲目播放，保留本地文件/内容 Uri |

音质候选顺序以 RN `getQualityOrder()` 为准：

- `asc`：首选音质 → 更高音质 → 更低音质倒序。
- `desc`：首选音质 → 更低音质倒序 → 更高音质。

## 暂不开放项

以下项目保留禁用展示，原因是当前 Android 没有等价底层能力，或需要独立架构专项：

| 设置 | 原因 |
|---|---|
| 关联歌词方式 | 播放页目前已经提供搜索关联和本地导入，但没有 RN 的“输入歌曲 ID”入口 |
| 通知栏显示关闭按钮 | Media3 notification command 需要独立会话命令与 restart 语义设计 |
| 软件启动时自动更新插件 | 涉及后台网络、24h 节流、静默失败和插件一致性策略，需插件专项 |
| 安装插件时不校验版本 | Android 当前安装流程没有实际版本校验开关，开放会成为无效设置 |
| 启用插件懒加载 | 当前 QuickJS `LoadedPlugin` 为 eager load，懒加载需要插件元信息缓存与生命周期重做 |
| 允许与其他应用同时播放 | 需要 PlaybackService 音频焦点动态策略，不能只改 UI |
| 软件启动时自动播放歌曲 | 依赖播放队列/进度持久恢复专项 |
| 播放失败时尝试更换音源 | RN 会跨插件搜索相似歌曲，Android 目前仅有显式替代插件映射 |
| 播放失败时自动暂停 | 需要统一 Media3 error recovery 策略，避免自动跳过循环 |
| 播放被暂时打断时 | 需要音频焦点 duck/pause 策略专项 |
| 音乐缓存上限 | Android 当前媒体缓存是 Room 条数上限，不是播放字节缓存 |
| 清除音乐/歌词/图片缓存 | 需要统一 cache service 区分音源缓存、歌词缓存、Coil 图片缓存 |
| 记录错误日志/详细日志/调试面板/查看错误日志 | 当前日志规范为结构化日志默认开启，和 RN debug 开关语义不一致 |

## 验收

1. 设置页 UI：已接入行不再显示 `待接入`，点击/开关能回写 DataStore。
2. 搜索：`playMusic` 不替换队列，`playMusicAndReplace` 替换为搜索结果队列。
3. 歌单/专辑/榜单/歌手详情：`playMusic` 只播放当前曲目，`playAlbum` 替换为当前列表。
4. 播放详情：默认页和常亮设置在进入 `PlayerRoute` 后立即生效；用户在页面内手动切页后不被偏好流重置。
5. 播放取源：未显式指定音质时按 `defaultPlayQuality + playQualityOrder` 回退；手动切音质仍只尝试用户选择的音质。
6. 下载取源：按 `defaultDownloadQuality + downloadQualityOrder` 回退。
7. 移动网络播放：默认阻断远程曲目，本地平台或本地 Uri 不阻断。
8. 验证命令至少覆盖：
   - `./gradlew :data:testDebugUnitTest`
   - `./gradlew :feature:settings:testDebugUnitTest`
   - `./gradlew :feature:search:testDebugUnitTest`
   - `./gradlew :feature:home:testDebugUnitTest`
   - `./gradlew :feature:player-ui:testDebugUnitTest`
   - `./gradlew :plugin:testDebugUnitTest`
   - `./gradlew :player:testDebugUnitTest`
   - `./gradlew :downloader:testDebugUnitTest`
   - `./gradlew :app:assembleDebug`
