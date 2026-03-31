# Contract: Plugin Lifecycle Parity

## Scope

定义插件生命周期对齐契约，覆盖添加与更新能力：

- 添加插件：本地文件、插件 URL、订阅列表
- 更新插件：单插件更新、批量更新、订阅更新

## Actors

- User: 发起插件添加/更新操作
- System: 校验输入、执行操作、返回结果、保持现有可用状态

## Inputs

### Add Plugin

- `source_type`: `local_file` | `plugin_url` | `subscription_url`
- `source_value`: 本地路径或 URL

### Update Plugin

- `operation_type`: `update_single` | `update_all` | `update_subscription`
- `target_plugin`: 单插件更新时必填
- `default_subscription_url`: `https://13413.kstore.vip/yuanli/yuanli.json`（当用户未自定义时）

## Preconditions

- 用户已进入插件管理入口
- 网络相关操作需具备可访问网络
- `update_single` 目标插件必须已安装

## Guarantees

- 成功时：
  - 新增或更新后的插件可在插件列表可见
  - 插件身份唯一，不出现重复条目
  - 批量更新返回可聚合结果（成功数量、失败数量、失败明细）
- 失败时：
  - 系统返回可读失败原因
  - 当前已可用插件状态不被破坏

## Error Contract

- `SOURCE_UNREACHABLE`: 来源不可访问
- `SOURCE_INVALID`: 来源内容无法解析为插件
- `MISSING_UPDATE_SOURCE`: 插件缺少更新来源
- `DUPLICATE_PLUGIN`: 重复安装请求（可按幂等成功处理）
- `VERSION_NOT_UPGRADABLE`: 版本不满足更新条件

## Evidence Contract

每次操作必须可产出以下证据：

- 操作类型
- 目标插件集合
- 成功/失败计数
- 失败详情（如有）
- 时间戳

## Result Shape Contract

- `operation_type`: `add` | `update_single` | `update_all` | `update_subscription`
- `target_plugins`: 目标插件集合
- `success_count`: 成功数量
- `failure_count`: 失败数量
- `failures[]`:
  - `target_plugin`
  - `source_ref`
  - `error_code`
  - `message`

## Acceptance Mapping

- FR-003, FR-004, FR-005, FR-006, FR-007, FR-008
