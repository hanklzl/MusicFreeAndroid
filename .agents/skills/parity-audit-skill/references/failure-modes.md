# Failure Modes

| 现象 | 状态码 | 处置 |
|---|---|---|
| `adb devices` 无设备 | exit 2 | audit.sh 立即终止，REPORT 写"no device" |
| Maestro / gh / python deps 缺失 | exit 2 | 同上 |
| `cd ../MusicFree && yarn` 失败 | exit 3 | `preflight_failed=rn_build`，本轮终止 |
| `./gradlew :app:assembleDebug` 失败 | exit 4 | `preflight_failed=android_build`，本轮终止 |
| `adb install -r` 失败 | exit 5 | `preflight_failed=install`，本轮终止 |
| install-plugins flow 失败 | exit 6 | `plugin_bootstrap_failed`，本轮终止 |
| 单 scenario Maestro flow timeout / 找不到元素 | scenario 标 `blocked_runtime`，继续下一个 | 不让整轮跑挂 |
| ADB 中途掉线 | abort 整轮 | exit 7 |
| RN 进程崩 | scenario 状态 `rn_baseline_unstable` | 不建 Issue，REPORT 高亮 |
| Android 进程崩 / ANR | scenario 状态 `diff_found severity=critical kind=crash` | `mode=audit` 时创建 Issue，`mode=dry-run` 时仅落 issue.md 草稿 |

`mode=dry-run` 时所有 Issue 触发条件等价于"生成 issue.md 草稿"。`mode=audit` 时按 spec §5.1 直接 `gh issue create`，并走指纹查重。
