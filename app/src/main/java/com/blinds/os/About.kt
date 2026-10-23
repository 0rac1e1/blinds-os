package com.blinds.os

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Versioning: YY.N. The first number is the year, the second counts releases within that year,
 * so 27.0 is the first release of 2027.
 */
object BlindsVersion {
    const val NAME = "27.1"
}

class PatchNote(
    val version: String,
    val title: String,
    val date: String,
    val items: List<String>,
    val planned: Boolean = false,
)

val PATCH_NOTES = listOf(
    PatchNote(
        version = "27.1",
        title = "Widgets picker, animations, Notification Center",
        date = "October 2026",
        items = listOf(
            "New widget picker with categories (Weather, Time, Battery, Date, Calendar) and live previews",
            "New widgets: battery, date strip, next calendar event (asks for calendar permission), second city on the clock",
            "Widget looks are edited in Customize > Widgets",
            "Animation system: folders open and close with motion, items glide to their new spot, edit buttons slide in. Pick Smooth, Bouncy, Snappy or Off in Customize > Animations",
            "Glass Notification Center: swipe down on the right half of the home screen (turn on in Customize > Notification badges)",
            "Folders: drag an app out of an open folder, put folders in the dock, choose a folder look and size",
            "Customize > Apps: rename any app or give it your own icon picture",
            "10 new wallpapers",
            "Fixes: Customize icon on the home screen, real widgets showing Can't load widget",
        ),
    ),
    PatchNote(
        version = "27.0.1",
        title = "Cleaner Customize, lock screen sync",
        date = "October 2026",
        items = listOf(
            "Customize is split into sections (Wallpaper, App icons, Liquid glass, Home screen, Notification badges, Appearance, System)",
            "Wallpaper: set your Blinds wallpaper on the lock screen, or copy your old home or lock screen wallpaper in (works when Android allows it)",
            "Customize has a new paintbrush icon",
            "Fixed the Blinds OS logo sometimes showing another app's icon; the logo is now built in",
            "Edit mode buttons moved above the grid so they no longer cover the remove buttons on the top row",
        ),
    ),
    PatchNote(
        version = "27.0",
        title = "Place anything, real widgets, icon packs",
        date = "October 2026",
        items = listOf(
            "Free placement: drop icons, folders and widgets on any grid cell, on any page (hold a dragged item at the edge to reach a new page)",
            "Resize widgets by dragging the corner handle while editing; other things move out of the way",
            "Real Android widgets from other apps: + Widget > Widgets from your apps",
            "Icon packs: Customize > Icon pack picks any installed Nova / ADW style pack",
            "Customize is its own app, and holding the home screen is for arranging",
        ),
    ),
    PatchNote(
        version = "26.5.1",
        title = "Customize app",
        date = "October 2026",
        items = listOf(
            "Customize is now its own app on your home screen (next to the Blinds OS app) instead of a pop-up sheet, and opens full screen",
            "Customize still opens from the Customize button while editing the home screen",
        ),
    ),
    PatchNote(
        version = "26.5",
        title = "Folders, badges and wallpapers",
        date = "October 2026",
        items = listOf(
            "Notification badges: a count (or dot) on app icons and folders. Turn on notification access in Customize > Notification badges. Only counts notifications, never reads them",
            "Folders: hold one app over another in edit mode, rename, open, take apps out",
            "9 new wallpapers and 7 live effects (rain, snow, night sky, waves, lava lamp, fireflies, aurora curtains), with speed, frame rate and battery saver controls",
            "Faster start-up: the home screen appears right away",
        ),
    ),
    PatchNote(
        version = "26.4.3",
        title = "Folders",
        date = "October 2026",
        items = listOf(
            "Folders: in edit mode, drag an app and hold it over the middle of another app (or a folder) until it grows, then let go",
            "Tap a folder to open it: rename it at the top, tap an app to open it, press and hold an app to take it out",
            "Folders can be moved and dragged between pages like icons; the - badge or Remove folder puts the apps back",
            "Dragging now waits a moment over an icon before swapping places, so it is calmer",
        ),
    ),
    PatchNote(
        version = "26.4.2",
        title = "Wallpapers",
        date = "October 2026",
        items = listOf(
            "9 new wallpapers: Time of day (follows your clock), Sunset, Midnight, Peach, Mint, Lavender, Rose Gold, Slate, Neon, plus a Surprise me button",
            "Live effects over any wallpaper: Aurora curtains, Night sky with shooting stars, Rain, Snow, Waves, Lava lamp, Fireflies",
            "Drift speed, animation frame rate (15 / 30 / 60) and pause in battery saver",
        ),
    ),
    PatchNote(
        version = "26.4.1",
        title = "Faster start",
        date = "October 2026",
        items = listOf(
            "Home screen appears right away: the app list is remembered between launches",
            "Dock and first-page icons are prepared in the background before they are drawn",
            "Startup profile included, so the launcher code is pre-compiled on your phone and opens faster (the speed-up grows after the first launch or two)",
        ),
    ),
    PatchNote(
        version = "26.4",
        title = "Widgets and moving things",
        date = "October 2026",
        items = listOf(
            "Weather widget: now, next hours and 5 days, with icons, a city search and F / C (data by Open-Meteo.com)",
            "Customize each widget: size, style, frost, tint, corner roundness and text colour (+ Widget, then Edit)",
            "Clock widgets: digital, analog or no card, small / wide / large",
            "Drag icons and widgets to move them between spots, pages and the dock",
        ),
    ),
    PatchNote(
        version = "26.3.3",
        title = "Widgets begin",
        date = "October 2026",
        items = listOf(
            "Widgets: tap + Widget while editing to add a clock (digital, analog or no card)",
            "Widgets are items on the home screen: drag them like icons, use Size to switch small / wide / large",
            "The old clock card is now a regular clock widget and can be removed or moved",
            "Weather widget comes next",
        ),
    ),
    PatchNote(
        version = "26.3.2",
        title = "Drag to move",
        date = "October 2026",
        items = listOf(
            "In edit mode, press and hold an icon, then drag it to a new spot, another page or the dock",
            "Hold a dragged icon at the screen edge to flip pages",
        ),
    ),
    PatchNote(
        version = "26.3.1",
        title = "Glass icon fixes",
        date = "October 2026",
        items = listOf(
            "Glass icons are now see-through: the coloured background becomes glass and only the logo stays solid",
            "Glass icons are much lighter on the phone, so the home screen is smoother",
        ),
    ),
    PatchNote(
        version = "26.3",
        title = "Glass icons and backup",
        date = "October 2026",
        items = listOf(
            "Liquid glass icons: Glass, Clear and Tinted styles with frost, shine, edge and tint sliders (Customize > Icons > Style)",
            "Back up and restore your layout and settings to a file (Customize > System)",
        ),
    ),
    PatchNote(
        version = "26.2",
        title = "The Blinds OS app",
        date = "October 2026",
        items = listOf(
            "New Blinds OS app with version, patch notes and device info",
            "Software update check: see when a new version is out and download it in one tap",
            "Icons from older apps now fill the whole icon shape instead of sitting on a white plate",
            "Dock now picks up Phone, Messages, Browser and Camera on Samsung phones",
        ),
    ),
    PatchNote(
        version = "26.1",
        title = "Fixes",
        date = "October 2026",
        items = listOf(
            "Fixed the Customize and Done buttons in edit mode",
            "Fixed icon and glass edge drawing",
        ),
    ),
    PatchNote(
        version = "26.0",
        title = "First release",
        date = "October 2026",
        items = listOf(
            "Liquid glass on the dock, clock, menus, search and settings, with tilt-reactive lighting",
            "iOS-style home screen with pages, dock, App Library and Spotlight search",
            "Edit mode, long-press quick actions and hidden apps",
            "Customize sheet: icon shapes, sizes, themed icons, grid, wallpapers, accent and more",
        ),
    ),
)

const val UPDATE_REPO = "0rac1e1/blinds-os"

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object Latest : UpdateState()
    object Failed : UpdateState()
    class Available(val version: String, val url: String) : UpdateState()
}

/** Whether the Blinds OS app is open. A global so any icon (home, dock, library, search) can open it. */
object AboutState {
    var show by mutableStateOf(false)
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
}

/** True when version a (like "27.0" or "v26.3") is newer than version b. */
private fun versionNewer(a: String, b: String): Boolean {
    val pa = a.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val pb = b.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Asks GitHub for the newest Blinds OS release and compares it with the installed version. */
private suspend fun fetchLatest(): UpdateState = withContext(Dispatchers.IO) {
    try {
        val conn = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode != 200) {
            UpdateState.Failed
        } else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            var url = json.optString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.getString("browser_download_url")
                        break
                    }
                }
            }
            if (versionNewer(tag, BlindsVersion.NAME)) {
                UpdateState.Available(tag.removePrefix("v"), url)
            } else {
                UpdateState.Latest
            }
        }
    } catch (e: Exception) {
        UpdateState.Failed
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // no browser available
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(label, color = ink.copy(alpha = 0.65f), size = 15.sp)
        Spacer(Modifier.width(16.dp))
        Txt(value, size = 15.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
    }
}

/** The Blinds OS logo, drawn from bundled artwork (never from an installed app's icon). */
@Composable
fun BlindsLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF1B3FA0), Color(0xFF6A2C9E), Color(0xFFD02A44))
                )
            ),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Blinds OS",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun BlindsAboutApp(model: AppModel) {
    if (!AboutState.show) return
    BackHandler { AboutState.show = false }

    val ctx = LocalContext.current
    val ink = LocalInk.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    fun checkUpdates() {
        AboutState.update = UpdateState.Checking
        scope.launch { AboutState.update = fetchLatest() }
    }
    LaunchedEffect(Unit) {
        if (AboutState.update == UpdateState.Idle) checkUpdates()
    }
    val isDefault = remember {
        try {
            val r = ctx.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            r?.activityInfo?.packageName == ctx.packageName
        } catch (e: Exception) {
            false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("Blinds OS", size = 30.sp, weight = FontWeight.Bold, shadow = true)
                    GlassButton("Done", onClick = { AboutState.show = false }, accent = true)
                }
            }

            item {
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(30.dp), strength = 1.2f) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BlindsLogo(84.dp)
                        Spacer(Modifier.height(14.dp))
                        Txt("Blinds OS", size = 26.sp, weight = FontWeight.Bold)
                        Txt("Version ${BlindsVersion.NAME}", color = ink.copy(alpha = 0.7f), size = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            if (isDefault) "Your home screen" else "Not your default home app yet",
                            color = ink.copy(alpha = 0.55f),
                            size = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Txt("Made by Studio 291", color = ink.copy(alpha = 0.55f), size = 13.sp)
                    }
                }
            }

            item {
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), strength = 1f) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        val u = AboutState.update
                        if (u is UpdateState.Available) {
                            Txt("Update available", size = 18.sp, weight = FontWeight.Bold)
                            Txt(
                                "Blinds OS ${u.version}",
                                color = ink.copy(alpha = 0.7f),
                                size = 15.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                            )
                            GlassButton("Download update", onClick = { openUrl(ctx, u.url) }, accent = true)
                            Txt(
                                "When it finishes, open the file and tap Update. Your settings are kept.",
                                color = ink.copy(alpha = 0.55f),
                                size = 12.sp,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        } else {
                            val msg = when (u) {
                                UpdateState.Checking -> "Checking for updates…"
                                UpdateState.Latest -> "Blinds OS is up to date"
                                UpdateState.Failed -> "Couldn't check for updates"
                                else -> "Software update"
                            }
                            Txt(msg, size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            GlassButton("Check for updates", onClick = { checkUpdates() })
                        }
                    }
                }
            }

            item {
                Txt(
                    "Patch notes",
                    size = 20.sp,
                    weight = FontWeight.Bold,
                    shadow = true,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            items(PATCH_NOTES, key = { it.version }) { note ->
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), strength = 1f) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(note.version, size = 20.sp, weight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            if (note.version == BlindsVersion.NAME) {
                                Txt("CURRENT", color = Color(s.accent), size = 11.sp, weight = FontWeight.Bold, spacing = 1.sp)
                            } else if (note.planned) {
                                Txt("UPCOMING", color = ink.copy(alpha = 0.6f), size = 11.sp, weight = FontWeight.Bold, spacing = 1.sp)
                            }
                        }
                        Txt(note.title, size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                        Txt(note.date, color = ink.copy(alpha = 0.55f), size = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        note.items.forEach { line ->
                            Txt(
                                "•  $line",
                                size = 14.sp,
                                color = ink.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            item {
                Txt(
                    "About this device",
                    size = 20.sp,
                    weight = FontWeight.Bold,
                    shadow = true,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            item {
                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), strength = 1f) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        InfoRow("Blinds OS", BlindsVersion.NAME)
                        InfoRow("Weather data", "Open-Meteo.com")
                        InfoRow("Device", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
                        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        InfoRow("Apps", model.apps.size.toString())
                        InfoRow(
                            "Glass blur",
                            if (Build.VERSION.SDK_INT >= 31) "Supported" else "Needs Android 12+",
                        )
                        InfoRow("Home app", if (isDefault) "Blinds OS" else "Another launcher")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
