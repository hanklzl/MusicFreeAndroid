# 首页 UI Fidelity 黄金数据态 Manifest v2

> 文档状态：当前规范（首页专项）
> 适用范围：加入 Android-only 今日推荐轻入口后的首页 UI fidelity 基线。
> 直接执行：是（仅首页专项）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 前序基线：[2026-04-11-homepage-ui-fidelity-manifest.md](./2026-04-11-homepage-ui-fidelity-manifest.md)
> 最后校验：2026-07-11

## 版本

- Manifest ID: `home-ui-fidelity-2026-07-11-v2`
- RN 参考：继承 v1；今日推荐卡片是 Android-only 增强，不作为 RN parity 项。
- 关联 spec：[2026-07-04-listening-preference-recommendation-design.md](./2026-07-04-listening-preference-recommendation-design.md)

## 继承范围

设备、语言、Drawer、歌单黄金数据和迷你播放器状态全部继承 v1。v2 只调整首页主内容结构，并新增一个固定可见入口：

| 项目 | 固定值 |
|------|--------|
| 标题 | `今日推荐` |
| 副文案 | `根据最近听歌偏好，每天为你整理` |
| 位置 | 四宫格之后、歌单区之前 |
| 无快照行为 | 入口仍可见且可点击；首页不触发插件网络召回 |

## 首页可见片段顺序

1. `HomeNavBar`
2. `HomeOperations`
3. `TodayRecommendationHomeCard`
4. `HomeSheetsHeader`
5. `HomeSheetsList`
6. Mini player 固定悬浮在底部

## 采集与验收

- 截图和 `uiautomator dump` 必须证明今日推荐卡片位于四宫格和歌单标题之间。
- 四宫格仍保持 4 个既有入口，原“推荐歌单”入口不得被替换。
- 点击今日推荐卡片应进入 `TodayRecommendationRoute`，且不影响首页 Drawer、tab 与歌单行交互。
- 本 manifest 只定义目标基线；运行态截图和 dump 未采集前，不得声称视觉验收完成。
