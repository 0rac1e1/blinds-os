package com.blinds.os

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One ready-made widget the picker can place. cw/ch are cells; [FULL_WIDTH] means "the whole row". */
class WidgetVariant(
    val label: String,
    val type: String,
    val style: String,
    val cw: Int,
    val ch: Int,
    val extra: String = "",
)

class WidgetCategory(val id: String, val title: String, val blurb: String, val variants: List<WidgetVariant>)

const val FULL_WIDTH = 99

val WIDGET_CATEGORIES = listOf(
    WidgetCategory(
        "time", "Time", "Clocks for your home screen",
        listOf(
            WidgetVariant("Digital clock", "clock", "digital", FULL_WIDTH, 2),
            WidgetVariant("Large digital clock", "clock", "digital", FULL_WIDTH, 4),
            WidgetVariant("Analog clock", "clock", "analog", 2, 2),
            WidgetVariant("Clock without a card", "clock", "bare", FULL_WIDTH, 2),
            WidgetVariant("World clock (London)", "clock", "digital", FULL_WIDTH, 2, "Europe/London;London"),
        ),
    ),
    WidgetCategory(
        "weather", "Weather", "The forecast for your city",
        listOf(
            WidgetVariant("Small", "weather", "card", 2, 2),
            WidgetVariant("Wide with hourly forecast", "weather", "card", FULL_WIDTH, 2),
            WidgetVariant("Large with the week ahead", "weather", "card", FULL_WIDTH, 4),
            WidgetVariant("Wide without a card", "weather", "bare", FULL_WIDTH, 2),
        ),
    ),
    WidgetCategory(
        "battery", "Battery", "Your charge level at a glance",
        listOf(
            WidgetVariant("Ring", "battery", "ring", 2, 2),
            WidgetVariant("Bar", "battery", "bar", FULL_WIDTH, 1),
        ),
    ),
    WidgetCategory(
        "calendar", "Calendar", "Today's date and your next events",
        listOf(
            WidgetVariant("Today", "date", "strip", 2, 2),
            WidgetVariant("Week strip", "date", "strip", FULL_WIDTH, 2),
            WidgetVariant("Next event", "calendar", "card", 2, 2),
            WidgetVariant("Up next", "calendar", "card", FULL_WIDTH, 2),
            WidgetVariant("Agenda", "calendar", "card", FULL_WIDTH, 4),
        ),
    ),
    WidgetCategory(
        "tools", "Music and tools", "What's playing, notes and quick toggles",
        listOf(
            WidgetVariant("Now playing", "music", "card", FULL_WIDTH, 1),
            WidgetVariant("Notes (small)", "notes", "card", 2, 2),
            WidgetVariant("Notes (wide)", "notes", "card", FULL_WIDTH, 2),
            WidgetVariant("Quick toggles", "toggles", "card", FULL_WIDTH, 1),
        ),
    ),
    WidgetCategory(
        "stacks", "Stacks", "Several widgets in one spot, swipe up and down",
        listOf(
            WidgetVariant("Small stack (battery, date, weather)", "stack", "card", 2, 2, "battery:ring,date:strip,weather:card"),
            WidgetVariant("Wide stack (clock, weather, calendar)", "stack", "card", FULL_WIDTH, 2, "clock:digital,weather:card,calendar:card"),
        ),
    ),
)

/** Adds a widget made from [v] to the home screen, on the page being viewed. */
fun placeWidgetVariant(s: LauncherSettings, ui: UiState, v: WidgetVariant) {
    val id = s.addWidget(v.type, "w", v.style, ui.firstIdOnPage)
    s.widget(id)?.let { s.updateWidget(it.copy(cw = v.cw, ch = v.ch, extra = v.extra)) }
    ui.pendingPage = ui.currentPage
    ui.pendingPin = id
    ui.pop(id)
}

/** Lays [content] out at its real size, then shrinks the picture so it fits a small preview. */
@Composable
fun ScaledBox(realW: Dp, realH: Dp, scale: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val d = LocalDensity.current
    Box(
        modifier
            .clipToBounds()
            .layout { measurable, _ ->
                val rw = with(d) { realW.roundToPx() }
                val rh = with(d) { realH.roundToPx() }
                val p = measurable.measure(Constraints.fixed(rw, rh))
                layout((rw * scale).roundToInt(), (rh * scale).roundToInt()) { p.placeRelative(0, 0) }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
        content = content,
    )
}

@Composable
private fun VariantPreview(v: WidgetVariant, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val cols = s.cols
    val cw = v.cw.coerceAtMost(cols)
    val realW = (cw * 78).dp
    val realH = (v.ch * 88).dp
    val previewW = minOf(realW.value * 0.62f, 230f)
    val spec = remember(v) { WidgetSpec("preview", v.type, "w", v.style, cw = v.cw, ch = v.ch, extra = v.extra) }
    val shape = RoundedCornerShape(24.dp)
    ScaledBox(realW, realH, previewW / realW.value) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (v.style == "bare") Modifier.border(1.dp, ink.copy(alpha = 0.18f), shape)
                    else Modifier.clip(shape).background(ink.copy(alpha = 0.12f)).border(1.dp, ink.copy(alpha = 0.22f), shape)
                ),
        ) {
            WidgetContent(spec, ui, preview = true)
        }
    }
}

/** The "+ Widget" sheet: categories that open to show a live preview of every widget in them. */
@Composable
fun WidgetPicker(ui: UiState) {
    if (!ui.showWidgetPicker) return
    val s = LocalSettings.current
    val ink = LocalInk.current
    var open by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x66000000)) }
            .bouncy(onClick = { ui.showWidgetPicker = false }),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.fillMaxWidth().padding(16.dp).swallowTaps(), strength = 1.2f) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("Add a widget", color = ink, size = 20.sp, weight = FontWeight.SemiBold)
                        Txt("Open a category, tap the one you like. It lands on this page; drag it where you want.", color = ink.copy(alpha = 0.65f), size = 12.sp)
                    }
                    GlassButton("Close", onClick = { ui.showWidgetPicker = false })
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                    WIDGET_CATEGORIES.forEach { cat ->
                        val isOpen = open == cat.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(ink.copy(alpha = if (isOpen) 0.14f else 0.08f))
                                .bouncy(onClick = { open = if (isOpen) null else cat.id }, pressedScale = 0.98f)
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Txt(cat.title, size = 17.sp, weight = FontWeight.SemiBold)
                                Txt(cat.blurb, size = 12.sp, color = ink.copy(alpha = 0.6f))
                            }
                            Txt(if (isOpen) "⌃" else "⌄", size = 20.sp, color = ink.copy(alpha = 0.6f))
                        }
                        AnimatedVisibility(
                            visible = isOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                cat.variants.forEach { v ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .bouncy(onClick = {
                                                ui.showWidgetPicker = false
                                                if (v.type == "weather" && s.weatherLat.isEmpty()) {
                                                    ui.pendingVariant = v
                                                    ui.addWeatherAfterCity = true
                                                    ui.showCity = true
                                                } else {
                                                    placeWidgetVariant(s, ui, v)
                                                }
                                            }, pressedScale = 0.97f)
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        VariantPreview(v, ui)
                                        Spacer(Modifier.height(6.dp))
                                        Txt(v.label, size = 13.sp, color = ink.copy(alpha = 0.8f), weight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ink.copy(alpha = 0.08f))
                            .bouncy(onClick = {
                                ui.showWidgetPicker = false
                                ui.showWidgetList = true
                            }, pressedScale = 0.98f)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt("From your apps", size = 17.sp, weight = FontWeight.SemiBold)
                            Txt("Real widgets that other apps provide", size = 12.sp, color = ink.copy(alpha = 0.6f))
                        }
                        Txt("›", size = 22.sp, color = ink.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Per-widget look controls (shown in Customize > Widgets)
// ---------------------------------------------------------------------------------------------

fun widgetTitle(spec: WidgetSpec, all: List<WidgetSpec>): String {
    val base = when (spec.type) {
        "clock" -> "Clock"
        "weather" -> "Weather"
        "battery" -> "Battery"
        "date" -> "Date"
        "calendar" -> "Calendar"
        "music" -> "Music"
        "notes" -> "Notes"
        "toggles" -> "Quick toggles"
        "stack" -> "Stack"
        else -> "Widget"
    }
    val same = all.filter { it.type == spec.type }
    return if (same.size > 1) "$base ${same.indexOfFirst { it.id == spec.id } + 1}" else base
}

private fun defaultStyle(type: String) = when (type) {
    "clock" -> "digital"
    "battery" -> "ring"
    "date" -> "strip"
    else -> "card"
}

@Composable
fun WidgetLookControls(spec: WidgetSpec, ui: UiState) {
    val s = LocalSettings.current
    ChoiceRow("Size (or drag the corner handle when arranging)", listOf("s" to "Small", "w" to "Wide", "l" to "Large"), spec.cls(s.cols)) {
        val n = when (it) {
            "s" -> 2 to 2
            "w" -> s.cols to 2
            else -> s.cols to 4
        }
        s.updateWidget(spec.copy(cw = n.first, ch = n.second))
        ui.relayout += 1
    }
    when (spec.type) {
        "clock" -> ChoiceRow("Style", listOf("digital" to "Digital", "analog" to "Analog", "bare" to "No card"), spec.style) {
            s.updateWidget(spec.copy(style = it))
        }
        "battery" -> ChoiceRow("Style", listOf("ring" to "Ring", "bar" to "Bar"), spec.style) {
            s.updateWidget(spec.copy(style = it))
        }
        else -> ChoiceRow("Style", listOf("card" to "Glass card", "bare" to "No card"), if (spec.style == "bare") "bare" else "card") {
            s.updateWidget(spec.copy(style = if (it == "bare") "bare" else defaultStyle(spec.type)))
        }
    }
    if (spec.style != "bare") {
        SliderRow("Frost", "${((if (spec.frost >= 0f) spec.frost else s.glassTint) * 100).roundToInt()}%", if (spec.frost >= 0f) spec.frost else s.glassTint, 0f..0.7f) {
            s.updateWidget(spec.copy(frost = it))
        }
        SwatchRow("Tint (first = default)", WIDGET_TINTS, spec.tint) { s.updateWidget(spec.copy(tint = it)) }
        SliderRow("Corner roundness", "${(if (spec.corner >= 0f) spec.corner else s.cornerRadius).roundToInt()}dp", if (spec.corner >= 0f) spec.corner else s.cornerRadius, 0f..44f) {
            s.updateWidget(spec.copy(corner = it))
        }
    }
    SwatchRow("Text colour (first = default)", WIDGET_TEXTS, spec.text) { s.updateWidget(spec.copy(text = it)) }
    if (spec.type == "clock" && spec.style != "analog") {
        ChoiceRow("Second city (wide and large only)", WORLD_CITIES, spec.extra) { s.updateWidget(spec.copy(extra = it)) }
    }
    if (spec.type == "weather") {
        ActionRow("Change city or units…") {
            ui.addWeatherAfterCity = false
            ui.showCity = true
            // the city picker is a home screen overlay, so leave Customize to show it
            ui.showSettings = false
        }
    }
    ActionRow("Reset this widget's look") {
        s.updateWidget(spec.copy(frost = -1f, tint = 0, corner = -1f, text = 0))
    }
    ActionRow("Remove this widget", destructive = true) {
        s.removeWidget(spec.id)
        ui.relayout += 1
    }
}
