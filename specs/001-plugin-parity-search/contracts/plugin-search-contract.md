# Contract: Plugin Song Search Parity

## Scope

定义“通过插件搜索歌曲”对齐契约，覆盖插件可选集合、搜索执行、分页与错误恢复。

## Actors

- User: 选择插件并输入关键词搜索
- System: 基于选中插件执行搜索并维护分页状态

## Inputs

- `plugin_platform`: 选中插件标识
- `query`: 搜索关键词（非空）
- `page`: 页码（从 1 开始，默认 1）

## Preconditions

- 至少存在一个可搜索插件
- `plugin_platform` 必须属于当前可搜索插件集合

## Searchable Plugin Set Rules

- 仅包含已安装且具备歌曲搜索能力的插件
- 不满足条件的插件不得展示在搜索插件选择集合
- 当已安装插件存在但无可搜索插件时，系统必须显示“当前已安装插件暂无可搜索项”
- 当未安装任何插件时，系统必须显示“请先在设置中安装插件”
- 搜索插件选择状态以 `platform` 为稳定键，插件更新后只要同一 `platform` 仍可搜索就保持当前选择

## Execution Guarantees

- 首次搜索：
  - 返回 `success`、`empty` 或 `error` 三态之一
- 分页搜索：
  - 保持同一关键词与同一插件上下文
  - 追加新页结果，不覆盖已成功结果
- 正确更新 `page` 与 `is_end`
- 若用户之前选中的插件在刷新后仍可搜索，则应保持当前选择
- 若用户之前选中的插件不再可搜索，则应自动切换到第一个可搜索插件或清空选择
- `loadMore` 失败时必须保留已加载结果，不得清空或降级为错误态

## Error Contract

- `NO_SEARCHABLE_PLUGIN`: 无可搜索插件
- `PLUGIN_NOT_AVAILABLE`: 选中插件不可用
- `SEARCH_EXECUTION_FAILED`: 搜索执行失败
- `LOAD_MORE_FAILED`: 分页加载失败（应保留已有结果）

## Post-Update Guarantee

- 插件完成更新后，后续搜索默认使用最新版本能力，不要求重新安装。

## Evidence Contract

每次搜索执行需至少记录：

- 插件标识、关键词、页码
- 结果条数、是否结束
- 执行状态与错误信息（如有）
- 时间戳

## Acceptance Mapping

- FR-009, FR-010, FR-011, FR-012
