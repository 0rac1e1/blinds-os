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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

const val LAYER_FULL = 0   // the whole icon, as the app drew it
const val LAYER_FG = 1     // just the foreground artwork of an adaptive icon
const val LAYER_MONO = 2   // the Android 13 monochrome layer, in white
const val LAYER_BG = 3     // just the background layer of an adaptive icon

/** Renders an app's icon into a square bitmap so it can be re-masked into any icon shape. */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(480)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(
        ctx: Context,
        app: AppEntry,
        px: Int,
        themed: Boolean,
        accent: Int,
        key: String,
        layer: Int = LAYER_FULL,
        pack: String = "",
    ): ImageBitmap {
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.Default) { render(ctx, app, px, themed, accent, layer, pack) }
        cache.put(key, bmp)
        return bmp
    }

    /** Stand-in (1x1) for "this icon has no such layer". */
    private fun noLayer(): ImageBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun render(ctx: Context, app: AppEntry, size: Int, themed: Boolean, accent: Int, layer: Int, pack: String): ImageBitmap {
        // a picture the person chose in Customize > Apps wins over everything else
        val custom = AppOverrides.iconFile(ctx, app.id)
        if (custom.exists()) {
            if (layer != LAYER_FULL) return noLayer()
            val cb = android.graphics.BitmapFactory.decodeFile(custom.path)
            if (cb != null) return Bitmap.createScaledBitmap(cb, size, size, true).asImageBitmap()
        }
        if (pack.isNotEmpty()) {
            val pd = IconPacks.drawableFor(ctx, pack, app.component)
            if (pd != null) {
                if (layer != LAYER_FULL) return noLayer()
                val pb = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val copy: Drawable = pd.constantState?.newDrawable(ctx.resources)?.mutate() ?: pd
                drawLegacy(Canvas(pb), copy, size)
                return pb.asImageBitmap()
            }
        }
        val pm = ctx.packageManager
        val raw: Drawable = try {
            // our own two entries load straight from our resources, so Android can never hand back the wrong icon
            when {
                app.isCustomize -> ctx.getDrawable(R.mipmap.ic_customize)
                app.isAbout -> ctx.getDrawable(R.mipmap.ic_launcher)
                else -> pm.getActivityIcon(app.component)
            } ?: pm.defaultActivityIcon
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        // work on our own copy so changing bounds never affects the system's cached drawable
        val d: Drawable = raw.constantState?.newDrawable(ctx.resources)?.mutate() ?: raw

        if (layer != LAYER_FULL && d !is AdaptiveIconDrawable) return noLayer()

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        if (d is AdaptiveIconDrawable && layer == LAYER_FG) {
            val lo = (-size * 0.25f).roundToInt()
            val hi = (size * 1.25f).roundToInt()
            d.foreground?.let {
                it.setBounds(lo, lo, hi, hi)
                it.draw(c)
            }
        } else if (d is AdaptiveIconDrawable && layer == LAYER_BG) {
            val lo = (-size * 0.25f).roundToInt()
            val hi = (size * 1.25f).roundToInt()
            d.background?.let {
                it.setBounds(lo, lo, hi, hi)
                it.draw(c)
            }
        } else if (d is AdaptiveIconDrawable && layer == LAYER_MONO) {
            val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
            if (mono == null) return noLayer()
            val lo = (-size * 0.25f).roundToInt()
            val hi = (size * 1.25f).roundToInt()
            mono.setTint(0xFFFFFFFF.toInt())
            mono.setBounds(lo, lo, hi, hi)
            mono.draw(c)
        } else if (d is AdaptiveIconDrawable) {
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
    val style = if (s.iconQuality == "off") "default" else s.iconStyle
    val key = "${app.id}|$px|$themed|$accent|${s.iconPack}|${s.iconVer}"
    val glassStyle = style != "default"

    val bmp by produceState<ImageBitmap?>(initialValue = IconCache.peek(key), key1 = key) {
        value = IconCache.load(ctx, app, px, themed, accent, key, pack = s.iconPack)
    }

    if (glassStyle) {
        val fgKey = "$key|fg"
        val monoKey = "$key|mono"
        val wantMono = style != "glass"
        val bgKey = "$key|bg"
        val bg by produceState<ImageBitmap?>(
            initialValue = if (!wantMono) IconCache.peek(bgKey) else null,
            key1 = bgKey,
            key2 = wantMono,
        ) {
            value = if (!wantMono) IconCache.load(ctx, app, px, themed, accent, bgKey, LAYER_BG, s.iconPack) else null
        }
        val fg by produceState<ImageBitmap?>(initialValue = IconCache.peek(fgKey), key1 = fgKey) {
            value = IconCache.load(ctx, app, px, themed, accent, fgKey, LAYER_FG, s.iconPack)
        }
        val mono by produceState<ImageBitmap?>(
            initialValue = if (wantMono) IconCache.peek(monoKey) else null,
            key1 = monoKey,
            key2 = wantMono,
        ) {
            value = if (wantMono) IconCache.load(ctx, app, px, themed, accent, monoKey, LAYER_MONO, s.iconPack) else null
        }
        GlassIcon(
            iconSize = size,
            modifier = modifier,
            style = style,
            full = bmp,
            bg = bg,
            fg = fg,
            mono = mono,
        )
        return
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

/**
 * Liquid glass app icons (Glass / Clear / Tinted).
 * The icon is a translucent glass tile over the wallpaper with a bright curved edge, a specular
 * highlight that follows the light angle, and the app's artwork floating on top.
 */
@Composable
private fun GlassIcon(
    iconSize: Dp,
    modifier: Modifier,
    style: String,
    full: ImageBitmap?,
    bg: ImageBitmap?,
    fg: ImageBitmap?,
    mono: ImageBitmap?,
) {
    val s = LocalSettings.current
    val bd = LocalBackdrop.current
    val dark = LocalDark.current
    val shape = iconShapeFor(s.iconShape)
    var pos by remember { mutableStateOf(Offset.Zero) }

    // tilt shifts the light in coarse steps and is only read while drawing, so icons are not
    // recomposed on every sensor update
    val lightShift by remember {
        derivedStateOf {
            if (s.motionLight && bd != null) ((bd.tilt.x * 28f) / 6f).roundToInt() * 6 else 0
        }
    }
    val plate = when {
        style == "tinted" -> Color(s.accent)
        s.iconDarkGlass -> Color(0xFF12141A)
        else -> Color.White
    }
    val frost = when (style) {
        "clear" -> s.iconFrost * 0.55f
        "tinted" -> s.iconTintAmt * 0.6f + s.iconFrost * 0.3f
        else -> s.iconFrost
    }
    val edge = s.iconEdge
    val shine = s.iconShine
    val tintAmt = s.iconTintAmt
    // only a custom photo wallpaper needs a blurred slice behind the tile; the built-in animated
    // wallpapers are already smooth, and redrawing them under every icon every frame is what lags
    val useSlice = s.iconQuality == "full" && bd != null && s.useCustomWallpaper && bd.custom != null
    val darkGlass = dark || s.iconDarkGlass
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val realFg = fg != null && fg.width > 1
    val realBg = bg != null && bg.width > 1
    val realMono = mono != null && mono.width > 1

    Box(
        modifier
            .size(iconSize)
            .shadow((2f + 7f * s.glassDepth).dp, shape)
            .then(if (useSlice) Modifier.onGloballyPositioned { pos = it.positionInRoot() } else Modifier)
            .clip(shape)
            .drawBehind {
                val sz = this.size
                val w = sz.width
                val h = sz.height
                val dst = IntSize(w.roundToInt(), h.roundToInt())

                // 1. a soft slice of the wallpaper behind the tile (frosted look on photo wallpapers)
                if (useSlice && bd != null) {
                    translate(left = -pos.x, top = -pos.y) {
                        drawWallpaper(bd, s, dark, soft = true)
                    }
                }

                // 2. glass body
                drawRect(plate.copy(alpha = frost.coerceIn(0f, 0.9f)))

                // 3. the app's artwork
                if (style == "glass") {
                    if (realBg && bg != null && realFg && fg != null) {
                        // the coloured background is see-through glass tint, the logo stays solid
                        drawImage(bg, dstSize = dst, alpha = tintAmt.coerceIn(0f, 1f))
                        drawImage(fg, dstSize = dst)
                    } else if (full != null) {
                        drawImage(full, dstSize = dst, alpha = (0.25f + 0.55f * tintAmt).coerceIn(0f, 1f))
                    }
                } else {
                    if (realMono && mono != null) {
                        drawImage(mono, dstSize = dst, alpha = 0.96f)
                    } else if (realFg && fg != null) {
                        drawImage(fg, dstSize = dst, alpha = 0.92f, colorFilter = gray)
                    } else if (full != null) {
                        drawImage(full, dstSize = dst, alpha = 0.6f, colorFilter = gray)
                    }
                }

                // 4. glass thickness: bright top, darker bottom
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.26f * shine),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (darkGlass) 0.16f else 0.06f),
                    )
                )

                // 5. specular bloom on the side facing the light, lens-like brighter edge, rim light
                val a = Math.toRadians((s.glassLight + lightShift).toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val half = hypot(w, h) / 2f
                val c = Offset(w / 2f, h / 2f)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.34f * shine), Color.Transparent),
                        center = Offset(c.x + dx * half * 0.6f, c.y + dy * half * 0.6f),
                        radius = max(w, h) * 0.8f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.16f * edge),
                        center = c,
                        radius = half,
                    )
                )
                drawOutline(
                    outline = shape.createOutline(sz, layoutDirection, this),
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.95f * edge),
                        0.3f to Color.White.copy(alpha = 0.14f * edge),
                        0.7f to Color.White.copy(alpha = 0.14f * edge),
                        1f to Color.White.copy(alpha = 0.6f * edge),
                        start = Offset(c.x + dx * half, c.y + dy * half),
                        end = Offset(c.x - dx * half, c.y - dy * half),
                    ),
                    style = Stroke(width = (0.7f + 1.7f * edge).dp.toPx()),
                )
            }
    )
}
