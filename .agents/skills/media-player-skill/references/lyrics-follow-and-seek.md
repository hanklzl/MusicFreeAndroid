# Lyrics Follow & Seek Overlay

互斥状态：

- 自动跟随仅在非手动滑动期间生效。
- seek overlay 进入条件由统一 helper 决定（不分散到多个 composable）。

PR 必跑测试：

- `MiniPlayerContentTest`
- `LyricFollow*Test`（如 `LyricAutoFollowTest`、`LyricFollowDebounceTest`）
- `LyricSeekOverlay*Test`

contract 守门（PR 3 起）：`LyricFollowDebounceContractTest`，验证关键测试类存在 + 关键断言函数被引用。
