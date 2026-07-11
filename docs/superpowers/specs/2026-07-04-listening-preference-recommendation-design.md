# 听歌偏好推荐歌单设计

> 文档状态：当前规范
> 适用范围：基于本地听歌偏好画像生成“今日推荐歌单”的 Android-only 增强功能；不修改插件协议。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 关联规则：[UI rules](../../dev-harness/ui/rules.md)、[Plugin rules](../../dev-harness/plugin/rules.md)、[Runtime rules](../../dev-harness/runtime/rules.md)、[Test rules](../../dev-harness/test/rules.md)
> RN 参考：[plugin.d.ts](../../../../MusicFree/src/types/plugin.d.ts)、[recommendSheets](../../../../MusicFree/src/pages/recommendSheets/)
> 最后校验：2026-07-11

## 2026-07-11 第一版实现决策

- 首页保留 RN 对齐的四宫格，在其后新增独立“今日推荐”轻卡片；不替换原“推荐歌单”入口。
- 偏好画像使用最近 30 个本地自然日内最多 5,000 条 `listen_event`，按最近性、有效播放时长和完成状态加权。
- 候选召回复用 `getRecommendSheetsByTag` 与 `search(query, 1, "sheet")`；不修改 `PluginApi`。
- 跨插件最多 4 路并行，同一插件内串行；单次插件调用上限 15 秒。
- 每日结果与最近曝光统一保存在现有 `runtime_snapshots` 的 `today_recommendation` namespace，保留 8 份、TTL 8 天，不新增 Room 表或 migration。
- 推荐列表最多 12 项，并限制单一平台占比；手动刷新保留旧内容直到新结果成功。

## 背景

用户希望 MusicFreeAndroid 能根据听歌内容理解用户偏好，并推荐更符合口味的内容。竞品中，QQ 音乐有每日推荐、猜你喜欢和雷达模式等个性化入口；网易云音乐有每日推荐和私人漫游。它们共同点是：推荐入口需要同时利用历史行为、口味画像、内容候选池和可解释反馈。

MusicFreeAndroid 与平台 App 不同：它没有中心化曲库，也不能直接访问 QQ 音乐、网易云音乐的用户画像服务。当前可用候选来自插件能力。现有插件协议支持推荐歌单与歌单搜索，但这些能力不是所有插件都具备，也不能要求插件消费本地用户偏好参数。

因此，本设计采用 Android 本地编排层：本地听歌记录生成偏好画像，画像只用于选择现有插件请求和本地重排，不修改插件 API。

## 已确认现状

### 可用偏好信号

基础歌曲模型 `MusicItem` 已包含：

- `id` / `platform`：歌曲身份与平台来源。
- `title` / `artist` / `album`：标题、歌手、专辑。
- `duration` / `artwork` / `qualities`：时长、封面、音质能力。
- `raw`：插件返回的额外字段。

听歌统计已落库 `listen_event`，包含：

- `playedAtMs`：播放时间。
- `playedSeconds`：有效播放秒数。
- `completed`：是否完整播放或接近结尾。
- `musicId` / `platform` / `title` / `artistRaw` / `album` / `artwork`。
- `language` / `genre`：从 `raw.language/lang`、`raw.genre/style/category/tags` 提取的语种和曲风。
- `mergeKey`：按标题和主歌手合并同歌。

歌手是强信号；曲风和语种是中等信号，依赖插件是否提供可识别字段。

### 插件能力边界

现有插件协议包含：

- `getRecommendSheetTags(): RecommendSheetTagsResult?`
- `getRecommendSheetsByTag(tag, page): PaginationResult<MusicSheetItemBase>?`
- `search(query, page, type)`，其中搜索类型包含 `sheet`。

这些是可选能力，不是所有插件都支持。当前 Android 已通过 `PluginInfo.supportedMethods` 检测实际 JS 方法，并在推荐歌单页面按 `getRecommendSheetsByTag` 过滤。

本设计明确不修改插件能力：

- 不新增 `getPersonalizedRecommendSheets`。
- 不给 `getRecommendSheetsByTag` 增加第三个参数。
- 不向 tag payload 注入用户偏好字段。
- 不要求插件作者适配本地画像。

插件只负责返回候选歌单；个性化只发生在 Android 本地。

## 目标

1. 基于本地听歌历史生成可解释的 `PreferenceProfile`。
2. 使用现有插件推荐歌单能力和歌单搜索能力获取候选歌单。
3. 在本地对候选歌单去重、打分、排序，生成“今日推荐歌单”。
4. 首页提供轻入口，推荐页展示完整结果和推荐理由。
5. 插件能力缺失、请求失败、画像样本不足时都有明确降级。
6. 不修改插件协议，不破坏 RN 兼容和旧插件行为。

## 非目标

- 不做连续播放的私人 FM / 私人漫游。
- 不直接推荐单曲。
- 不训练模型，不引入云端推荐服务。
- 不对插件 JS 协议做破坏性或扩展性变更。
- 不把 QuickJS、插件实例、Coroutine job、Media3 对象持久化。
- 不在冷启动首屏同步执行推荐召回。
- 不做 Release 构建验收；普通功能以 Debug 构建为默认闸门。

## 总体架构

```text
listen_event
  -> PreferenceProfileBuilder
  -> RecommendationQueryPlanner
  -> PluginCandidateFetcher
       -> getRecommendSheetTags / getRecommendSheetsByTag
       -> search(query, page, "sheet")
  -> PlaylistRanker
  -> DailyRecommendationRepository
  -> TodayRecommendationViewModel
  -> 首页入口 / 今日推荐歌单页
```

新增能力建议放在 `:data` 与 `:feature:home`：

- `:data`：画像查询、推荐快照、曝光记录、Repository。
- `:feature:home`：今日推荐入口、页面 ViewModel 和 UI。

如果实现时发现 `:data` 依赖 `:plugin` 会违反模块方向，则把插件候选获取编排放在 `:feature:home` 或新增 feature 层 orchestrator，`data` 只保存画像和快照。

## 偏好画像

`PreferenceProfile` 第一版包含：

```kotlin
data class PreferenceProfile(
    val generatedAt: Long,
    val windowDays: Int,
    val topArtists: List<WeightedToken>,
    val topGenres: List<WeightedToken>,
    val topLanguages: List<WeightedToken>,
    val topPlatforms: List<WeightedToken>,
    val recentSongs: List<SongIdentity>,
    val confidence: ProfileConfidence,
    val signature: String,
)
```

权重来源：

- 播放次数。
- 总播放时长。
- 完整播放。
- 最近播放。
- 样本量。

建议权重：

```text
tokenScore =
  playCountWeight
  + totalSecondsWeight
  + completedWeight
  + recencyWeight
  - lowSamplePenalty
```

置信度：

- 有效听歌事件少于 10 首：低置信度。
- `genre` / `language` 覆盖率低：对应维度低置信度，但不影响歌手画像。
- 画像 signature 由 top artists / genres / languages / platforms 和样本窗口生成，用于判断缓存是否仍匹配。

## 候选召回

候选召回分两路，互为补充。

### 推荐歌单 tag 召回

适用于支持 `getRecommendSheetsByTag` 的插件。

流程：

1. 调用 `getRecommendSheetTags()` 获取插件原始 tag。
2. 本地用画像 token 匹配 tag 的 `title` 和原始字段。
3. 匹配到 tag 时，调用 `getRecommendSheetsByTag(tag, page = 1)`。
4. 没有匹配 tag 时，调用插件默认 tag。
5. 只传插件原始 tag payload，不注入本地画像字段。

tag 匹配示例：

| 画像 token | 匹配词 |
|---|---|
| `yue` | 粤语、港乐、Cantonese |
| `zh-CN` | 华语、国语、中文 |
| `folk` | 民谣、folk、治愈、安静 |
| `pop` | 流行、pop、华语流行 |
| `rock` | 摇滚、rock |

`getRecommendSheetTags()` 失败或返回空时，不直接判定插件不可用；仍可用默认 tag 试探 `getRecommendSheetsByTag`。

### 歌单搜索召回

适用于支持 `sheet` 搜索的插件。

本地根据画像生成 query：

- `周杰伦 歌单`
- `陈奕迅 粤语`
- `粤语 经典`
- `民谣 治愈`
- `华语 R&B`
- `最近常听歌手 + 歌单`

约束：

- 每个插件的 query 数量设上限。
- 每个 query 默认只拉第一页。
- 慢插件必须有 bounded timeout。
- 搜索失败只影响该来源，不中断整体推荐。

### 冷启动召回

当画像置信度不足时，使用冷启动 query 和默认 tag：

- 推荐歌单默认 tag。
- `热门 歌单`
- `华语 流行`
- `经典 歌单`
- `新歌 推荐`

冷启动结果要明确标记为低个性化，不展示“因为你常听...”这类文案。

## 候选模型

候选歌单统一归一化：

```kotlin
data class PlaylistCandidate(
    val identity: SheetIdentity,
    val item: MusicSheetItemBase,
    val source: CandidateSource,
    val sourcePlatform: String,
    val matchedTokens: List<MatchedToken>,
    val fetchedAt: Long,
)

sealed interface CandidateSource {
    data class RecommendTag(val tagId: String, val tagTitle: String?) : CandidateSource
    data class SheetSearch(val query: String) : CandidateSource
}
```

## 本地打分与去重

第一版不拉完整歌单详情做重型精排，只对候选字段粗排。

加分项：

- 标题、描述、`artist`、`raw` 命中 top artist。
- 标题、描述命中曲风或语种标签。
- 来自用户常用平台。
- 来自与画像匹配的推荐 tag。
- 同一候选同时被 tag 召回和搜索召回。
- `worksNum` 合理且有封面。

降分项：

- 今日或近期已经曝光。
- 与近期推荐重复。
- 标题和描述过空。
- 缺少 `id` 或 `platform`。
- 同一插件候选过多。

去重：

- 主键：`platform:id`。
- 辅助：规范化标题相同且封面相同，保留高分候选。
- 搜索召回和 tag 召回命中同一歌单时合并理由。

插件公平性：

- 每个插件在最终列表中的数量设上限。
- 如果只有一个插件可用，允许突破上限，但 reason 中保留来源统计。

## 推荐理由

推荐理由只使用本地可解释信号：

- `因为你最近常听 周杰伦`
- `偏好 粤语 / 民谣`
- `来自你常用的 网易云 插件`
- `和你最近常听的风格相近`
- 低置信度：`先听一些歌，推荐会更准`

不能编造插件未返回的信息。若只有通用召回，不展示具体偏好理由。

## 缓存与刷新

新增推荐快照：

```kotlin
data class DailyRecommendationSnapshot(
    val date: String,
    val profileSignature: String,
    val generatedAt: Long,
    val selectedSheets: List<RecommendedSheet>,
    val sourceStats: RecommendationSourceStats,
)
```

持久化建议：

- 快照可存 Room 或 RuntimeSnapshot，最终实现按数据规模选择。
- 第一版从最近 7 份每日 Snapshot 汇总曝光键，避免当天刷新反复推荐同一歌单。
- 不使用 DataStore 保存大列表。

刷新策略：

- 默认每天生成一次。
- 用户手动刷新时重新召回，但保留当天曝光降权。
- 画像 signature 变化明显时允许提前刷新。
- 插件失败时回退到上一次成功快照。
- 没有快照时展示可恢复空态。

冷启动性能：

- 推荐生成不阻塞 App 冷启动。
- 首页只读取轻量快照；没有快照时显示入口和加载态。
- 真正召回在用户进入推荐页或后台懒任务中执行。

## UI 设计

### 首页入口

首页在现有四宫格后新增独立小型“今日推荐”卡片。第一版不做营销大 hero，也不改动 RN 对齐的四宫格。

展示：

- 标题：`今日推荐`
- 第一版副文案：`根据最近听歌偏好，每天为你整理`。
- 首页只提供轻入口，不启动插件召回；完整推荐结果在进入页面后生成。

### 今日推荐歌单页

普通 AppBar 页面，使用 `MusicFreeScreenScaffold(title = "今日推荐")`。

内容：

- 推荐列表。
- 每行/卡片展示封面、标题、来源插件、推荐理由。
- 下拉或按钮刷新。
- 空态、错误态、低置信度提示。

交互：

- 点击歌单进入现有插件歌单详情链路。
- 推荐页不直接播放，不直接拉完整歌单歌曲列表。
- 用户刷新时显示局部 loading，不清空旧结果直到新结果成功。

## 日志

新增结构化日志：

- `recommend_profile_built`
- `recommend_candidate_fetch_start`
- `recommend_candidate_fetch_success`
- `recommend_candidate_fetch_failed`
- `recommend_rank_finished`
- `recommend_snapshot_saved`
- `recommend_snapshot_restore_failed`
- `recommend_sheet_open`
- `recommend_refresh_failed`

关键字段：

- `profileSignature`
- `windowDays`
- `topArtistCount`
- `genreCoverage`
- `languageCoverage`
- `candidateCount`
- `selectedCount`
- `durationMs`
- `source`
- `platform`
- `query`
- `tagId`
- `result`
- `reason`

日志不得记录完整用户画像大 payload；只记录数量、覆盖率、hash 或少量非敏感摘要。

## 错误处理

- 插件未加载：触发现有插件加载流程，失败后跳过该插件。
- 插件不支持推荐歌单：跳过 tag 召回，尝试 sheet 搜索。
- 插件不支持 sheet 搜索：跳过搜索召回。
- 单个插件超时：记录失败，其他插件继续。
- 全部失败且有旧快照：展示旧快照并提示可能不是最新。
- 全部失败且无旧快照：显示空态和刷新入口。
- 画像样本不足：走冷启动召回。

## Runtime State 分类

- 推荐页当前 loading、toast、刷新中状态：ViewModel local。
- 当日推荐结果：现有 SnapshotStore，namespace 为 `today_recommendation`。
- 推荐曝光记录：从最近 7 份 Snapshot 中派生，不单独落表。
- 偏好画像：从 `listen_event` 派生，可缓存轻量 snapshot，但源事实是 Room 事件。
- 插件实例、QuickJS runtime、Coroutine job 不进入持久化。

## 数据迁移

第一版不新增 Room 表，因而不提升数据库版本。现有 `runtime_snapshots` 同时承载每日结果和短期曝光键。

后续若实现长期负反馈或精确曝光分析并新增 Room 表，必须：

- 提升 `AppDatabase.version`。
- 新增对应 `Migration(N, N+1)`。
- 增加 migration instrumentation test。

候选表：

- `daily_recommendation_snapshot`
- `recommendation_exposure`

只有当短期 Snapshot 无法满足长期分析需求时，才引入独立表；该后续需求不阻塞第一版。

## 测试计划

单元测试：

1. `PreferenceProfileBuilderTest`
   - 歌手权重按播放次数、时长、完成率和最近播放排序。
   - 曲风/语种覆盖率不足时对应维度低置信度。
   - 少量听歌记录进入低置信度。
2. `RecommendationQueryPlannerTest`
   - 歌手画像生成 sheet search query。
   - 风格/语种画像生成 tag matcher。
   - 低置信度生成冷启动 query。
3. `PluginCandidateFetcherTest`
   - 支持 `getRecommendSheetsByTag` 的插件走 tag/default tag。
   - 不支持推荐歌单但支持 `sheet` 搜索的插件走搜索召回。
   - 单插件失败不影响其他插件。
4. `PlaylistRankerTest`
   - 命中歌手/风格/语种加分。
   - 重复候选合并理由。
   - 近期曝光降权。
   - 插件结果数量上限生效。
5. `DailyRecommendationRepositoryTest`
   - 快照命中、过期、profile signature 变化、失败回退。
6. `TodayRecommendationViewModelTest`
   - 首次加载、刷新、错误态、旧快照展示、点击歌单。

若修改 Compose UI：

- 增加推荐页基础 compose 测试。
- 用户可见点击入口使用 `loggedClick` / `LoggedIconButton`。

验证命令按改动模块选择，默认至少：

```bash
./gradlew :data:testDebugUnitTest --no-daemon
./gradlew :feature:home:testDebugUnitTest --no-daemon
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

## 运行态验收

1. 有足够听歌历史时，进入今日推荐页能生成推荐歌单。
2. 推荐理由与画像一致，不编造不存在的信息。
3. 清空或低样本历史时，进入冷启动推荐。
4. 禁用推荐歌单能力插件后，可通过 `sheet` 搜索召回。
5. 插件请求失败时，其他插件继续；旧快照可回退。
6. 点击推荐歌单能进入现有插件歌单详情。
7. App 冷启动首屏不被推荐生成阻塞。
8. 反馈日志能看到画像构建、候选召回、排序、快照保存和打开推荐歌单的关键事件。

## 后续可扩展

- 增加“不感兴趣”反馈，进入推荐曝光/负反馈表。
- 对高分候选少量调用 `getMusicSheetInfo(page = 1)` 做内容精排。
- 增加“私人漫游”连续播放模式。
- 扩展 `ListenDimExtractor` 的曲风和语种映射。
- 提供“为什么推荐”详情。
