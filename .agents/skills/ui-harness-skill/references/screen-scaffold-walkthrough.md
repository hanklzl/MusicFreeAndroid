# Screen Scaffold Walkthrough

普通页面默认结构：

```kotlin
@Composable
fun MyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicFreeScreenScaffold(
        title = "页面标题",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MusicFreeTheme.colors.pageBackground),
        ) {
            // 内容
        }
    }
}
```

要点：

- `innerPadding` 已包含状态栏与 AppBar 高度；不要额外加顶部 padding。
- 背景色用 `MusicFreeTheme.colors.pageBackground`；AppBar 色由 scaffold 内部处理。
- 如需自定义 actions，使用 `actions: @Composable RowScope.() -> Unit` 参数。

特殊 chrome（`HomeRoute`、`SearchRoute`、`PlayerRoute`、`LocalRoute`）见 rules.md 对应小节，必须在 rules 文件登记原因 + 状态栏策略。
