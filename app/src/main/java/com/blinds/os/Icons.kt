package com.blinds.os

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Renders an app's icon into a square bitmap so it can be re-masked into any icon shape. */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(220)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(ctx: Context, app: AppEntry, px: Int, themed: Boolean, accent: Int, key: String): ImageBitmap {
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.Default) { render(ctx, app, px, themed, accent) }
        cache.put(key, bmp)
        return bmp
    }

    private fun render(ctx: Context, app: AppEntry, size: Int, themed: Boolean, accent: Int): ImageBitmap {
        val pm = ctx.packageManager
        val raw: Drawable = try {
            pm.getActivityIcon(app.component)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        // work on our own copy so changing bounds never affects the system's cached drawable
        val d: Drawable = raw.constantState?.newDrawable(ctx.resources)?.mutate() ?: raw

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        if (d is AdaptiveIconDrawable) {
            // adaptive layers are 108dp but only the middle 72dp is meant to be seen: zoom in to that
            val lo = (-size * 0.25f).roundToInt()
            val hi = (size * 1.25f).roundToInt()
            val mono = if (themed && Build.VERSION.SDK_INT >= 33) d.monochrome else null
            if (mono != null) {
                c.drawColor(ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.72f))
                mono.setTint(ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.7f))
                mono.setBounds(lo, lo, hi, hi)
                mono.draw(c)
            } else {
                d.background?.let {
                    it.setBounds(lo, lo, hi, hi)
                    it.draw(c)
                }
                d.foreground?.let {
                    it.setBounds(lo, lo, hi, hi)
                    it.draw(c)
                }
            }
        } else {
            drawLegacy(c, d, size)
        }
        return bmp.asImageBitmap()
    }

    /**
     * Older, non-adaptive icons usually come with their own rounded corners and transparent margin.
     * Find the visible artwork and scale it up to fill the whole shape (like iOS does) instead of
     * floating it on a white plate. Tiny glyph-only icons still get a soft plate.
     */
    private fun drawLegacy(c: Canvas, d: Drawable, size: Int) {
        val work = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(work))

        val px = IntArray(size * size)
        work.getPixels(px, 0, size, 0, 0, size, size)
        var minX = size
        var minY = size
        var maxX = -1
        var maxY = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if ((px[y * size + x] ushr 24) > 40) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        val coverage = if (maxX < 0) 0f else (w * h).toFloat() / (size * size)

        if (maxX < 0 || coverage < 0.30f) {
            // glyph-only icon: soft plate, glyph with a modest margin
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                0xFFF6F7FA.toInt(), 0xFFD5D9E4.toInt(), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            val inset = (size * 0.16f).roundToInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(c)
            return
        }

        // colour of the artwork's outer edge, used to fill any transparent corners after scaling
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        val ring = 3
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val onEdge = x < minX + ring || x > maxX - ring || y < minY + ring || y > maxY - ring
                if (!onEdge) continue
                val v = px[y * size + x]
                if ((v ushr 24) > 200) {
                    r += (v shr 16) and 0xFF
                    g += (v shr 8) and 0xFF
                    b += v and 0xFF
                    n++
                }
            }
        }
        if (n > 0) {
            c.drawColor(
                0xFF000000.toInt() or
                    ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            )
        }

        val side = maxOf(w, h).toFloat()
        val scale = size / side
        val cx = minX + w / 2f
        val cy = minY + h / 2f
        val m = Matrix()
        m.postScale(scale, scale)
        m.postTranslate(size / 2f - cx * scale, size / 2f - cy * scale)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(work, m, bp)
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val themed = s.themedIcons
    val accent = s.accent
    val key = "${app.id}|$px|$themed|$accent"

    val bmp by produceState<ImageBitmap?>(initialValue = IconCache.peek(key), key1 = key) {
        value = IconCache.load(ctx, app, px, themed, accent, key)
    }

    val shape = iconShapeFor(s.iconShape)
    val glossy = s.iconGlass
    Box(
        modifier
            .size(size)
            .shadow(5.dp, shape)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (glossy) {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                            endY = this.size.height * 0.55f,
                        )
                    )
                    drawOutline(
                        outline = shape.createOutline(this.size, layoutDirection, this),
                        color = Color.White.copy(alpha = 0.28f),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            }
    ) {
        bmp?.let {
            Image(bitmap = it, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        }
    }
}
