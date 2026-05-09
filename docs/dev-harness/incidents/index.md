# Dev Harness — Incidents Index

> 文档状态：当前规范（Dev Harness — Incidents 索引）
> 适用范围：跨域 incident ID 唯一性、状态汇总、guard 类型反查
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-09

## 编号规则

- 格式：`INC-YYYY-NNNN`，年 + 4 位序号。
- 递增不回收；废弃改 `status`，不复用 ID。
- 跨域全局唯一。

## status 取值

- `active`：当前生效。
- `superseded by INC-XXXX`：被另一条更准确的 incident 取代。
- `stale`：根因已不存在 / 不可复现，但保留作为历史记录。

## v1 索引

| ID | area | 标题 | rule | guard |
|---|---|---|---|---|
| INC-2026-0001 | test | runBlocking + Flow.first 死锁 | [test/rules.md#rule-runtest-mandatory](../test/rules.md#rule-runtest-mandatory) | contract-test |
| INC-2026-0002 | test | PlayerController.connect 主线程 runBlocking 死锁 | [test/rules.md#rule-no-runblocking-mainthread-in-instrumentation](../test/rules.md#rule-no-runblocking-mainthread-in-instrumentation) | contract-test |
| INC-2026-0003 | test | mergeExtDexDebugAndroidTest D8 OOM | [test/rules.md#rule-gradle-jvmargs-baseline](../test/rules.md#rule-gradle-jvmargs-baseline) | grep |
| INC-2026-0004 | test | DataStore multiple active 同文件 | [test/rules.md#rule-datastore-per-instance-isolation](../test/rules.md#rule-datastore-per-instance-isolation) | contract-test |
| INC-2026-0005 | test | feature module 缺 androidTest runner | [test/rules.md#rule-feature-androidtest-baseline](../test/rules.md#rule-feature-androidtest-baseline) | contract-test |
| INC-2026-0006 | ui | 顶部导航动画 250ms 偏离 RN 100ms | [ui/rules.md#rule-nav-animation-100ms](../ui/rules.md#rule-nav-animation-100ms) | contract-test |
| INC-2026-0007 | ui | 散落的 TopAppBarDefaults.topAppBarColors 手写 | [ui/rules.md#rule-no-raw-material3-topappbar](../ui/rules.md#rule-no-raw-material3-topappbar) | grep |
| INC-2026-0008 | ui | MainActivity 隐式补顶部 inset 白名单 | [ui/rules.md#rule-mainactivity-no-implicit-top-inset](../ui/rules.md#rule-mainactivity-no-implicit-top-inset) | grep + manual |
| INC-2026-0009 | plugin | QuickJS 跨线程访问 runtime 崩溃 | [plugin/rules.md#rule-quickjs-single-thread](../plugin/rules.md#rule-quickjs-single-thread) | manual |
| INC-2026-0010 | plugin | 集成测试默认依赖 kstore.vip 真网络 | [plugin/rules.md#rule-network-test-gated](../plugin/rules.md#rule-network-test-gated) | contract-test |
| INC-2026-0011 | player | 全屏播放器内容贴到状态栏后方 | [player/rules.md#rule-immersive-content-respects-statusbar](../player/rules.md#rule-immersive-content-respects-statusbar) | manual |
| INC-2026-0012 | player | 歌词自动跟随重复触发 / seek overlay 错位 | [player/rules.md#rule-lyric-follow-debounce](../player/rules.md#rule-lyric-follow-debounce) | contract-test |
