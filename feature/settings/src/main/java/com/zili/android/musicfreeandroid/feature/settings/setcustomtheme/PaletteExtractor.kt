package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PALETTE_FALLBACK_ARGB: Int = 0xFF3FA3B5.toInt()

// Cap decoded bitmap so palette extraction does not allocate full-res user photos.
private const val MAX_DECODE_DIMENSION: Int = 1080

data class PaletteColors(
    val primary: Color,
    val average: Color,
    val vibrant: Color,
)

/**
 * Indirection over disk copy + Palette extraction so [SetCustomThemeViewModel] is
 * trivially testable with a fake implementation (no real Bitmap / ContentResolver).
 */
interface ImageAndPaletteLoader {
    suspend fun copyImageToInternal(context: Context, uri: Uri): Uri?
    suspend fun extractPalette(context: Context, uri: Uri): PaletteColors?
}

object DefaultImageAndPaletteLoader : ImageAndPaletteLoader {

    override suspend fun copyImageToInternal(context: Context, uri: Uri): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = context.contentResolver.getType(uri)
                val ext = mime?.substringAfterLast('/', "jpg") ?: "jpg"
                val dest = File(context.filesDir, "theme_background.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                Uri.fromFile(dest)
            }.getOrNull()
        }

    override suspend fun extractPalette(context: Context, uri: Uri): PaletteColors? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampled(context, uri, MAX_DECODE_DIMENSION) ?: return@runCatching null
                val palette = Palette.from(bitmap).generate()
                val dominant = palette.getDominantColor(PALETTE_FALLBACK_ARGB)
                PaletteColors(
                    primary = Color(dominant),
                    average = Color(palette.getMutedColor(dominant)),
                    vibrant = Color(palette.getVibrantColor(dominant)),
                )
            }.getOrNull()
        }

    private fun decodeSampled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        var inSample = 1
        while (
            bounds.outWidth > 0 && bounds.outHeight > 0 &&
            (bounds.outWidth / inSample > maxDim || bounds.outHeight / inSample > maxDim)
        ) {
            inSample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }
}
