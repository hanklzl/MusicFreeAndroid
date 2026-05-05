# Logan Logging System Design

> 文档状态：当前规范
> 适用范围：Android 原生日志系统、用户反馈日志包、开发阶段日志记录规范。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：[MusicFree RN 日志实现](../../../../MusicFree/src/utils/log.ts)、[MusicFree RN 基础设置](../../../../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx)、[Logan](https://github.com/Meituan-Dianping/Logan)、[Logan 技术文章](https://tech.meituan.com/2018/02/11/logan.html)
> 最后校验：2026-05-05

## 背景

当前 Android 实现只有分散的 `android.util.Log` 调用，主要集中在插件、搜索、歌单导入和少量播放器错误路径。日志只进入 logcat，普通用户反馈时无法稳定提供问题现场，也缺少统一的日志分类、字段结构、保留策略和导出机制。

RN 原版已有基础日志能力：`../MusicFree/src/utils/log.ts` 基于 `react-native-logs` 写入错误日志和 trace 日志；`../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx` 提供记录错误日志、记录详细日志、查看错误日志和清空日志入口。Android 原生版本需要用 Kotlin + 多模块架构实现等价但更适合移动端反馈的日志系统。

本设计选择美团 Logan 作为底层持久化日志库。Logan 的 Android 方案使用 native 层、压缩、加密和 mmap 缓存，适合低开销、高可靠的移动端日志采集与反馈包导出。

## 目标

1. 引入美团 Logan 作为 Android 持久化日志底层。
2. 提供应用内统一日志门面，业务模块不直接依赖 Logan。
3. 默认记录错误日志、关键路径 trace 和详细诊断日志。
4. 为普通用户提供“生成日志包并系统分享”的反馈路径。
5. 日志包包含 Logan 原始加密日志、设备信息、版本信息、manifest 和解码说明。
6. 默认保留最近 7 天日志，并设置总量上限，避免日志无限增长。
7. 提供本地解码脚本和文档，用于解析用户反馈包。
8. 更新 `AGENTS.md`，把开发阶段日志记录规范写成长期规则。

## 非目标

1. 第一阶段不做应用内明文日志查看。
2. 第一阶段不做远程上传、后端接收或在线分析平台。
3. 第一阶段不做日志脱敏，也不做详细日志手动开关。
4. 第一阶段不把每个 Repository/ViewModel 全量埋点一次性补完。
5. 第一阶段不接入 Timber，也不允许业务代码直接新增 `android.util.Log.*`。

## 选型

采用“Logan + `MfLogger` 门面 + 原始日志包导出”方案。

备选方案“业务代码直接调用 Logan”接入更快，但会让 Logan API 扩散到 `:plugin`、`:feature:search`、`:player`、`:feature:home`、`:feature:settings` 等模块，后续替换底层或调整字段格式时成本较高。

备选方案“Logan 只做详细日志，错误日志另走普通文件”会产生两套保留、清理、导出和解码流程，削弱统一日志系统的价值。

推荐方案在模块边界和长期维护上更稳：业务代码只写结构化事件，Logan 只作为 `:logging` 模块的实现细节。

## 模块边界

新增独立 `:logging` 模块。

依赖方向：

```text
:app, :feature:*, :data, :player, :plugin -> :logging
:logging -> Logan + Android/Kotlin 基础库
```

`:logging` 不依赖 `:core`，避免形成多模块交叉依赖。日志事件中需要记录 domain 信息时，由调用方传入基础字符串、数字、布尔值或 map。

`:app` 负责在 `MusicFreeApplication.onCreate()` 初始化日志系统，并在设置页触发日志包导出与系统分享。

业务模块只依赖 `MfLogger`，不直接操作 Logan 文件、key/IV、日志包格式或 `FileProvider`。

## 对外 API

第一阶段提供以下核心 API：

```kotlin
interface MfLogger {
    fun trace(category: LogCategory, event: String, fields: Map<String, Any?> = emptyMap())
    fun detail(category: LogCategory, event: String, fields: Map<String, Any?> = emptyMap())
    fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )
    fun flush()
}
```

日志包能力由独立服务提供：

```kotlin
interface FeedbackLogExporter {
    suspend fun createPackage(): FeedbackPackage
    suspend fun clearLogs()
    suspend fun pruneLogs()
}
```

`FeedbackPackage` 包含 `Uri`、文件名、大小、日期范围和文件列表摘要。分享 intent 由 `:app` 或 `:feature:settings` 负责触发，导出实现位于 `:logging`。

## 日志分类

第一阶段分类固定为：

| category | 范围 |
|---|---|
| `app` | 启动、初始化、权限请求结果、关键生命周期 |
| `plugin` | 插件加载、安装、卸载、订阅更新、JS 方法调用、QuickJS/axios 异常 |
| `search` | 搜索开始/结束/失败、分页、fallback 播放解析 |
| `player` | 播放服务连接、音源解析、播放控制、Media3 错误、通知栏控制 |
| `playlist_import` | 选择插件、解析导入链接、入库、失败回滚 |
| `feedback` | 生成日志包、分享 intent、清理日志、淘汰日志 |

后续新增 category 必须能说明独立诊断价值；不能把临时页面名当 category 滥用。

## 字段结构

每条日志写入前统一格式化为一行 JSON 字符串：

```json
{
  "level": "trace",
  "category": "plugin",
  "event": "plugin_api_call_success",
  "timestamp": "2026-05-05T20:30:00.000+08:00",
  "sessionId": "session-uuid",
  "traceId": "optional-flow-id",
  "durationMs": 123,
  "result": "success",
  "fields": {
    "platform": "example",
    "method": "search",
    "count": 20
  }
}
```

错误日志额外包含：

- `errorClass`
- `errorMessage`
- `stackTrace`

字段值只允许基础类型、字符串列表或简单 map；复杂对象由调用方转换成诊断摘要，避免把 domain object 直接序列化进日志。

## 内容策略

日志默认全部开启：

- `error` 默认开启。
- `trace` 默认开启。
- `detail` 默认开启。

第一阶段不做详细日志开关，也暂时不做脱敏。日志可以记录完整 URL、搜索词、插件返回摘要、请求/响应片段等诊断信息。因为日志包导出的是 Logan 原始加密日志，设置页导出前仍必须弹出确认，提示用户日志可能包含搜索词、请求地址、插件返回内容和其他诊断信息。

## Logan 初始化

`MusicFreeApplication.onCreate()` 调用日志系统初始化。初始化流程：

1. 生成本次进程 `sessionId`。
2. 创建 Logan 运行目录，例如 `files/logan/`。
3. 创建导出临时目录，例如 `cache/feedback/`。
4. 配置 Logan 缓存目录、日志目录、key/IV、最大文件策略。
5. 安装 `Thread.setDefaultUncaughtExceptionHandler`，先记录未捕获异常并 flush，再交给原 handler。
6. 记录 `app_start` 事件，包含 app version、versionCode、build type、Android SDK、ABI、设备型号。
7. 执行一次日志淘汰。

初始化失败不能阻塞应用启动，但必须尽量落到 logcat，并在 `MfLogger` 后续调用中降级为 no-op。

## Key 与 IV

Debug 使用固定开发 key/IV，便于本地开发与脚本验证。

Release 使用 Gradle 从环境变量注入：

- `LOGAN_AES_KEY`
- `LOGAN_AES_IV`

Release 构建缺少 key/IV 时必须 fail fast，避免产出无法解码或使用开发密钥的正式包。普通 Debug 构建不依赖 Release 环境变量。

仓库提供本地解码脚本和说明，但不提交 Release 私钥。

## 淘汰机制

日志默认保留最近 7 天。

同时设置总量上限，第一阶段默认 50MB。淘汰顺序：

1. 删除早于 7 天的日志文件。
2. 如果剩余日志总量仍超过 50MB，按最旧文件优先删除，直到总量回到上限内。

淘汰在应用启动时执行一次，在生成日志包前再执行一次。设置页提供“清空日志”入口，清空 Logan 日志目录和导出临时目录。

## 反馈日志包

用户在设置页点击“生成日志包并分享”后：

1. 弹出确认说明，提示日志可能包含搜索词、请求地址、插件返回内容和设备信息。
2. 执行日志淘汰和 flush。
3. 生成 zip：`musicfree-feedback-YYYYMMDD-HHMMSS.zip`。
4. 通过 `FileProvider` 暴露 zip。
5. 使用 `Intent.ACTION_SEND` 调起系统分享。

zip 内容：

```text
manifest.json
README-decode.md
logan/
  <Logan 原始加密日志文件>
```

`manifest.json` 包含：

- app versionName / versionCode
- applicationId
- buildType
- Android SDK
- Android release
- device manufacturer / model
- supported ABIs
- package generatedAt
- sessionId
- log date range
- file list、大小和相对路径

`README-decode.md` 说明如何使用仓库脚本解码原始 Logan 日志，并说明 Release 日志需要对应 Release key/IV。

## 本地解码脚本

新增 `tools/logan/`：

```text
tools/logan/
  README.md
  decode-logan.sh
```

脚本输入为日志包 zip 或解压后的 Logan 原始日志目录。Debug 日志默认使用仓库固定开发 key/IV；Release 日志通过环境变量传入 key/IV。

脚本输出解码后的文本日志到 `tools/logan/out/` 或调用者指定目录。脚本必须避免把 Release key/IV 写入仓库或输出到日志。

## 设置页入口

`SettingsScreen` 增加日志反馈入口。普通 AppBar 页面继续使用 `MusicFreeScreenScaffold`，遵守 [screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)。

第一阶段入口：

- `生成日志包并分享`
- `清空日志`

不新增独立“查看日志”页面。

## 第一阶段埋点范围

### app

- `MusicFreeApplication.onCreate()`：日志初始化成功/失败、启动环境。
- `MainActivity.onCreate()`：Splash、edge-to-edge、通知权限请求结果。

### plugin

- `PluginManager`：插件目录加载、单插件加载成功/失败、安装、卸载、订阅更新、下载失败、替换文件失败。
- `LoadedPlugin`：14 个插件 API 调用的开始、成功、失败、耗时、platform、方法名、输入关键字段、结果数量。
- `JsEngine`：console、evaluate/close 异常。
- `AxiosShim`：请求、响应、失败、状态码、耗时、body 片段。
- `RequireShim`：模块注册和 asset 读取失败。

### search

- 搜索开始。
- 每个平台搜索结果。
- 分页结果。
- fallback 到 WY 的匹配与失败原因。
- 播放解析结果。

### player

- `PlayerController`：连接 `MediaController`、`playQueue`、`playItem`、`setMediaItemAndPlay`、skip、shuffle、repeat、连接失败。
- `PlaybackService`：service create/destroy、session connect、custom command、task removed。

### playlist_import

- 打开导入。
- 插件能力列表。
- 提交 URL。
- 解析结果。
- 导入目标。
- 入库结果。
- 回滚失败。

### feedback

- 清理日志。
- 生成 zip。
- 分享 intent 成功/失败。

## AGENTS.md 更新

新增“日志记录规范”章节，作为后续开发长期规则：

1. 新功能或 bugfix 涉及启动、插件、网络、播放、数据写入、导入导出、跨模块状态变化时，必须补结构化日志。
2. 业务代码使用 `MfLogger`，禁止新增直接 `android.util.Log.*` 和直接 Logan 调用。
3. 日志事件命名使用稳定小写 snake_case，例如 `plugin_install_failed`。
4. catch 后如果吞掉异常或转成用户 toast，必须记录 `error`。
5. 耗时操作必须记录 `durationMs`，可使用 start/end helper 或 scoped helper。
6. 日志字段使用稳定 key，避免把临时 UI 文案作为机器可读字段。
7. 不把 `docs/superpowers/plans/*.md` 中旧日志写法当作当前规范来源。

## 测试与验收

单元测试：

- 日志 JSON 格式化稳定。
- Throwable 转换包含 class、message、stack trace。
- Logan key/IV 配置选择：Debug 固定值，Release 缺少环境变量 fail fast。
- 日志淘汰：7 天规则和 50MB 总量规则。
- `manifest.json` 生成内容稳定。
- 日志包 zip 包含 `manifest.json`、`README-decode.md` 和 Logan 原始日志。

集成/运行态验收：

- `:app:assembleDebug` 通过。
- 启动应用后 Logan 目录产生日志。
- 插件安装、搜索、播放解析、歌单导入失败路径能写入日志。
- 设置页生成日志包并系统分享。
- Debug 日志包可用本地脚本解码。
- 清空日志后旧日志文件和导出临时文件被删除。

## 实施顺序

1. 创建 `:logging` 模块，接入 Logan，提供 `MfLogger`、初始化器和 no-op 降级。
2. 增加 key/IV Gradle 注入、Debug 默认值和 Release fail-fast。
3. 实现日志格式化、sessionId、异常 handler、flush 和淘汰。
4. 实现反馈日志包生成、manifest、README、FileProvider 和分享入口。
5. 提供 `tools/logan/` 解码脚本与说明。
6. 补第一阶段核心链路日志。
7. 更新 `AGENTS.md` 日志记录规范。
8. 完成单元测试、Debug 构建和运行态验收。

## 风险

- Logan native 依赖会增加包体和 ABI 验证成本。
- Release key/IV 注入需要 CI 和本地发布流程同步更新。
- 默认记录详细日志且暂不脱敏，会增加用户主动分享日志包时的隐私提示责任。
- 原始 Logan 日志不可直接阅读，开发者必须使用解码脚本和对应 key/IV。
- 第一阶段埋点面较广，需要控制改动顺序，避免在同一轮中混入无关重构。
