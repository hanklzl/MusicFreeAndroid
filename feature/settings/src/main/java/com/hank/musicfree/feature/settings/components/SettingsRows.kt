package com.hank.musicfree.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx

@Composable
fun SettingSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .withOptionalTestTag(testTag),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(
            containerColor = MusicFreeTheme.colors.card,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rpx(16)),
        ) {
            Text(
                text = title,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
            )
            content()
        }
    }
}

@Composable
fun SettingValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    SettingBaseRow(
        title = title,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = onClick,
    ) {
        Text(
            text = value,
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.textSecondary,
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingBaseRow(
        title = title,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onCheckedChange(it) },
        )
    }
}

@Composable
fun SettingActionRow(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    trailingText: String = if (enabled) "" else "待接入",
    onClick: () -> Unit,
) {
    SettingBaseRow(
        title = title,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = onClick,
    ) {
        if (trailingText.isNotEmpty()) {
            Text(
                text = trailingText,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingBaseRow(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(96))
            .settingsRowClick(enabled = enabled, onClick = onClick)
            .withOptionalTestTag(testTag)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = rpx(24)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = FontSizes.content,
            color = MusicFreeTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

private fun Modifier.settingsRowClick(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    return if (enabled) {
        clickable(onClick = onClick)
    } else {
        semantics {
            disabled()
            onClick {
                true
            }
        }
    }
}

private fun Modifier.withOptionalTestTag(testTag: String?): Modifier {
    return if (testTag == null) {
        this
    } else {
        testTag(testTag)
    }
}
