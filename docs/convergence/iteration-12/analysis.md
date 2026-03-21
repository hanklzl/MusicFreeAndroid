# 迭代 12 分析（默认订阅真实验证）

## 背景
- 用户反馈早期出现“默认订阅导入失败”和“导入成功但搜索/播放异常”。
- 迭代 9~11 已修复网络兼容、字段透传和播放兜底逻辑。

## 本轮关注点
- 将“默认订阅”链路纳入真实 connected instrumentation，避免仅靠单插件 URL 验证。

## 目标
- 覆盖真实路径：
  - `installFromSubscriptionUrl(DEFAULT_URL)`；
  - 从导入结果中定位 `元力WY`；
  - `search("in the end")`；
  - `getMediaSource(first)`。
