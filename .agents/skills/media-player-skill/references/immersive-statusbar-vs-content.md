# Immersive Status Bar vs Content

`PlayerScreen`：

- 背景层：`Image(...)`、`Box(Modifier.background(...))` 等 MAY 绘制到状态栏后方。
- 内容层：标题、控件、歌词卡片、按钮 MUST 用 `WindowInsets.statusBars` 显式避让。

实现要点：

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars), // 内容层避让
) {
    // ...
}
```

manual guard：harness-curator-skill 在巡检时显式列 INC-2026-0011 提醒人工核对截图。
