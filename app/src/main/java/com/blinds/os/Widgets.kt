package com.blinds.os

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Registers a home-screen item's on-screen box so dragging can find it. */
@Composable
fun Modifier.dragSlot(ui: UiState, id: String): Modifier {
    val token = remember { Any() }
    DisposableEffect(id) {
        onDispose { if (ui.cells[id]?.token === token) ui.cells.remove(id) }
    }
    return this.onGloballyPositioned { c ->
        val p = c.positionInWindow()
        ui.cells[id] = CellInfo(token, Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
    }
}

private fun nextSize(size: String) = when (size) {
    "s" -> "w"
    "w" -> "l"
    else -> "s"
}

internal val WIDGET_TINTS = listOf(
    0, 0xFFFFFFFF.toInt(), 0xFFCFE8FF.toInt(), 0xFFFFD1E0.toInt(), 0xFFFFE0A6.toInt(),
    0xFFC8F5E1.toInt(), 0xFFDCCBFF.toInt(), 0xFF1B1B22.toInt(),
)
internal val WIDGET_TEXTS = listOf(
    0, 0xFFFFFFFF.toInt(), 0xFF0F1115.toInt(), 0xFF0A84FF.toInt(), 0xFFFFD60A.toInt(),
    0xFFFF453A.toInt(), 0xFF30D158.toInt(), 0xFFBF5AF2.toInt(),
)

/** Small, wide or large, judged by how many cells the widget covers. */
fun WidgetSpec.cls(cols: Int): String {
    val (w, h) = span(cols)
    return when {
        w * h <= 4 -> "s"
        h >= 4 -> "l"
        else -> "w"
    }
}

/** A widget on the home screen: its content, plus the edit-mode controls (remove, size, edit, resize handle). */
@Composable
fun WidgetItem(
    spec: WidgetSpec,
    ui: UiState,
    cellW: Dp,
    cellH: Dp,
    onLayoutChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalSettings.current
    val editing = ui.editing
    Box(
        modifier
            .dragSlot(ui, spec.id)
            .padding(5.dp)
            .graphicsLayer { alpha = if (ui.dragId == spec.id) 0.35f else 1f },
    ) {
        WidgetFrame(spec) {
            WidgetContent(spec, ui)
        }
        if (editing) {
            val (w, h) = spec.span(s.cols)
            Chip("−", Alignment.TopStart) { s.removeWidget(spec.id) }
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillButton("Size") {
                    val presets = listOf(2 to 2, s.cols to 2, s.cols to 4)
                    val n = presets[(presets.indexOf(w to h) + 1) % presets.size]
                    s.updateWidget(spec.copy(cw = n.first, ch = n.second))
                    onLayoutChanged()
                }
                PillButton("Edit") {
                    // the look of a widget is edited in the Customize app
                    CustomizeState.section = "widgets"
                    CustomizeState.focusWidget = spec.id
                    ui.editing = false
                    ui.selectedId = null
                    ui.showSettings = true
                }
            }
            ResizeHandle(w, h, cellW, cellH, s.cols, s.rows, onResize = { nw, nh ->
                s.widget(spec.id)?.let { s.updateWidget(it.copy(cw = nw, ch = nh)) }
            }, onDone = onLayoutChanged)
        }
    }
}

/** The corner handle you drag to stretch a widget over more (or fewer) grid cells. */
@Composable
fun BoxScope.ResizeHandle(
    w: Int,
    h: Int,
    cellW: Dp,
    cellH: Dp,
    maxW: Int,
    maxH: Int,
    onResize: (Int, Int) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val cwPx = with(density) { cellW.toPx() }
    val chPx = with(density) { cellH.toPx() }
    val curW by rememberUpdatedState(w)
    val curH by rememberUpdatedState(h)
    val resize by rememberUpdatedState(onResize)
    val done by rememberUpdatedState(onDone)
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(2.dp)
            .size(36.dp)
            .pointerInput(cwPx, chPx, maxW, maxH) {
                var dx = 0f
                var dy = 0f
                var sw = 1
                var sh = 1
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                        sw = curW
                        sh = curH
                    },
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        val nw = (sw + (dx / cwPx).roundToInt()).coerceIn(1, maxW)
                        val nh = (sh + (dy / chPx).roundToInt()).coerceIn(1, maxH)
                        if (nw != curW || nh != curH) resize(nw, nh)
                    },
                )
            }
            .clip(CircleShape)
            .drawBehind {
                drawRect(Color(0xE6303036))
                val a = Offset(size.width * 0.34f, size.height * 0.66f)
                val b = Offset(size.width * 0.66f, size.height * 0.34f)
                drawLine(Color.White, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(a.x, a.y - size.height * 0.16f), a, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(a.x + size.width * 0.16f, a.y), a, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(b.x, b.y + size.height * 0.16f), b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(b.x - size.width * 0.16f, b.y), b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            },
    )
}

/** The glass card (or nothing, for "No card") a widget sits in, with the widget's own look applied. */
@Composable
fun WidgetFrame(spec: WidgetSpec, content: @Composable () -> Unit) {
    val s = LocalSettings.current
    val ink = if (spec.text != 0) Color(spec.text) else LocalInk.current
    CompositionLocalProvider(LocalInk provides ink) {
        if (spec.style == "bare") {
            Box(Modifier.fillMaxSize()) { content() }
        } else {
            val shape = RoundedCornerShape((if (spec.corner >= 0f) spec.corner else s.cornerRadius).dp)
            GlassSurface(
                Modifier.fillMaxSize(),
                shape = shape,
                strength = 1f,
                tint = if (spec.tint != 0) Color(spec.tint) else null,
                tintAlpha = if (spec.frost >= 0f) spec.frost else null,
            ) { content() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Chip(
    label: String,
    at: Alignment,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .align(at)
            .offset(x = 2.dp, y = 2.dp)
            .size(26.dp)
            .bouncy(onClick = onClick)
            .clip(CircleShape)
            .drawBehind { drawRect(Color(0xE6303036)) },
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, color = Color.White, size = 18.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .bouncy(onClick = onClick)
            .clip(CircleShape)
            .drawBehind { drawRect(Color(0xCC303036)) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, color = Color.White, size = 12.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
fun ClockBody(spec: WidgetSpec) {
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val minute = now / 60_000L
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val bare = spec.style == "bare"
    val cls = spec.cls(LocalSettings.current.cols)
    val small = cls == "s"
    val large = cls == "l"

    if (spec.style == "analog") {
        AnalogFace(now, ink, small)
    } else {
        val timeText = remember(minute, is24) {
            SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
        }
        val dateText = remember(minute, small) {
            SimpleDateFormat(if (small) "EEE, MMM d" else "EEEE, MMMM d", Locale.getDefault()).format(Date(now))
        }
        val battery = remember(minute) {
            try {
                (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (small) 14.dp else 22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Txt(dateText.uppercase(), color = ink.copy(alpha = 0.75f), size = if (small) 11.sp else 13.sp, weight = FontWeight.SemiBold, spacing = 1.2.sp, shadow = bare, maxLines = 1)
            Txt(timeText, color = ink, size = if (small) 40.sp else if (large) 96.sp else 66.sp, weight = FontWeight.Light, shadow = bare)
            val second = if (small) null else secondCityText(spec.extra, now, is24)
            if (second != null) {
                Txt(second, color = ink.copy(alpha = 0.8f), size = 15.sp, weight = FontWeight.Medium, maxLines = 1, shadow = bare)
            }
            if (battery >= 0 && !small) {
                Txt("Battery $battery%", color = ink.copy(alpha = 0.65f), size = 13.sp, shadow = bare)
            }
        }
    }
}

@Composable
private fun AnalogFace(now: Long, ink: Color, small: Boolean) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f)
    val m = cal.get(Calendar.MINUTE).toFloat()
    val accent = Color(LocalSettings.current.accent)
    Canvas(Modifier.fillMaxSize().padding(if (small) 14.dp else 18.dp)) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ink.copy(alpha = 0.25f), r, c, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val len = if (i % 3 == 0) 0.14f else 0.07f
            val from = Offset(c.x + (r * (1f - len)) * cos(a).toFloat(), c.y + (r * (1f - len)) * sin(a).toFloat())
            val to = Offset(c.x + (r * 0.96f) * cos(a).toFloat(), c.y + (r * 0.96f) * sin(a).toFloat())
            drawLine(ink.copy(alpha = if (i % 3 == 0) 0.9f else 0.45f), from, to, strokeWidth = (if (i % 3 == 0) 3.dp else 1.5.dp).toPx(), cap = StrokeCap.Round)
        }
        fun hand(deg: Float, length: Float, width: Float, color: Color) {
            val a = Math.toRadians(deg - 90.0)
            drawLine(color, c, Offset(c.x + r * length * cos(a).toFloat(), c.y + r * length * sin(a).toFloat()), strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(h * 30f, 0.5f, 5.dp.toPx(), ink)
        hand(m * 6f, 0.78f, 3.dp.toPx(), ink)
        drawCircle(accent, 4.dp.toPx(), c)
    }
}
