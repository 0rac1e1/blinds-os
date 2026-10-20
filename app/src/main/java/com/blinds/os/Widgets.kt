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
import androidx.compose.runtime.DisposableEffect
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

private fun nextClockStyle(style: String) = when (style) {
    "digital" -> "analog"
    "analog" -> "bare"
    else -> "digital"
}

private fun styleName(style: String) = when (style) {
    "analog" -> "Analog"
    "bare" -> "No card"
    else -> "Digital"
}

/** A widget on the home screen: its content, plus the edit-mode controls (remove, size, style). */
@Composable
fun WidgetItem(spec: WidgetSpec, ui: UiState, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val editing = ui.editing
    Box(
        modifier
            .dragSlot(ui, spec.id)
            .padding(5.dp)
            .graphicsLayer { alpha = if (ui.dragId == spec.id) 0.35f else 1f },
    ) {
        when (spec.type) {
            "clock" -> ClockWidget(spec)
            else -> Box(Modifier.fillMaxSize())
        }
        if (editing) {
            Chip("−", Alignment.TopStart) { s.removeWidget(spec.id) }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillButton("Size") { s.updateWidget(spec.copy(size = nextSize(spec.size))) }
                PillButton(styleName(spec.style)) {
                    s.updateWidget(spec.copy(style = nextClockStyle(spec.style)))
                }
            }
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
fun ClockWidget(spec: WidgetSpec) {
    val s = LocalSettings.current
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
    val small = spec.size == "s"
    val large = spec.size == "l"

    val body: @Composable () -> Unit = {
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
                if (battery >= 0 && !small) {
                    Txt("Battery $battery%", color = ink.copy(alpha = 0.65f), size = 13.sp, shadow = bare)
                }
            }
        }
    }

    if (bare) {
        Box(Modifier.fillMaxSize()) { body() }
    } else {
        GlassSurface(Modifier.fillMaxSize(), strength = 1f) { body() }
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

/** Edit-mode sheet for choosing a widget to add. */
@Composable
fun WidgetPicker(ui: UiState) {
    if (!ui.showWidgetPicker) return
    val s = LocalSettings.current
    val ink = LocalInk.current
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x66000000)) }
            .swallowTaps()
            .bouncy(onClick = { ui.showWidgetPicker = false }),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.fillMaxWidth().padding(24.dp).swallowTaps(), strength = 1.2f) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("Add a widget", color = ink, size = 20.sp, weight = FontWeight.SemiBold)
                Txt("It is added to this page. Drag it where you want, and use Size and style while editing.", color = ink.copy(alpha = 0.7f), size = 13.sp)
                val add: (String, String, String) -> Unit = { type, size, style ->
                    s.addWidget(type, size, style, ui.firstIdOnPage)
                    ui.showWidgetPicker = false
                }
                GlassButton("Digital clock", onClick = { add("clock", "wide", "digital") })
                GlassButton("Analog clock", onClick = { add("clock", "s", "analog") })
                GlassButton("Clock without card", onClick = { add("clock", "wide", "bare") })
                GlassButton("Cancel", onClick = { ui.showWidgetPicker = false })
            }
        }
    }
}
