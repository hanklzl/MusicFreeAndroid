# Dev Harness — Incidents Index

> 文档状态：当前规范（Dev Harness — Incidents 索引）
> 适用范围：跨域 incident ID 唯一性、状态汇总、guard 类型反查
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-12

## 编号规则

- 格式：`INC-YYYY-NNNN`，年 + 4 位序号。
- 递增不回收；废弃改 `status`，不复用 ID。
- 跨域全局唯一。

## status 取值

- `active`：当前生效。
- `superseded by INC-XXXX`：被另一条更准确的 incident 取代。
- `stale`：根因已不存在 / 不可复现，但保留作为历史记录。

## 索引

| ID | area | 标题 | rule | guard |
|---|---|---|---|---|
| INC-2026-0001 | test | runBlocking + Flow.first { predicate } 死锁 | [test/rules.md#rule-runtest-mandatory](../test/rules.md#rule-runtest-mandatory) | contract-test |
| INC-2026-0002 | test | PlayerController.connect 主线程 runBlocking 死锁 | [test/rules.md#rule-no-runblocking-mainthread-in-instrumentation](../test/rules.md#rule-no-runblocking-mainthread-in-instrumentation) | contract-test |
| INC-2026-0003 | test | mergeExtDexDebugAndroidTest D8 OOM | [test/rules.md#rule-gradle-jvmargs-baseline](../test/rules.md#rule-gradle-jvmargs-baseline) | grep |
| INC-2026-0004 | test | DataStore multiple active 同文件 | [test/rules.md#rule-datastore-per-instance-isolation](../test/rules.md#rule-datastore-per-instance-isolation) | contract-test |
| INC-2026-0005 | test | feature 模块缺 androidTest runner 基线 | [test/rules.md#rule-feature-androidtest-baseline](../test/rules.md#rule-feature-androidtest-baseline) | contract-test |
| INC-2026-0006 | ui | 顶部导航动画误按 RN JS 100ms 建守门 | [ui/rules.md#rule-nav-animation-rn-android](../ui/rules.md#rule-nav-animation-rn-android) | contract-test |
| INC-2026-0007 | ui | 散落的 TopAppBarDefaults.topAppBarColors 手写 | [ui/rules.md#rule-no-raw-material3-topappbar](../ui/rules.md#rule-no-raw-material3-topappbar) | grep |
| INC-2026-0008 | ui | MainActivity 隐式补顶部 inset 白名单 | [ui/rules.md#rule-mainactivity-no-implicit-top-inset](../ui/rules.md#rule-mainactivity-no-implicit-top-inset) | grep + manual |
| INC-2026-0009 | plugin | QuickJS 跨线程访问 runtime 崩溃 | [plugin/rules.md#rule-quickjs-single-thread](../plugin/rules.md#rule-quickjs-single-thread) | manual |
| INC-2026-0010 | plugin | 集成测试默认依赖 kstore.vip 真网络 | [plugin/rules.md#rule-network-test-gated](../plugin/rules.md#rule-network-test-gated) | contract-test |
| INC-2026-0011 | player | 全屏播放器内容贴到状态栏后方 | [player/rules.md#rule-immersive-content-respects-statusbar](../player/rules.md#rule-immersive-content-respects-statusbar) | manual |
| INC-2026-0012 | player | 歌词自动跟随重复触发 / seek overlay 错位 | [player/rules.md#rule-lyric-follow-debounce](../player/rules.md#rule-lyric-follow-debounce) | contract-test |
| INC-2026-0013 | test | ViewModel 异步加载 stale 结果未丢弃 | [test/rules.md#rule-async-load-generation](../test/rules.md#rule-async-load-generation) | manual |
| INC-2026-0014 | plugin | userVariables 写入并发竞态 | [plugin/rules.md#rule-user-variable-serialization](../plugin/rules.md#rule-user-variable-serialization) | manual |
| INC-2026-0015 | ui | Drawer / 浮层未让 status bar | [ui/rules.md#rule-overlay-respects-statusbar](../ui/rules.md#rule-overlay-respects-statusbar) | manual |
| INC-2026-0016 | test | 测试 fixture 没跟生产 VM 构造器更新 | [test/rules.md#rule-test-fixture-must-track-vm-ctor](../test/rules.md#rule-test-fixture-must-track-vm-ctor) | ci-step |
| INC-2026-0017 | player | 纯秒小数 LRC 时间戳 [s.ff] 未识别 | [player/rules.md#rule-lyric-parser-supports-second-only-timestamp](../player/rules.md#rule-lyric-parser-supports-second-only-timestamp) | contract-test |
| INC-2026-0018 | plugin | 插件加载失败被静默吞掉，UI 无法定位 | [plugin/rules.md#rule-plugin-failure-must-surface](../plugin/rules.md#rule-plugin-failure-must-surface) | manual |
| INC-2026-0019 | network | 新建 OkHttpClient 绕过 @BaseOkHttp 派生 | [network/rules.md#rule-okhttp-derive-from-base](../network/rules.md#rule-okhttp-derive-from-base) | grep |
| INC-2026-0020 | network | Media3 使用 DefaultHttpDataSource 绕过 base client | [network/rules.md#rule-media3-okhttp-data-source](../network/rules.md#rule-media3-okhttp-data-source) | grep |
| INC-2026-0021 | network | Coil ImageLoader 走默认 fetcher 绕过 base client | [network/rules.md#rule-coil-uses-base-okhttp](../network/rules.md#rule-coil-uses-base-okhttp) | grep |
| INC-2026-0022 | player | 通知播放空 session 自递归 StackOverflowError | [player/rules.md#rule-notification-play-no-recursion](../player/rules.md#rule-notification-play-no-recursion) | contract-test |
