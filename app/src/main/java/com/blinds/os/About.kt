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
    const val NAME = "26.3.3"
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
        version = "27.0",
        title = "First release of 2027",
        date = "Planned, not final",
        planned = true,
        items = listOf(
            "Real Android widgets",
            "Icon packs",
        ),
    ),
    PatchNote(
        version = "26.4",
        title = "Move icons around",
        date = "Planned, not final",
        planned = true,
        items = listOf(
            "Drag icons to move them (in 26.3.2)",
            "Free home screen grid",
            "Clock and weather widgets you can customize and drag",
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
    val own = model.apps.firstOrNull { it.pkg == ctx.packageName }
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
                        if (own != null) AppIcon(own, 84.dp)
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
