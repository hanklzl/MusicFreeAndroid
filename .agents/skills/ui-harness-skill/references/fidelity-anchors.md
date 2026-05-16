# Fidelity Anchors

`core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt` 是首页 UI fidelity 与 contract 测试断言的 anchor 表。

约束：

- 修改 anchor 名称 / 删除 anchor 必须同步：`PluginSearchPlayAnchorContractTest`、`HomeFidelityHomeStructureTest`、`HomeDrawerBehaviorTest`、`docs/home-fidelity/homepage/README.md`。
- 新增 anchor 时优先复用既有 namespace（`Home.*` / `Player.*` / `Settings.*` / `Dialog.*`）。

首页 fidelity 取证规范在 `docs/home-fidelity/`，本 skill 仅引用，不复制规则。
