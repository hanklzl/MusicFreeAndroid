# 迭代 9 实现记录：明文流量 + Axios 语义补齐 + 集成测试收敛

## 改动范围
- `app/src/main/AndroidManifest.xml`
- `plugin/src/androidTest/AndroidManifest.xml`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt`
- `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`

## 关键实现
1. 允许明文流量（解决 `KW` cleartext 拦截）
- 在 `app` 的 `<application>` 增加：
  - `android:usesCleartextTraffic="true"`

2. 补齐 plugin instrumentation 网络权限
- 新增 `plugin/src/androidTest/AndroidManifest.xml`：
  - `<uses-permission android:name="android.permission.INTERNET" />`
  - `<application android:usesCleartextTraffic="true" />`
- 解决 `connectedDebugAndroidTest` 中 URL 安装插件时 `missing INTERNET permission`。

3. `AxiosShim` 兼容增强（重点）
- `POST` 对齐 axios 常用语义：
  - 支持 `config.params` 拼接到 URL（不再仅 GET 支持 params）。
  - 按 header/body 推断 `Content-Type`：
    - 优先使用 `headers["Content-Type"]`（忽略大小写）
    - `String` body 默认 `application/x-www-form-urlencoded`
    - `JSObject` body 默认 `application/json`
  - 当 `Content-Type` 为 `x-www-form-urlencoded` 且 `data` 为对象时，转 form 编码。
- 响应体解码：
  - 新增 `gzip/deflate` 解码分支，避免压缩响应被当作乱码字符串。
  - 解决 `KG` 搜索链路 `lists undefined` 的根因。

4. 集成测试强化（避免假通过）
- 保留本地 runtime shim 用例。
- 真实插件用例升级为：
  - `KW/WY` 必须返回非空搜索结果（`in the end`）。
  - `KG` 至少验证搜索调用可稳定完成（上游接口可能返回 `total=0`）。

## 结果
- `KW`: cleartext 问题消失，搜索返回真实数据。
- `WY`: `songs undefined` 消失，`weapi/search/get` 返回有效 JSON。
- `KG`: 不再出现 `lists undefined` 运行时异常（接口结果受上游返回影响）。
