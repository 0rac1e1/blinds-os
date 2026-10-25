@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.app.WallpaperManager
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Whether the built-in Customize app is open. */
object CustomizeState {
    var show by mutableStateOf(false)

    /** Which section is open, or null for the section list. */
    var section by mutableStateOf<String?>(null)

    /** A widget to open first in the Widgets section (set by the Edit button on a widget). */
    var focusWidget: String? = null
}

private class SectionInfo(val id: String, val title: String, val blurb: String)

private val SECTIONS = listOf(
    SectionInfo("wallpaper", "Wallpaper", "Backgrounds, live effects, lock screen"),
    SectionInfo("icons", "App icons", "Shape, size, glass style, icon packs"),
    SectionInfo("glass", "Liquid glass", "Blur, frost, tint, light"),
    SectionInfo("home", "Home screen", "Grid size, dock, corners, App Library"),
    SectionInfo("apps", "Apps", "Rename an app or give it its own icon picture"),
    SectionInfo("anim", "Animations", "Style of motion, battery saver"),
    SectionInfo("widgets", "Widgets", "Look of each widget: style, frost, colours, city"),
    SectionInfo("badges", "Notification badges", "Counts and dots on icons"),
    SectionInfo("look", "Appearance and behaviour", "Theme, accent, haptics, search"),
    SectionInfo("system", "System", "About, backup, default home app, reset"),
)

private val SECTION_WORDS = mapOf(
    "wallpaper" to "wallpaper background live effect lock screen parallax photo drift speed fps old",
    "icons" to "icon shape size style glass pack names labels themed squircle circle",
    "glass" to "glass blur frost tint edge depth light transparent see-through clear",
    "home" to "grid columns rows dock corner radius library folder look page",
    "apps" to "rename name custom icon override picture app",
    "anim" to "animation motion bouncy snappy smooth speed",
    "widgets" to "widget clock weather battery calendar date city look",
    "badges" to "notification center badge dot count swipe",
    "look" to "theme dark light accent haptics vibration search swipe behaviour",
    "system" to "backup restore reset default home app about update",
)

/** The built-in Customize app: a full-screen glass app inside the launcher, like the Blinds OS app. */
@Composable
fun SettingsSheet(model: AppModel, ui: UiState) {
    AppShell(CustomizeState.show) { CustomizeContent(model, ui) }
}

@Composable
private fun CustomizeContent(model: AppModel, ui: UiState) {
    BackHandler(enabled = CustomizeState.show) {
        if (CustomizeState.section != null) CustomizeState.section = null else CustomizeState.show = false
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        SheetBody(model = model, ui = ui)
    }
}

@Composable
private fun SheetBody(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalDark.current

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

    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Backup.export(ctx, uri) }
                Toast.makeText(ctx, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
                if (ok) {
                    Toast.makeText(ctx, "Backup restored. Add real widgets from other apps again.", Toast.LENGTH_LONG).show()
                    ctx.findActivity()?.recreate()
                } else {
                    Toast.makeText(ctx, "That isn't a Blinds OS backup", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    run {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val open = SECTIONS.firstOrNull { it.id == CustomizeState.section }
                Txt(
                    open?.title ?: "Customize",
                    size = 24.sp,
                    weight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                if (open != null) {
                    GlassButton("Back", onClick = { CustomizeState.section = null }, accent = true)
                } else {
                    GlassButton("Done", onClick = { ui.showSettings = false }, accent = true)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (CustomizeState.section == null) {
                    var query by remember { mutableStateOf("") }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(s.accent)),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Txt("Search settings…", color = ink.copy(alpha = 0.45f), size = 16.sp)
                            inner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ink.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    val q = query.trim().lowercase()
                    val shownSections = if (q.isEmpty()) SECTIONS else SECTIONS.filter {
                        (it.title + " " + it.blurb + " " + (SECTION_WORDS[it.id] ?: "")).lowercase().contains(q)
                    }
                    shownSections.forEach { sec ->
                        SectionRow(sec.title, sec.blurb) { CustomizeState.section = sec.id }
                    }
                    if (shownSections.isEmpty()) {
                        Txt("Nothing matches \"$query\".", color = ink.copy(alpha = 0.6f), size = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                    }
                } else when (CustomizeState.section) {
                "wallpaper" -> {
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
                    ActionRow("Surprise me (random wallpaper)") {
                        val pick = WALLPAPERS.filter { it.id != s.wallpaper || s.useCustomWallpaper }.random()
                        s.wallpaper = pick.id
                        s.useCustomWallpaper = false
                    }
                    SwitchRow("Animated wallpaper", s.animatedWallpaper) { s.animatedWallpaper = it }
                    ChoiceRow("Live effect", LIVE_EFFECTS, s.wallpaperLive) { s.wallpaperLive = it }
                    SliderRow("Drift speed", "${"%.1f".format(s.wallpaperSpeed)}x", s.wallpaperSpeed, 0.2f..2.5f) { s.wallpaperSpeed = it }
                    ChoiceRow("Animation frame rate", listOf("15" to "15 fps", "30" to "30 fps", "60" to "60 fps"), s.wallpaperFps.toString()) { s.wallpaperFps = it.toInt() }
                    SwitchRow("Pause animation in battery saver", s.pauseSaver) { s.pauseSaver = it }
                    SliderRow("Parallax", pct(s.parallax), s.parallax, 0f..1f) { s.parallax = it }

                    ActionRow("Use my old home wallpaper") {
                        if (SystemWallpaper.importCurrent(ctx, WallpaperManager.FLAG_SYSTEM)) {
                            s.wallpaperVersion += 1
                            s.useCustomWallpaper = true
                            Toast.makeText(ctx, "Copied your old wallpaper", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "Android didn't let me read it. Choose the photo instead.", Toast.LENGTH_LONG).show()
                            picker.launch("image/*")
                        }
                    }
                    ActionRow("Use my old lock screen wallpaper") {
                        if (SystemWallpaper.importCurrent(ctx, WallpaperManager.FLAG_LOCK)) {
                            s.wallpaperVersion += 1
                            s.useCustomWallpaper = true
                            Toast.makeText(ctx, "Copied your lock screen wallpaper", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "Android didn't let me read it. Choose the photo instead.", Toast.LENGTH_LONG).show()
                            picker.launch("image/*")
                        }
                    }
                    ActionRow("Set this wallpaper on my lock screen") {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                SystemWallpaper.setLock(ctx, SystemWallpaper.render(ctx, s, dark))
                            }
                            Toast.makeText(
                                ctx,
                                if (ok) "Lock screen updated" else "Couldn't change the lock screen",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    Txt(
                        "The lock screen gets a still picture of your wallpaper (live effects can't run there). Copying your old wallpaper doesn't work with every Samsung or live wallpaper; if so, pick the photo yourself. Setting the lock screen replaces your current one.",
                        size = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                }
                "icons" -> {
                Group("Icons") {
                    ChoiceRow(
                        "Shape",
                        listOf("squircle" to "Squircle", "circle" to "Circle", "rounded" to "Rounded", "square" to "Square"),
                        s.iconShape,
                    ) { s.iconShape = it }
                    SliderRow("Size", "${s.iconSize.roundToInt()}dp", s.iconSize, 44f..76f) { s.iconSize = it }
                    ChoiceRow(
                        "Style",
                        listOf("default" to "Default", "glass" to "Glass", "clear" to "Clear", "tinted" to "Tinted"),
                        s.iconStyle,
                    ) { s.iconStyle = it }
                    if (s.iconStyle != "default") {
                        ChoiceRow(
                            "Glass quality",
                            listOf("off" to "Off (flat)", "light" to "Light (fast)", "full" to "Full (photo blur, slower)"),
                            s.iconQuality,
                        ) { s.iconQuality = it }
                        SliderRow("Frost", pct(s.iconFrost), s.iconFrost, 0f..0.6f) { s.iconFrost = it }
                        SliderRow("Shine", pct(s.iconShine), s.iconShine, 0f..1f) { s.iconShine = it }
                        SliderRow("Edge", pct(s.iconEdge), s.iconEdge, 0f..1f) { s.iconEdge = it }
                        SliderRow("Tint amount", pct(s.iconTintAmt), s.iconTintAmt, 0f..1f) { s.iconTintAmt = it }
                        SwitchRow("Dark glass", s.iconDarkGlass) { s.iconDarkGlass = it }
                    } else {
                        SwitchRow("Glossy shine", s.iconGlass) { s.iconGlass = it }
                    }
                    SwitchRow("Themed icons (Android 13+)", s.themedIcons) { s.themedIcons = it }
                    SwitchRow("Show app names", s.showLabels) { s.showLabels = it }
                }
                Group("Icon pack") {
                    val packs = remember { IconPacks.installed(ctx) }
                    ChoiceRow("Pack", listOf("" to "None (normal icons)") + packs.map { it.pkg to it.label }, s.iconPack) { s.iconPack = it }
                    Txt(
                        if (packs.isEmpty()) "No icon packs found. Install a Nova / ADW style icon pack from the Play Store and it shows up here. Apps the pack does not cover keep their normal icon."
                        else "Apps the pack does not cover keep their normal icon.",
                        size = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                }
                "glass" -> {
                Group("Liquid glass") {
                    SliderRow("Blur", "${s.glassBlur.roundToInt()}", s.glassBlur, 0f..60f) { s.glassBlur = it }
                    SliderRow("Frost", pct(s.glassTint), s.glassTint, 0f..0.6f) { s.glassTint = it }
                    SliderRow("See-through", pct(s.glassClarity), s.glassClarity, 0f..1f) { s.glassClarity = it }
                    SwatchRow("Tint", GLASS_TINTS, s.glassTintArgb) { s.glassTintArgb = it }
                    SliderRow("Edge light", pct(s.glassEdge), s.glassEdge, 0f..1f) { s.glassEdge = it }
                    SliderRow("Depth", pct(s.glassDepth), s.glassDepth, 0f..1f) { s.glassDepth = it }
                    SliderRow("Light angle", "${s.glassLight.roundToInt()}°", s.glassLight, 0f..360f) { s.glassLight = it }
                    SwitchRow("Light follows tilt", s.motionLight) { s.motionLight = it }
                }
                }
                "home" -> {
                Group("Home screen") {
                    StepperRow("Columns", s.cols, 3..6) { s.cols = it }
                    StepperRow("Rows", s.rows, 4..8) { s.rows = it }
                    StepperRow("Dock apps", s.dockCount, 3..5) {
                        s.dockCount = it
                        s.normalizeDock()
                    }
                    SliderRow("Corner radius", "${s.cornerRadius.roundToInt()}dp", s.cornerRadius, 12f..44f) { s.cornerRadius = it }
                    SwitchRow("App Library page", s.appLibrary) { s.appLibrary = it }
                }
                Group("Folders") {
                    ChoiceRow(
                        "Look",
                        listOf("glass" to "Glass", "clear" to "Clear", "tinted" to "Tinted", "dark" to "Dark"),
                        s.folderStyle,
                    ) { s.folderStyle = it }
                    ChoiceRow("Apps per row when open", listOf("3" to "3", "4" to "4", "5" to "5"), s.folderCols.toString()) { s.folderCols = it.toInt() }
                }
                }
                "apps" -> {
                    AppOverridesSection(model)
                }
                "anim" -> {
                Group("Animations") {
                    ChoiceRow(
                        "Style",
                        listOf("smooth" to "Smooth", "bouncy" to "Bouncy", "snappy" to "Snappy", "off" to "Off"),
                        s.animStyle,
                    ) { s.animStyle = it }
                    SwitchRow("Turn off in battery saver", s.animSaver) { s.animSaver = it }
                    Txt(
                        "Controls folders opening and closing, items gliding into place, the edit buttons and the Notification Center. If Android's \"Remove animations\" is on, Blinds OS follows it.",
                        size = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                }
                "widgets" -> {
                    val list = s.widgets
                    var openId by remember { mutableStateOf(CustomizeState.focusWidget) }
                    if (list.isEmpty()) {
                        Txt(
                            "You have no widgets yet. Hold the home screen, tap + Widget, and pick one.",
                            size = 14.sp,
                            color = ink.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                    list.forEach { w ->
                        if (openId == w.id) {
                            Group(widgetTitle(w, list)) {
                                WidgetLookControls(w, ui)
                                ActionRow("Close") { openId = null }
                            }
                        } else {
                            SectionRow(widgetTitle(w, list), "Tap to edit") { openId = w.id }
                        }
                    }
                }
                "badges" -> {
                Group("Notification Center") {
                    SwitchRow("Glass Notification Center", s.notifCenter) {
                        s.notifCenter = it
                        NotifCenter.collecting = it
                        NotifCenter.service?.refresh()
                    }
                    Txt(
                        "Swipe down on the right half of the home screen to open it. Needs notification access. Titles and text are only held in memory while it's on, never saved. Swiping down on the left half still opens search.",
                        size = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Group("Notification badges") {
                    ChoiceRow("Style", listOf("number" to "Number", "dot" to "Dot", "off" to "Off"), s.badgeStyle) { s.badgeStyle = it }
                    SwatchRow("Colour", BADGE_COLORS, s.badgeColor) { s.badgeColor = it }
                    ActionRow(if (Badges.enabled) "Notification access is on (tap to manage)" else "Turn on notification access…") {
                        openNotificationAccess(ctx)
                    }
                    ActionRow("App info (restricted settings, battery)…") { openAppInfo(ctx) }
                    Txt(
                        "Badges only count notifications per app and never read their text. If the access switch is greyed out, open App info, tap the three dots and choose Allow restricted settings. If badges stop updating, set Battery to Unrestricted there.",
                        size = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                }
                "look" -> {
                Group("Appearance") {
                    ChoiceRow("Theme", listOf("system" to "System", "dark" to "Dark", "light" to "Light"), s.theme) { s.theme = it }
                    SwatchRow("Accent", ACCENTS, s.accent) { s.accent = it }
                }
                Group("Behaviour") {
                    SwitchRow("Haptic feedback", s.haptics) { s.haptics = it }
                    SwitchRow("Swipe down for search", s.swipeSearch) { s.swipeSearch = it }
                }
                }
                "system" -> {
                Group("System") {
                    ActionRow("About Blinds OS and patch notes") {
                        CustomizeState.section = null
                        ui.showSettings = false
                        AboutState.show = true
                    }
                    ActionRow("Set Blinds OS as the default home app") { openHomeSettings(ctx) }
                    ActionRow("Back up layout and settings…") { backupSaver.launch("blinds-os-backup.json") }
                    ActionRow("Restore from a backup…") { backupOpener.launch(arrayOf("*/*")) }
                    if (s.hiddenIds.isNotEmpty()) {
                        ActionRow("Show ${s.hiddenIds.size} hidden app(s) again") { s.unhideAll() }
                    }
                    ActionRow("Reset glass and appearance") { s.resetAppearance() }
                    ActionRow("Rebuild home screen layout", destructive = true) {
                        s.resetLayout()
                        model.refresh()
                    }
                }
                }
                    else -> {}
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

private val BADGE_COLORS = listOf(
    0xFFFF3B30.toInt(), 0xFFFF9F0A.toInt(), 0xFF30D158.toInt(), 0xFF0A84FF.toInt(),
    0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFF111111.toInt(),
)

internal fun openNotificationAccess(ctx: Context) {
    try {
        val i = Intent(
            if (Build.VERSION.SDK_INT >= 30) Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
            else Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= 30) {
            i.putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, BadgeService::class.java).flattenToString(),
            )
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            // no settings screen available
        }
    }
}

private fun openAppInfo(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // no settings screen available
    }
}

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

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
private fun SectionRow(title: String, blurb: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ink.copy(alpha = 0.08f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 17.sp, weight = FontWeight.SemiBold)
            Txt(blurb, size = 12.sp, color = ink.copy(alpha = 0.6f))
        }
        Txt("›", size = 24.sp, color = ink.copy(alpha = 0.5f))
    }
}

@Composable
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
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
fun SliderRow(
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
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
fun ChoiceRow(
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
fun SwatchRow(label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
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
fun ActionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
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
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      WALLPAPERS.chunked(4).forEach { rowList ->
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rowList.forEach { wp ->
            val on = wp.id == selected
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.62f)
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
                Txt(wp.name, size = 11.sp, color = ink.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(top = 5.dp))
            }
        }
        repeat(4 - rowList.size) { Box(Modifier.weight(1f)) }
       }
      }
    }
}
