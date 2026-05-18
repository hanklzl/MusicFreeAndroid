package com.hank.musicfree.feature.playerui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.hank.musicfree.core.model.DesktopLyricAlignment
import kotlin.math.roundToInt

internal data class DesktopLyricOverlayState(
    val enabled: Boolean = false,
    val alignment: DesktopLyricAlignment = DesktopLyricAlignment.Center,
    val topPercent: Float = 0.08f,
    val leftPercent: Float = 0.08f,
    val widthPercent: Float = 0.84f,
    val fontSizeSp: Int = 18,
    val textColor: String = "#FFFFFFFF",
    val backgroundColor: String = "#66000000",
)

internal class DesktopLyricOverlayController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var textView: TextView? = null

    fun update(state: DesktopLyricOverlayState, text: String) {
        if (!state.enabled || text.isBlank() || !Settings.canDrawOverlays(appContext)) {
            remove()
            return
        }
        val view = textView ?: TextView(appContext).also {
            it.maxLines = 2
            it.includeFontPadding = false
            textView = it
            runCatching {
                windowManager.addView(it, state.toLayoutParams())
            }.onFailure {
                textView = null
                return
            }
        }
        view.text = text
        view.textSize = state.fontSizeSp.toFloat()
        view.setTextColor(parseAndroidColor(state.textColor, Color.WHITE))
        view.setBackgroundColor(parseAndroidColor(state.backgroundColor, 0x66000000))
        view.gravity = when (state.alignment) {
            DesktopLyricAlignment.Left -> Gravity.START
            DesktopLyricAlignment.Center -> Gravity.CENTER
            DesktopLyricAlignment.Right -> Gravity.END
        }
        view.setPadding(24, 12, 24, 12)
        runCatching {
            windowManager.updateViewLayout(view, state.toLayoutParams())
        }.onFailure {
            remove()
        }
    }

    fun remove() {
        textView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        textView = null
    }

    private fun DesktopLyricOverlayState.toLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = appContext.resources.displayMetrics
        return WindowManager.LayoutParams(
            (displayMetrics.widthPixels * widthPercent.coerceIn(0.2f, 1f)).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels * leftPercent.coerceIn(0f, 1f)).roundToInt()
            y = (displayMetrics.heightPixels * topPercent.coerceIn(0f, 1f)).roundToInt()
        }
    }
}

internal fun parseAndroidColor(raw: String, fallback: Int): Int {
    return runCatching { raw.toColorInt() }.getOrDefault(fallback)
}
