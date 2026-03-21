# 迭代 10 分析（播放点击报错）

## 用户反馈
- 搜索结果可以正常返回，但点击播放报错：
  - `getMediaSource failed ... cannot read property 'url' of undefined`
  - 场景集中在 `元力QQ`，并要求以 `元力WY` 作为本轮验证插件。

## 根因确认
- `LoadedPlugin.getMediaSource()` 会把 `MusicItem` 通过 `JsBridge.musicItemToMap()` 回传给插件 JS。
- 现状下 `MusicItem` 仅保留标准字段（`id/title/artist/...`），插件搜索结果中的扩展字段（如 `songmid`）在 `toMusicItem()` 后被丢弃。
- `元力QQ` 的 `getMediaSource` 实现依赖 `musicItem.songmid`。字段丢失后，请求参数异常，最终在插件 JS 内部访问响应对象 `...data.url` 时触发 `undefined` 访问异常。

## 修复目标
- 保留并透传插件扩展字段，保证 `search -> getMediaSource` 跨调用字段完整。
- 增加测试覆盖，避免后续 runtime/bridge 迭代再次回归。
