# Data Model: 插件能力对齐（添加-更新-搜索）

## 1. CapabilityGapItem

- Purpose: 表示一个“原版 vs Compose”能力差异条目，是对齐矩阵的最小单元。
- Fields:
  - `capability_id` (string): 稳定能力标识，例如 `plugin.update.single`
  - `capability_name` (string): 能力名称
  - `scope_stage` (enum): `add` | `update` | `search`
  - `original_status` (enum): `supported` | `partial` | `unsupported`
  - `compose_status` (enum): `supported` | `partial` | `unsupported`
  - `gap_status` (enum): `aligned` | `partially_aligned` | `not_aligned`
  - `target_definition` (string): 对齐完成标准
  - `priority` (enum): `P1` | `P2` | `P3`
  - `evidence_refs` (list<string>): 证据链接（测试、截图、日志）
- Validation:
  - `capability_id` 全局唯一
  - `gap_status` 必须由 `original_status` 与 `compose_status` 推导并可人工复核
  - `P1` 条目必须至少绑定 1 条自动化证据和 1 条人工验收证据

## 2. PluginDescriptor

- Purpose: 表示插件在系统中的可管理身份及可搜索能力状态。
- Fields:
  - `plugin_platform` (string): 插件平台标识
  - `display_name` (string)
  - `version` (string | null)
  - `source_url` (string | null)
  - `install_source_type` (enum): `local_file` | `plugin_url` | `subscription`
  - `searchable` (boolean): 是否支持歌曲搜索
  - `enabled` (boolean)
  - `last_updated_at` (datetime | null)
- Validation:
  - `plugin_platform` 唯一
  - 当执行“更新插件”时，`source_url` 不能为空
  - `searchable=false` 的插件不得进入搜索选择集合

## 3. PluginOperationResult

- Purpose: 记录一次添加或更新操作的结果与失败信息。
- Fields:
  - `operation_id` (string)
  - `operation_type` (enum): `add` | `update_single` | `update_all` | `update_subscription`
  - `target_plugins` (list<string>)
  - `success_count` (int)
  - `failure_count` (int)
  - `failures` (list<OperationFailure>)
  - `started_at` (datetime)
  - `finished_at` (datetime)
- Validation:
  - `success_count + failure_count == target_plugins.size`（批量场景）
  - `failure_count > 0` 时必须提供失败原因

## 4. SearchExecutionRecord

- Purpose: 记录插件搜索行为，支撑分页和对齐验收。
- Fields:
  - `search_id` (string)
  - `plugin_platform` (string)
  - `query` (string)
  - `page` (int)
  - `result_count` (int)
  - `is_end` (boolean)
  - `status` (enum): `success` | `empty` | `error`
  - `error_message` (string | null)
  - `executed_at` (datetime)
- Validation:
  - `query` 不能为空
  - `page >= 1`
  - `status=error` 时 `error_message` 必填

## 5. ParityTestCase

- Purpose: 定义能力对齐用例与发布门禁映射。
- Fields:
  - `case_id` (string)
  - `covers_capability_id` (string)
  - `test_layer` (enum): `unit` | `integration` | `e2e_manual`
  - `preconditions` (list<string>)
  - `steps` (list<string>)
  - `expected_outcome` (list<string>)
  - `result` (enum): `pass` | `fail` | `blocked`
  - `evidence_refs` (list<string>)
- Validation:
  - 每个 P1 `CapabilityGapItem` 至少映射 2 个不同层级用例
  - 发布前所有 P1 用例结果必须为 `pass`

## Relationships

- `CapabilityGapItem` 1:N `ParityTestCase`
- `PluginDescriptor` 1:N `PluginOperationResult`
- `PluginDescriptor` 1:N `SearchExecutionRecord`
- `PluginOperationResult` 与 `SearchExecutionRecord` 共同为 `CapabilityGapItem` 提供证据

## State Transitions

### Plugin Lifecycle

- `NotInstalled -> Installed`
- `Installed -> UpdateAvailable`
- `UpdateAvailable -> Updated`
- `Updated -> Installed`
- 任意状态在失败时进入 `OperationFailed`（不破坏当前可用版本）

### Search Lifecycle

- `Idle -> Searching`
- `Searching -> SuccessPage`
- `SuccessPage -> LoadingMore`
- `LoadingMore -> SuccessPage` 或 `Completed`
- 任意执行异常进入 `SearchError`，允许回到 `Searching` 重试
