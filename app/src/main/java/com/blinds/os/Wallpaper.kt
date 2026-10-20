package com.blinds.os

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class WallpaperSpec(val id: String, val name: String, val base: List<Color>, val blobs: List<Color>)

val WALLPAPERS = listOf(
    WallpaperSpec(
        "aurora", "Aurora",
        listOf(Color(0xFF0B1030), Color(0xFF1B1464), Color(0xFF3A0CA3)),
        listOf(Color(0xFF00E5FF), Color(0xFFFF2E93), Color(0xFF7C4DFF), Color(0xFF00FFA3)),
    ),
    WallpaperSpec(
        "dusk", "Dusk",
        listOf(Color(0xFF1A0B2E), Color(0xFF6A1B6A), Color(0xFFFF6B6B)),
        listOf(Color(0xFFFFB86B), Color(0xFFFF5E8A), Color(0xFF6A5CFF), Color(0xFFFFD36B)),
    ),
    WallpaperSpec(
        "ocean", "Ocean",
        listOf(Color(0xFF001B3A), Color(0xFF003B6F), Color(0xFF0077B6)),
        listOf(Color(0xFF00D4FF), Color(0xFF48CAE4), Color(0xFF0096C7), Color(0xFF90E0EF)),
    ),
    WallpaperSpec(
        "ember", "Ember",
        listOf(Color(0xFF1A0505), Color(0xFF5A0E0E), Color(0xFFB3261E)),
        listOf(Color(0xFFFF7A18), Color(0xFFFFC837), Color(0xFFFF3D00), Color(0xFFFF6EC7)),
    ),
    WallpaperSpec(
        "forest", "Forest",
        listOf(Color(0xFF04140D), Color(0xFF0B3D2E), Color(0xFF146356)),
        listOf(Color(0xFF5EEAD4), Color(0xFFA3E635), Color(0xFF34D399), Color(0xFF22D3EE)),
    ),
    WallpaperSpec(
        "mono", "Graphite",
        listOf(Color(0xFF0A0A0A), Color(0xFF1C1C1E), Color(0xFF2C2C2E)),
        listOf(Color(0xFF6B7280), Color(0xFF9CA3AF), Color(0xFF4B5563), Color(0xFFE5E7EB)),
    ),
)

/**
 * Shared state the wallpaper and every glass surface read from.
 * Glass panels redraw a blurred slice of the same wallpaper, so they all need the same clock.
 */
class Backdrop {
    var size by mutableStateOf(Size.Zero)
    var timeSec by mutableFloatStateOf(0f)
    var tilt by mutableStateOf(Offset.Zero)
    var custom by mutableStateOf<ImageBitmap?>(null)
}

/**
 * A tiny, heavily downscaled copy of the custom wallpaper photo. Drawing it stretched up gives a cheap,
 * smooth "blurred" look, so many glass tiles (icons) can share it instead of each running a real blur.
 */
object SoftWallpaper {
    private var source: ImageBitmap? = null
    private var soft: ImageBitmap? = null

    fun of(src: ImageBitmap): ImageBitmap {
        val cached = soft
        if (cached != null && source === src) return cached
        var cur: Bitmap = src.asAndroidBitmap()
        val targetW = (cur.width / 24).coerceAtLeast(8)
        while (cur.width > targetW * 2) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        val ratio = cur.height.toFloat() / cur.width.toFloat()
        val out = Bitmap.createScaledBitmap(cur, targetW, (targetW * ratio).roundToInt().coerceAtLeast(2), true)
        val img = out.asImageBitmap()
        source = src
        soft = img
        return img
    }
}

/**
 * Paints the wallpaper into the current DrawScope. [screen] is always the full screen size.
 * With [soft] set, a custom photo is drawn from its pre-blurred copy.
 */
fun DrawScope.drawWallpaper(bd: Backdrop, s: LauncherSettings, dark: Boolean, soft: Boolean = false) {
    val screen = bd.size
    if (screen.width <= 0f || screen.height <= 0f) return
    val par = s.parallax * 36f
    val ox = -bd.tilt.x * par
    val oy = bd.tilt.y * par

    val custom = if (s.useCustomWallpaper) bd.custom else null
    if (custom != null) {
        val scale = max(screen.width / custom.width, screen.height / custom.height) * 1.1f
        val dw = custom.width * scale
        val dh = custom.height * scale
        val img = if (soft) SoftWallpaper.of(custom) else custom
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((screen.width - dw) / 2f + ox).roundToInt(), ((screen.height - dh) / 2f + oy).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        )
    } else {
        val spec = WALLPAPERS.firstOrNull { it.id == s.wallpaper } ?: WALLPAPERS[0]
        val t = bd.timeSec
        drawRect(
            brush = Brush.linearGradient(spec.base, start = Offset.Zero, end = Offset(screen.width, screen.height)),
            size = screen,
        )
        spec.blobs.forEachIndexed { i, c ->
            val phase = i * 1.7f
            val cx = screen.width * (0.5f + 0.4f * sin(t * 0.13f * (1f + i * 0.21f) + phase)) + ox * (1f + i * 0.3f)
            val cy = screen.height * (0.5f + 0.4f * cos(t * 0.11f * (1f + i * 0.17f) + phase * 1.3f)) + oy * (1f + i * 0.3f)
            val r = screen.width * (0.62f + 0.12f * sin(t * 0.2f + phase))
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.85f), c.copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }

    // gentle vignette so icons and labels stay readable on any wallpaper
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.38f)),
            startY = 0f,
            endY = screen.height,
        ),
        size = screen,
    )
    if (!dark) {
        // light mode washes the wallpaper out so dark text stays legible
        drawRect(color = Color.White.copy(alpha = 0.46f), size = screen)
    }
}

private const val MAX_WALLPAPER_EDGE = 2000

/** Copies a picked photo into app storage (downscaled) so it keeps working after the picker closes. */
fun saveWallpaper(ctx: Context, uri: Uri): Boolean = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > MAX_WALLPAPER_EDGE || bounds.outHeight / sample > MAX_WALLPAPER_EDGE) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (bmp == null) {
        false
    } else {
        File(ctx.filesDir, "wallpaper.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }
} catch (e: Exception) {
    false
}

fun loadWallpaper(ctx: Context): ImageBitmap? = try {
    val f = File(ctx.filesDir, "wallpaper.png")
    if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
} catch (e: Exception) {
    null
}
