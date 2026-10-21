package com.blinds.os

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

val LIVE_EFFECTS = listOf(
    "none" to "None",
    "curtains" to "Aurora curtains",
    "stars" to "Night sky",
    "rain" to "Rain",
    "snow" to "Snow",
    "waves" to "Waves",
    "lava" to "Lava lamp",
    "fireflies" to "Fireflies",
)

/** Same input, same number between 0 and 1: gives every particle its own fixed random look. */
private fun hash(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

private fun fract(v: Float) = v - floor(v)

private const val TAU = (2.0 * PI).toFloat()

/**
 * Animated effects drawn over the wallpaper. Everything is a pure function of time [t], so there is
 * no state to keep, and the effect stops costing anything when it is off.
 */
fun DrawScope.drawLive(kind: String, t: Float, screen: Size, tilt: Offset, colors: List<Color>) {
    val w = screen.width
    val h = screen.height
    val u = 1.dp.toPx()
    val pal = if (colors.isEmpty()) listOf(Color.White) else colors
    when (kind) {
        "stars" -> {
            for (i in 0 until 70) {
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + hash(i, 3) * 1.5f) + hash(i, 4) * TAU))
                drawCircle(
                    Color.White.copy(alpha = twinkle * 0.9f),
                    (0.7f + hash(i, 5) * 1.5f) * u,
                    Offset(hash(i, 1) * w + tilt.x * -6f * u * hash(i, 5), hash(i, 2) * h * 0.85f),
                )
            }
            val cyc = t / 8f
            val ph = fract(cyc)
            if (ph < 0.12f) {
                val k = ph / 0.12f
                val seed = floor(cyc).toInt()
                val sx = hash(seed, 7) * w * 0.6f
                val sy = hash(seed, 8) * h * 0.25f
                val head = Offset(sx + k * 0.45f * w, sy + k * 0.22f * w)
                val tail = Offset(head.x - 0.16f * w, head.y - 0.07f * w)
                drawLine(
                    Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1f - k)), start = tail, end = head),
                    tail, head, strokeWidth = 2f * u, cap = StrokeCap.Round,
                )
            }
        }
        "rain" -> {
            val slant = 0.22f + tilt.x * 0.25f
            for (i in 0 until 90) {
                val speed = 0.9f + hash(i, 2)
                val y = fract(t * speed * 0.8f + hash(i, 3)) * (h + 80f * u) - 40f * u
                val x = hash(i, 1) * w
                val len = (14f + 18f * hash(i, 4)) * u
                drawLine(
                    Color.White.copy(alpha = 0.18f + 0.22f * hash(i, 5)),
                    Offset(x - slant * len, y - len), Offset(x, y),
                    strokeWidth = 1.2f * u, cap = StrokeCap.Round,
                )
            }
        }
        "snow" -> {
            for (i in 0 until 70) {
                val y = fract(t * (0.04f + 0.05f * hash(i, 2)) + hash(i, 3)) * (h + 20f * u) - 10f * u
                val x = hash(i, 1) * w + sin(t * 0.6f + hash(i, 4) * TAU) * 18f * u + tilt.x * -10f * u
                drawCircle(Color.White.copy(alpha = 0.55f + 0.35f * hash(i, 6)), (1.4f + 2.4f * hash(i, 5)) * u, Offset(x, y))
            }
        }
        "waves" -> {
            for (l in 0 until 4) {
                val path = Path()
                val base = h * (0.64f + l * 0.08f)
                path.moveTo(0f, h)
                var x = 0f
                while (x <= w + 24f * u) {
                    val y = base + sin(x / w * TAU * (1.2f + l * 0.4f) + t * (0.5f + l * 0.2f) + l) * 16f * u * (1f + l * 0.3f) + tilt.y * 8f * u
                    path.lineTo(x, y)
                    x += 24f * u
                }
                path.lineTo(w + 24f * u, h)
                path.close()
                drawPath(path, pal[l % pal.size].copy(alpha = 0.26f))
            }
        }
        "lava" -> {
            for (i in 0 until 6) {
                val cx = w * (0.5f + 0.32f * sin(t * 0.17f * (1f + i * 0.3f) + i * 2f))
                val cy = h * (0.5f + 0.42f * sin(t * 0.12f * (1f + i * 0.25f) + i * 1.7f))
                val r = w * (0.26f + 0.1f * hash(i, 1))
                val c = pal[i % pal.size]
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.8f), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    r, Offset(cx, cy),
                )
            }
        }
        "fireflies" -> {
            val glow = Color(0xFFD9FF6B)
            for (i in 0 until 40) {
                val x = w * (0.5f + 0.46f * sin(t * 0.2f * (0.5f + hash(i, 1)) + hash(i, 2) * TAU))
                val y = h * (0.5f + 0.42f * sin(t * 0.17f * (0.5f + hash(i, 3)) + hash(i, 4) * TAU))
                val pulse = 0.5f + 0.5f * sin(t * (1f + hash(i, 5) * 2f) + i)
                val r = 11f * u
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = pulse * 0.8f), glow.copy(alpha = 0f)), center = Offset(x, y), radius = r),
                    r, Offset(x, y),
                )
                drawCircle(Color.White.copy(alpha = pulse), 1.4f * u, Offset(x, y))
            }
        }
        "curtains" -> {
            for (i in 0 until 5) {
                val x0 = w * (0.06f + 0.2f * i) + sin(t * 0.3f + i) * w * 0.05f
                val bw = w * 0.24f
                val c = pal[i % pal.size]
                val top = h * 0.04f
                val bottom = h * 0.78f
                val path = Path()
                var y = top
                var first = true
                while (y <= bottom) {
                    val x = x0 + sin(y * 0.006f / u * u + t * 0.5f + i * 1.3f) * 30f * u
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    y += 10f * u
                }
                y = bottom
                while (y >= top) {
                    val x = x0 + bw + sin(y * 0.006f / u * u + t * 0.5f + i * 1.3f) * 30f * u
                    path.lineTo(x, y)
                    y -= 10f * u
                }
                path.close()
                drawPath(
                    path,
                    Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.5f), Color.Transparent), startY = top, endY = bottom),
                )
            }
        }
    }
}

private val KEY_NIGHT = Pair(
    listOf(Color(0xFF050816), Color(0xFF0B1030), Color(0xFF1B1464)),
    listOf(Color(0xFF3A0CA3), Color(0xFF1E88E5), Color(0xFF7C4DFF), Color(0xFF00B8D4)),
)
private val KEY_DAWN = Pair(
    listOf(Color(0xFF2B1B4A), Color(0xFF7A3E7A), Color(0xFFFF9E80)),
    listOf(Color(0xFFFFB86B), Color(0xFFFF6B9A), Color(0xFF9575FF), Color(0xFFFFD36B)),
)
private val KEY_DAY = Pair(
    listOf(Color(0xFF0A5BA8), Color(0xFF2E8BD8), Color(0xFF7CC8FF)),
    listOf(Color(0xFFFFF3B0), Color(0xFF64D2FF), Color(0xFFB3E5FC), Color(0xFFFFFFFF)),
)
private val KEY_DUSK = Pair(
    listOf(Color(0xFF1A0B2E), Color(0xFF6A1B6A), Color(0xFFFF6B6B)),
    listOf(Color(0xFFFFB86B), Color(0xFFFF5E8A), Color(0xFF6A5CFF), Color(0xFFFFD36B)),
)
private val TIME_KEYS = listOf(KEY_NIGHT, KEY_DAWN, KEY_DAY, KEY_DUSK)

private var todMinute = -1L
private var todSpec: WallpaperSpec? = null

/** A wallpaper whose colours follow the phone's clock: night, dawn, day, dusk, and back. */
fun timeOfDaySpec(): WallpaperSpec {
    val now = System.currentTimeMillis()
    val minute = now / 60_000L
    todSpec?.let { if (minute == todMinute) return it }
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    val seg = (hour / 6f).toInt().coerceIn(0, 3)
    val f = (hour - seg * 6f) / 6f
    val a = TIME_KEYS[seg]
    val b = TIME_KEYS[(seg + 1) % 4]
    val spec = WallpaperSpec(
        "timeofday", "Time of day",
        a.first.indices.map { lerp(a.first[it], b.first[it], f) },
        a.second.indices.map { lerp(a.second[it], b.second[it], f) },
    )
    todMinute = minute
    todSpec = spec
    return spec
}
