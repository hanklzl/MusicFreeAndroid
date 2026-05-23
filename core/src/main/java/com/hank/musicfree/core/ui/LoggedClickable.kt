package com.hank.musicfree.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.UiLogEvents
import com.hank.musicfree.logging.UiLogEvents.Fields

/**
 * Compose [Modifier] 包装：点击时先发一条 `ui_click` 事件，再调用原 onClick。
 *
 * 用法：
 * ```
 * Modifier.loggedClick(
 *     targetId = "player.controls.play",
 *     screen = "player",
 *     fields = mapOf("itemId" to itemId),
 *     onClick = { viewModel.onPlay() },
 * )
 * ```
 *
 * `targetId` 命名约定：点分层级、纯 ASCII、稳定，例如
 * `home.tab_bar.player` / `search.result.music_row` / `settings.row.theme`。
 */
fun Modifier.loggedClick(
    targetId: String,
    screen: String,
    fields: Map<String, Any?> = emptyMap(),
    targetLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
    ) {
        logUiClick(targetId, screen, targetLabel, fields)
        onClick()
    }
}

/**
 * 直接发一条 `ui_click` 事件，不带 Modifier。用于非 Modifier 链路（例如 IconButton onClick）。
 */
fun logUiClick(
    targetId: String,
    screen: String,
    targetLabel: String? = null,
    extra: Map<String, Any?> = emptyMap(),
) {
    val base = mutableMapOf<String, Any?>(
        Fields.TARGET_ID to targetId,
        Fields.SCREEN to screen,
    )
    if (!targetLabel.isNullOrEmpty()) base[Fields.TARGET_LABEL] = targetLabel
    if (extra.isNotEmpty()) base.putAll(extra)
    MfLog.detail(LogCategory.UI, UiLogEvents.UI_CLICK, base)
}

/**
 * [IconButton] 的带日志包装，签名与 Material3 [IconButton] 对齐，多出 [targetId] / [screen]。
 */
@Composable
fun LoggedIconButton(
    targetId: String,
    screen: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    targetLabel: String? = null,
    fields: Map<String, Any?> = emptyMap(),
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = {
            logUiClick(targetId, screen, targetLabel, fields)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

// 备注：早期 v1.A 提供过 `ScreenLogEffect`，已删除——screen_enter / screen_exit 现
// 由 `AppNavHost` 的 NavController.OnDestinationChangedListener 统一发出，避免重复埋点。
// Screen 想附带 params 时直接调用 MfLog.detail(LogCategory.NAVIGATION, "screen_enter", ...) 即可。
