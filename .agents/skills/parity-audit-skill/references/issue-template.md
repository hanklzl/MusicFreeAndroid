<!-- parity-fingerprint: {{fingerprint}} -->
<!-- parity-run-id: {{run_id}} -->
<!-- parity-scenario: {{scenario_id}} -->

## 现象
{{summary}}

## 对比截图
| RN（参考实现） | Android（当前） |
|---|---|
| ![rn]({{rn_screenshot_url}}) | ![android]({{android_screenshot_url}}) |

> waypoint: `{{waypoint}}` · SSIM = `{{ssim}}`

## 复现步骤
{{repro_steps}}

## 期望（对齐 RN 行为）
{{expected}}

## 实际（Android 当前行为）
{{actual}}

## Event Diff 关键片段
```json
{{event_diff_snippet}}
```

## 修复指向
{{fix_hints}}

## 元数据
- scenario: `{{scenario_id}}` (priority=`{{priority}}`)
- run: `{{run_id}}` · severity: `{{severity}}` · kind: `{{kind}}`
- Android commit: `{{android_sha}}` · RN commit: `{{rn_sha}}` · 设备: `{{device_model}}` API {{api_level}}
{{#rn_also_crashed}}- ⚠ rn_also_crashed=true（本轮 RN baseline 同样崩溃，仅作为元数据上下文，不影响 Android 侧 Issue 的处理）{{/rn_also_crashed}}
