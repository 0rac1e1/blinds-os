@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val GLASS_TINTS = listOf(
    0xFFFFFFFF.toInt(), 0xFFCFE8FF.toInt(), 0xFFFFD1E0.toInt(), 0xFFFFE0A6.toInt(),
    0xFFC8F5E1.toInt(), 0xFFDCCBFF.toInt(), 0xFF1B1B22.toInt(),
)
private val ACCENTS = listOf(
    0xFF0A84FF.toInt(), 0xFF64D2FF.toInt(), 0xFF30D158.toInt(), 0xFFFFD60A.toInt(),
    0xFFFF9F0A.toInt(), 0xFFFF453A.toInt(), 0xFFFF375F.toInt(), 0xFFBF5AF2.toInt(),
)

@Composable
fun SettingsSheet(model: AppModel, ui: UiState) {
    BackHandler(enabled = ui.showSettings) { ui.showSettings = false }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = ui.showSettings, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { ui.showSettings = false }) }
            )
        }
        AnimatedVisibility(
            visible = ui.showSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 300f)) { it },
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SheetBody(model = model, ui = ui)
        }
    }
}

@Composable
private fun SheetBody(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveWallpaper(ctx, uri) }
                if (ok) {
                    s.wallpaperVersion += 1
                    s.useCustomWallpaper = true
                }
            }
        }
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.68f).swallowTaps(),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        strength = 1.5f,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.3f)))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("Customize", size = 24.sp, weight = FontWeight.Bold)
                GlassButton("Done", onClick = { ui.showSettings = false }, accent = true)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Group("Liquid glass") {
                    SliderRow("Blur", "${s.glassBlur.roundToInt()}", s.glassBlur, 0f..60f) { s.glassBlur = it }
                    SliderRow("Frost", pct(s.glassTint), s.glassTint, 0f..0.6f) { s.glassTint = it }
                    SwatchRow("Tint", GLASS_TINTS, s.glassTintArgb) { s.glassTintArgb = it }
                    SliderRow("Edge light", pct(s.glassEdge), s.glassEdge, 0f..1f) { s.glassEdge = it }
                    SliderRow("Depth", pct(s.glassDepth), s.glassDepth, 0f..1f) { s.glassDepth = it }
                    SliderRow("Light angle", "${s.glassLight.roundToInt()}°", s.glassLight, 0f..360f) { s.glassLight = it }
                    SwitchRow("Light follows tilt", s.motionLight) { s.motionLight = it }
                }

                Group("Icons") {
                    ChoiceRow(
                        "Shape",
                        listOf("squircle" to "Squircle", "circle" to "Circle", "rounded" to "Rounded", "square" to "Square"),
                        s.iconShape,
                    ) { s.iconShape = it }
                    SliderRow("Size", "${s.iconSize.roundToInt()}dp", s.iconSize, 44f..76f) { s.iconSize = it }
                    SwitchRow("Glossy shine", s.iconGlass) { s.iconGlass = it }
                    SwitchRow("Themed icons (Android 13+)", s.themedIcons) { s.themedIcons = it }
                    SwitchRow("Show app names", s.showLabels) { s.showLabels = it }
                }

                Group("Home screen") {
                    StepperRow("Columns", s.cols, 3..6) { s.cols = it }
                    StepperRow("Rows", s.rows, 4..8) { s.rows = it }
                    StepperRow("Dock apps", s.dockCount, 3..5) {
                        s.dockCount = it
                        s.normalizeDock()
                    }
                    SliderRow("Corner radius", "${s.cornerRadius.roundToInt()}dp", s.cornerRadius, 12f..44f) { s.cornerRadius = it }
                    SwitchRow("Clock widget", s.clockWidget) { s.clockWidget = it }
                    ChoiceRow("Clock style", listOf("card" to "Glass card", "bare" to "No card"), s.clockStyle) { s.clockStyle = it }
                    SwitchRow("App Library page", s.appLibrary) { s.appLibrary = it }
                }

                Group("Wallpaper") {
                    WallpaperRow(selected = if (s.useCustomWallpaper) "" else s.wallpaper) {
                        s.wallpaper = it
                        s.useCustomWallpaper = false
                    }
                    ActionRow(if (s.wallpaperVersion > 0) "Choose a different photo…" else "Choose a photo…") {
                        picker.launch("image/*")
                    }
                    if (s.wallpaperVersion > 0) {
                        SwitchRow("Use my photo", s.useCustomWallpaper) { s.useCustomWallpaper = it }
                    }
                    SwitchRow("Animated wallpaper", s.animatedWallpaper) { s.animatedWallpaper = it }
                    SliderRow("Parallax", pct(s.parallax), s.parallax, 0f..1f) { s.parallax = it }
                }

                Group("Appearance") {
                    ChoiceRow("Theme", listOf("system" to "System", "dark" to "Dark", "light" to "Light"), s.theme) { s.theme = it }
                    SwatchRow("Accent", ACCENTS, s.accent) { s.accent = it }
                }

                Group("Behaviour") {
                    SwitchRow("Haptic feedback", s.haptics) { s.haptics = it }
                    SwitchRow("Swipe down for search", s.swipeSearch) { s.swipeSearch = it }
                }

                Group("System") {
                    ActionRow("About Blinds OS and patch notes") {
                        ui.showSettings = false
                        AboutState.show = true
                    }
                    ActionRow("Set Blinds OS as the default home app") { openHomeSettings(ctx) }
                    if (s.hiddenIds.isNotEmpty()) {
                        ActionRow("Show ${s.hiddenIds.size} hidden app(s) again") { s.unhideAll() }
                    }
                    ActionRow("Reset glass and appearance") { s.resetAppearance() }
                    ActionRow("Rebuild home screen layout", destructive = true) {
                        s.resetLayout()
                        model.refresh()
                    }
                }

                Txt(
                    "Blinds OS ${BlindsVersion.NAME}",
                    color = ink.copy(alpha = 0.45f),
                    size = 12.sp,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(28.dp))
            }
        }
    }
}

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

private fun openHomeSettings(ctx: Context) {
    val tries = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (i in tries) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Little building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInk.current
    Txt(
        title.uppercase(),
        color = ink.copy(alpha = 0.55f),
        size = 12.sp,
        weight = FontWeight.SemiBold,
        spacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ink.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(label, size = 16.sp)
            Txt(valueText, size = 14.sp, color = ink.copy(alpha = 0.6f))
        }
        GlassSlider(value = value, onChange = onChange, range = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, size = 16.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        GlassSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Txt(label, size = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, text) ->
                val on = id == selected
                Txt(
                    text,
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = if (on) Color.White else ink,
                    modifier = Modifier
                        .bouncy(onClick = { onSelect(id) }, pressedScale = 0.95f)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color(s.accent) else ink.copy(alpha = 0.10f))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun SwatchRow(label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Txt(label, size = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c ->
                val on = c == selected
                Box(
                    Modifier
                        .size(36.dp)
                        .bouncy(onClick = { onSelect(c) }, pressedScale = 0.9f)
                        .then(if (on) Modifier.border(2.5.dp, ink, CircleShape) else Modifier.border(1.dp, ink.copy(alpha = 0.2f), CircleShape))
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                )
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, size = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniButton("−") { if (value > range.first) onChange(value - 1) }
            Txt("$value", size = 16.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = Modifier.width(36.dp))
            MiniButton("+") { if (value < range.last) onChange(value + 1) }
        }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .size(34.dp)
            .bouncy(onClick = onClick, pressedScale = 0.88f)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, size = 20.sp, weight = FontWeight.Medium)
    }
}

@Composable
private fun ActionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Txt(
        label,
        size = 16.sp,
        color = if (destructive) Color(0xFFFF453A) else LocalSettings.current.accent.let { Color(it) },
        modifier = Modifier
            .fillMaxWidth()
            .bouncy(onClick = onClick, pressedScale = 0.98f)
            .padding(vertical = 15.dp),
    )
}

@Composable
private fun WallpaperRow(selected: String, onSelect: (String) -> Unit) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WALLPAPERS.forEach { wp ->
            val on = wp.id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 62.dp, height = 100.dp)
                        .bouncy(onClick = { onSelect(wp.id) }, pressedScale = 0.93f)
                        .then(if (on) Modifier.border(2.5.dp, Color(s.accent), RoundedCornerShape(16.dp)) else Modifier)
                        .padding(if (on) 3.dp else 0.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .drawBehind {
                            drawRect(Brush.linearGradient(wp.base, start = Offset.Zero, end = Offset(size.width, size.height)))
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(wp.blobs[0].copy(alpha = 0.9f), Color.Transparent),
                                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                                    radius = size.width,
                                ),
                                radius = size.width,
                                center = Offset(size.width * 0.3f, size.height * 0.3f),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(wp.blobs[1].copy(alpha = 0.85f), Color.Transparent),
                                    center = Offset(size.width * 0.75f, size.height * 0.75f),
                                    radius = size.width,
                                ),
                                radius = size.width,
                                center = Offset(size.width * 0.75f, size.height * 0.75f),
                            )
                        }
                )
                Txt(wp.name, size = 12.sp, color = ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
