# 搜索状态：从进程级 RuntimeStore 降级为页面生命周期

> 文档状态：当前规范补充（设计决策记录）
> 适用范围：`:feature:search` 的 `SearchSessionStore` / `SearchViewModel` 状态作用域
> 关联规则：[runtime/rules.md#rule-runtime-state-classification](../../dev-harness/runtime/rules.md#rule-runtime-state-classification)、[#rule-runtime-store-boundary](../../dev-harness/runtime/rules.md#rule-runtime-store-boundary)
> 决策日期：2026-06-05

## 背景 / 问题

线上反馈日志（v1.2.13）暴露搜索页两个缺陷：

1. 退出搜索页再进入，搜索框残留上一次的搜索词，未清空。
2. 再次触发搜索时一直没有结果。

根因：`SearchSessionStore` 此前是进程级 `@Singleton`（同时以 `@IntoSet` 注册进 RuntimeStore 集合），随 App 进程存活，离开页面不复位：

- `query` / `results` 跨导航残留 → 搜索框不清空。
- `SearchViewModel.init` 每次都调 `SearchSessionStore.restore()`，而 `restore()` 用整体赋值新建 `SearchSessionState`，把 collector 刚填好的 live `searchablePlugins` 抹成空集；插件已加载不会再次 emit，`ensureMediaSearched()` 因 `searchablePluginsFor()` 为空把搜索挂为 `pendingSearch` 后再也不触发 → 卡在 SEARCHING 无结果。

## 决策

把搜索会话状态降级为**页面生命周期**：

- `SearchSessionStore` 去掉 `@Singleton`，改为每个 `SearchViewModel` 一个实例（经 `@Inject` 构造注入）。
- 从 `SearchRuntimeModule` 移除 `@Binds @IntoSet @Singleton` 的 RuntimeStore 绑定——搜索不再属于进程级冷启动恢复集合。
- `SearchViewModel.init` 不再调用 `restore()`：新进页面即空状态。
- 移除搜索 / loadMore 成功路径上的 `persist()` 调用：不再落盘搜索快照。
- `restore()` / `persist()` 方法本身保留（满足 `RuntimeStore` 接口与既有单测），但不再接入 App 启动或搜索热路径。

## 取舍

- **保留**：配置变更（旋转、深浅色切换）经 ViewModel 保活，搜索状态仍在。
- **放弃**：进程冷启动后的搜索结果恢复（此前由 SnapshotStore 提供）。与 RN MusicFree 行为一致——重启后搜索为空，可接受。
- **修复**：离开搜索页 → ViewModel 清除 → 状态复位，搜索框清空、再次搜索正常。

## 验收

- `:feature:search` 单测全绿（含搜索逻辑、ViewModel 适配）。
- 新增回归测试：重新进入搜索（新建 ViewModel + 新建 store）起始为空状态，且新搜索能返回结果（覆盖原“再次搜索无结果”）。
- 运行态：冷启动→搜索→返回→再进入，搜索框为空；输入新词搜索有结果。
