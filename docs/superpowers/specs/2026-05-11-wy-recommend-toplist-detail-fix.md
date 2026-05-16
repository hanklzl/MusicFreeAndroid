# 网易音乐推荐歌单与榜单详情加载失败修复

> 文档状态：当前规范
> 适用范围：`wy.js` 等插件返回数值型媒体 ID 时，Android 插件桥、推荐歌单详情与榜单详情加载链路。
> 直接执行：是（作为本次修复 spec + plan）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 相关规范：[推荐歌单与榜单 RN 对齐及详情点击修复设计](./2026-05-09-recommend-toplist-rn-align-design.md)、[Dev Harness 插件规则](../../dev-harness/plugin/rules.md)
> 外部插件参考：<https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js>
> 最后校验：2026-05-11

## 背景

用户反馈网易音乐插件的推荐歌单与榜单入口中，很多条目进入详情后显示“加载歌单失败”。当前 Android 已经按推荐/榜单入口传递点击时的 `MusicSheetItemBase` seed，详情页也会直接调用插件的 `getMusicSheetInfo(seed, page = 1)` 或 `getTopListDetail(seed, page = 1)`。

外部 `wy.js` 的 `formatSheetItem()` 对歌单和榜单返回 `id: _.id || _.resourceId`，这些值来自网易接口时通常是 JavaScript number。`getMusicSheetInfo(sheet)` 再把 `sheet.id` 原样作为网易 `/api/v6/playlist/detail` 参数。

## 根因

Android `JsBridge` 当前用 `map["id"]?.toString()` 生成 domain ID。QuickJS / Kotlin 桥接把 JS number 暴露为 `Double` 时，整数 ID 会被转成带 `.0` 或科学计数法的字符串。随后 `musicSheetItemToMap()` 把该字符串覆盖回传给插件，导致 `wy.js` 请求网易详情时使用异常 ID，部分接口无法识别，最终详情加载返回 `null` 或抛错，UI 显示“加载歌单失败”。

## 目标

1. 插件返回数值型整数 ID 时，Android domain ID 必须规范化为无小数点字符串，例如 `987654321.0` -> `"987654321"`。
2. 字符串 ID 必须保持原样，包括前导零、非数字后缀或插件私有 ID。
3. 推荐歌单、榜单、专辑、歌手、音乐和评论等插件模型的 `id` 解析使用同一规则。
4. 保持现有 raw 扩展字段传递能力，不破坏插件自定义字段。

## 非目标

- 不修改 `wy.js` 插件源码。
- 不改变插件 API 方法签名。
- 不重做推荐歌单、榜单或详情页 UI。
- 不新增真实网络测试；默认测试仍不得依赖外部网易服务。

## 实施计划

1. 在 `plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt` 添加失败测试，覆盖数值型整数 ID 解析和 round trip 回传。
2. 在 `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt` 增加统一 ID 字符串规范化 helper。
3. 将 `MusicItem`、`MusicSheetItemBase`、`AlbumItemBase`、`ArtistItemBase`、`MusicComment` 的 `id` 解析切到该 helper。
4. 验证 `:plugin:testDebugUnitTest`、`:feature:home:testDebugUnitTest`、`scripts/dev-harness/check.sh` 和 `:app:assembleDebug`。

## 验收标准

- 数值型 JS integer ID 不再变成带 `.0` 或科学计数法的字符串。
- `musicSheetItemToMap()` 回传给插件的 `id` 为规范化字符串，`wy.js` 能用该 ID 请求歌单/榜单详情。
- 既有 raw 扩展字段测试继续通过。
- 无新增真网默认测试，无新增 `android.util.Log.*`。
