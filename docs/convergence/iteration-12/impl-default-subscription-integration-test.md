# 迭代 12 实现说明（默认订阅集成测试）

## 变更文件
- `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`

## 新增用例
- `defaultSubscription_installAndWyPlaybackChain_succeeds`

## 覆盖内容
1. 真实订阅安装
- 调用默认订阅地址：`https://13413.kstore.vip/yuanli/yuanli.json`
- 断言 `successfulInstalls > 0`。

2. 真实插件定位
- 从 `pluginManager.plugins.value` 中定位 `WY/网易` 平台插件。

3. 真实搜索与播放地址解析
- 搜索 `in the end` 返回非空。
- `getMediaSource` 返回非空且 URL 非空。

## 价值
- 将“默认订阅导入 -> 搜索 -> 播放地址解析”纳入自动化验证，减少回归风险。
