# 迭代 9 差异分析报告

## 本轮触发问题（来自真实端上与集成测试）
- 用户确认可允许明文流量后，`KW` 仍出现网络策略相关失败：
  - `CLEARTEXT communication to search.kuwo.cn not permitted`
- `WY` 搜索报错：
  - `JS async error: cannot read property 'songs' of undefined`
- `KG` 搜索报错：
  - `JS async error: cannot read property 'lists' of undefined`

## 根因定位
1. 网络策略层
- `app` 未显式允许 cleartext，`http://search.kuwo.cn` 被系统拦截。

2. Axios 兼容层（POST 语义不完整）
- `AxiosShim` 之前固定 `POST` 为 `application/json`，与真实插件常见的 `x-www-form-urlencoded` 不一致。
- `POST` 时未拼接 `config.params`，与 axios 行为存在偏差。
- 导致 `WY` 的 `https://music.163.com/weapi/search/get` 返回体异常（200 但 body 空），插件在访问 `result.songs` 时崩溃。

3. 响应解码层
- `KG` 请求中显式设置 `Accept-Encoding`，返回 gzip 压缩体；
- `AxiosShim` 直接按字符串读取，未做 gzip/deflate 解码，导致 JSON 解析失败后进入 `lists undefined`。

4. 测试有效性缺口
- `LoadedPlugin.search()` 内部会吞异常并返回空列表；
- 仅断言“不抛异常”会出现假通过，无法覆盖真实失败。

## 本轮目标
- 允许 cleartext 流量并修复 `KW` 网络拦截。
- 将 `AxiosShim` 对齐真实插件依赖的 POST/form/params 与压缩解码语义。
- 用 connected instrumentation 验证 `KW/WY/KG` 真实插件搜索链路，避免“假通过”。
