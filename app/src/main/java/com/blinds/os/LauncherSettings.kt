package com.blinds.os

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** One persisted, Compose-observable setting. Reading it inside a composable subscribes to changes. */
internal class Pref<T : Any>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {

    private var state by mutableStateOf(load())

    @Suppress("UNCHECKED_CAST")
    private fun load(): T = when (default) {
        is Boolean -> prefs.getBoolean(key, default)
        is Int -> prefs.getInt(key, default)
        is Float -> prefs.getFloat(key, default)
        is String -> prefs.getString(key, default) ?: default
        else -> default
    } as T

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state = value
        val e = prefs.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }

    fun reset() {
        state = default
        prefs.edit().remove(key).apply()
    }
}

/**
 * Every customization option in Blinds OS lives here so the settings sheet,
 * the glass engine and the home screen all read from one place.
 */
class LauncherSettings(private val prefs: SharedPreferences) {

    private val resettable = mutableListOf<Pref<*>>()

    private fun <T : Any> pref(key: String, default: T, resets: Boolean = true): Pref<T> =
        Pref(prefs, key, default).also { if (resets) resettable += it }

    // ---------- Liquid glass ----------
    var glassBlur by pref("glassBlur", 24f)                 // blur radius in dp
    var glassTint by pref("glassTint", 0.14f)               // how milky the glass is
    var glassTintArgb by pref("glassTintArgb", 0xFFFFFFFF.toInt())
    var glassEdge by pref("glassEdge", 0.8f)                // brightness of the rim light + bloom
    var glassDepth by pref("glassDepth", 0.6f)              // drop shadow strength
    var glassLight by pref("glassLight", 225f)              // degrees, 225 = light from the top-left
    var motionLight by pref("motionLight", true)            // light follows how you tilt the phone

    // ---------- Icons ----------
    var iconShape by pref("iconShape", "squircle")          // squircle | circle | rounded | square
    var iconSize by pref("iconSize", 60f)
    var iconGlass by pref("iconGlass", true)                // glossy shine on icons
    var themedIcons by pref("themedIcons", false)           // Android 13+ monochrome icons in accent colour
    var showLabels by pref("showLabels", true)

    // ---------- Icon styles (liquid glass icons) ----------
    var iconStyle by pref("iconStyle", "default")           // default | glass | clear | tinted
    var iconQuality by pref("iconQuality", "light")         // off | light | full
    var iconFrost by pref("iconFrost", 0.12f)               // how milky the glass tile is
    var iconShine by pref("iconShine", 0.8f)                // top sheen + specular highlight
    var iconEdge by pref("iconEdge", 0.8f)                  // rim light brightness and thickness
    var iconTintAmt by pref("iconTintAmt", 0.35f)           // how much of the app's colour (or the accent) shows
    var iconPack by pref("iconPack", "")                      // package name of the chosen icon pack, "" = none
    var iconDarkGlass by pref("iconDarkGlass", false)       // dark glass instead of light glass

    // ---------- Layout ----------
    var cols by pref("cols", 4)
    var rows by pref("rows", 6)
    var dockCount by pref("dockCount", 4)
    var cornerRadius by pref("cornerRadius", 28f)
    var clockWidget by pref("clockWidget", true)
    var clockStyle by pref("clockStyle", "card")            // card | bare
    var appLibrary by pref("appLibrary", true)

    // ---------- Wallpaper ----------
    var wallpaper by pref("wallpaper", "aurora")
    var useCustomWallpaper by pref("useCustomWallpaper", false)
    var wallpaperVersion by pref("wallpaperVersion", 0)
    var animatedWallpaper by pref("animatedWallpaper", true)
    var parallax by pref("parallax", 0.6f)
    var wallpaperLive by pref("wallpaperLive", "none")       // none | curtains | stars | rain | snow | waves | lava | fireflies
    var wallpaperFps by pref("wallpaperFps", 30)             // 15 | 30 | 60
    var wallpaperSpeed by pref("wallpaperSpeed", 1f)         // drift speed multiplier
    var pauseSaver by pref("pauseSaver", true)               // pause the animation in battery saver

    var glassClarity by pref("glassClarity", 0f)             // 0 = normal glass ... 1 = nearly see-through

    // ---------- Animations ----------
    var animStyle by pref("animStyle", "smooth")             // smooth | bouncy | snappy | off
    var animSaver by pref("animSaver", true)                 // switch animations off in battery saver

    var iconVer by pref("iconVer", 0, resets = false)         // bumped whenever a custom app icon changes
    private var namesRaw by pref("appNames", "", resets = false)

    /** Custom app names the person typed in Customize > Apps, by app id. */
    val appNames: Map<String, String>
        get() = namesRaw.split('\n').mapNotNull { line ->
            val i = line.indexOf('|')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

    fun setAppName(id: String, name: String) {
        val clean = name.replace('|', ' ').replace('\n', ' ').trim()
        val m = appNames.toMutableMap()
        if (clean.isEmpty()) m.remove(id) else m[id] = clean
        namesRaw = m.entries.joinToString("\n") { "${it.key}|${it.value}" }
    }

    // ---------- Gestures, locked apps, search, schedule, layouts (27.2) ----------
    var gDtTop by pref("gDtTop", "none")                     // none | search | notifs | library | edit | customize | torch | app:<id>
    var gDtMid by pref("gDtMid", "none")
    var gDtBottom by pref("gDtBottom", "none")
    var autoBackup by pref("autoBackup", true, resets = false)  // keep a copy inside the app, refreshed daily
    var searchContacts by pref("searchContacts", false)
    var searchSettings by pref("searchSettings", true)
    var libraryView by pref("libraryView", "categories")     // categories | az
    var schedFrom by pref("schedFrom", 19)                   // dark from this hour (theme = schedule)
    var schedTo by pref("schedTo", 7)                        // light again from this hour
    var nightWallpaper by pref("nightWallpaper", "")         // wallpaper id used after dark in schedule mode, "" = same
    private var lockedRaw by pref("locked", "", resets = false)
    private var recentRaw by pref("recents", "", resets = false)
    private var layoutsRaw by pref("layouts", "", resets = false)

    val lockedIds: Set<String> get() = lockedRaw.unpack().toSet()

    fun toggleLock(id: String) {
        val l = lockedIds
        lockedRaw = (if (id in l) l - id else l + id).toList().pack()
    }

    val recentIds: List<String> get() = recentRaw.unpack()

    fun noteLaunch(id: String) {
        recentRaw = (listOf(id) + recentIds.filter { it != id }).take(8).pack()
    }

    /** True when the schedule says it is night right now. */
    fun scheduledDark(hour: Int): Boolean =
        if (schedFrom == schedTo) false
        else if (schedFrom > schedTo) hour >= schedFrom || hour < schedTo
        else hour in schedFrom until schedTo

    /** Saved home screen layouts: name -> snapshot of the arrangement. */
    val layoutNames: List<String>
        get() = try {
            val arr = org.json.JSONArray(if (layoutsRaw.isEmpty()) "[]" else layoutsRaw)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        } catch (e: Exception) { emptyList() }

    fun saveLayout(name: String) {
        val clean = name.trim().take(24).ifEmpty { "Layout" }
        val arr = try { org.json.JSONArray(if (layoutsRaw.isEmpty()) "[]" else layoutsRaw) } catch (e: Exception) { org.json.JSONArray() }
        val keep = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("name") != clean) keep.put(o)
        }
        keep.put(
            org.json.JSONObject()
                .put("name", clean)
                .put("home", homeRaw).put("dock", dockRaw).put("hidden", hiddenRaw)
                .put("widgets", widgetsRaw).put("folders", foldersRaw).put("pins", pinsRaw).put("hosted", hostedRaw)
        )
        layoutsRaw = keep.toString()
    }

    fun applyLayout(name: String) {
        try {
            val arr = org.json.JSONArray(layoutsRaw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("name") == name) {
                    homeRaw = o.optString("home"); dockRaw = o.optString("dock"); hiddenRaw = o.optString("hidden")
                    widgetsRaw = o.optString("widgets"); foldersRaw = o.optString("folders")
                    pinsRaw = o.optString("pins"); hostedRaw = o.optString("hosted")
                    return
                }
            }
        } catch (e: Exception) { /* keep the current layout */ }
    }

    fun deleteLayout(name: String) {
        try {
            val arr = org.json.JSONArray(layoutsRaw)
            val keep = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("name") != name) keep.put(o)
            }
            layoutsRaw = keep.toString()
        } catch (e: Exception) { /* nothing saved */ }
    }

    // ---------- Folders ----------
    var folderStyle by pref("folderStyle", "glass")          // glass | clear | tinted | dark
    var folderCols by pref("folderCols", 4)                  // apps per row in the open folder: 3 | 4 | 5

    // ---------- Theme ----------
    var theme by pref("theme", "system")                    // system | dark | light
    var accent by pref("accent", 0xFF0A84FF.toInt())

    // ---------- Behaviour ----------
    var badgeStyle by pref("badgeStyle", "number")           // number | dot | off
    var notifCenter by pref("notifCenter", false)            // glass Notification Center (swipe down on the right half)
    var badgeColor by pref("badgeColor", 0xFFFF3B30.toInt())
    var haptics by pref("haptics", true)
    var swipeSearch by pref("swipeSearch", true)

    // ---------- Layout data (not touched by "reset appearance") ----------
    var lastSeenVersion by pref("lastSeenVersion", "", resets = false)   // the version whose "What's new" she has dismissed
    var seeded by pref("seeded", false, resets = false)
    var dockChecked by pref("dockChecked", false, resets = false)
    var aboutPlaced by pref("aboutPlaced", false, resets = false)
    var customizePlaced by pref("customizePlaced", false, resets = false)
    var weatherName by pref("weatherName", "", resets = false)
    var weatherLat by pref("weatherLat", "", resets = false)
    var weatherLon by pref("weatherLon", "", resets = false)
    var weatherUnit by pref("weatherUnit", "f", resets = false)       // f | c
    var weatherJson by pref("weatherJson", "", resets = false)
    var weatherKey by pref("weatherKey", "", resets = false)
    var weatherTime by pref("weatherTime", "", resets = false)
    private var foldersRaw by pref("folders", "", resets = false)
    private var pinsRaw by pref("grid", "", resets = false)
    private var hostedRaw by pref("awidgets", "", resets = false)
    var widgetsMigrated by pref("widgetsMigrated", false, resets = false)
    private var widgetsRaw by pref("widgets", "", resets = false)
    private var homeRaw by pref("home", "", resets = false)
    private var dockRaw by pref("dock", "", resets = false)
    private var hiddenRaw by pref("hidden", "", resets = false)

    val homeIds: List<String> get() = homeRaw.unpack()
    val dockIds: List<String> get() = dockRaw.unpack()
    val hiddenIds: List<String> get() = hiddenRaw.unpack()

    val widgets: List<WidgetSpec> get() = widgetsRaw.unpack().mapNotNull { WidgetSpec.parse(it) }

    fun widget(id: String): WidgetSpec? = widgets.firstOrNull { it.id == id }

    private fun saveWidgets(list: List<WidgetSpec>) { widgetsRaw = list.joinToString("\n") { it.pack() } }

    /** Adds a widget to the home screen, before [before] (or first if null). */
    fun addWidget(type: String, size: String, style: String, before: String?): String {
        val id = "w:$type:${System.currentTimeMillis()}"
        saveWidgets(widgets + WidgetSpec(id, type, size, style))
        val home = homeIds.toMutableList()
        val at = if (before != null) home.indexOf(before).let { if (it < 0) 0 else it } else 0
        home.add(at, id)
        setHome(home)
        return id
    }

    fun updateWidget(spec: WidgetSpec) {
        saveWidgets(widgets.map { if (it.id == spec.id) spec else it })
    }

    fun removeWidget(id: String) {
        saveWidgets(widgets.filter { it.id != id })
        setHome(homeIds - id)
        unpin(id)
    }

    /** One time (26.3.2 -> 26.4): the old clock card becomes the first clock widget. */
    fun migrateWidgets() {
        if (widgetsMigrated) return
        widgetsMigrated = true
        if (clockWidget && widgets.none { it.type == "clock" }) {
            addWidget("clock", "wide", if (clockStyle == "bare") "bare" else "digital", null)
        }
    }

    // ---------- Free placement on the grid ----------

    val pins: Map<String, Pin>
        get() = pinsRaw.unpack().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 4) null else {
                val pg = p[1].toIntOrNull()
                val c = p[2].toIntOrNull()
                val r = p[3].toIntOrNull()
                if (pg == null || c == null || r == null) null else p[0] to Pin(pg, c, r)
            }
        }.toMap()

    private fun savePins(map: Map<String, Pin>) {
        pinsRaw = map.entries.joinToString("\n") { "${it.key}|${it.value.page}|${it.value.col}|${it.value.row}" }
    }

    fun pinTo(id: String, page: Int, col: Int, row: Int) {
        savePins(pins + (id to Pin(page, col, row)))
    }

    fun unpin(id: String) {
        val m = pins
        if (id in m) savePins(m - id)
    }

    /** Makes every item's current spot explicit, so later changes (new apps, removed pages) never shuffle it. */
    fun commitPlacements(pages: List<List<Placed>>) {
        val m = LinkedHashMap<String, Pin>()
        pages.forEachIndexed { pg, items -> items.forEach { m[it.id] = Pin(pg, it.col, it.row) } }
        savePins(m)
    }

    private fun inheritPin(from: String, to: String) {
        val m = pins
        val p = m[from] ?: return
        savePins(m + (to to p))
    }

    // ---------- Real Android widgets ----------

    val hosted: List<HostedWidget> get() = hostedRaw.unpack().mapNotNull { HostedWidget.parse(it) }

    fun hostedWidget(id: String): HostedWidget? = hosted.firstOrNull { it.id == id }

    private fun saveHosted(list: List<HostedWidget>) { hostedRaw = list.joinToString("\n") { it.pack() } }

    fun addHosted(appWidgetId: Int, provider: String, cw: Int, ch: Int): String {
        val id = "a:$appWidgetId"
        saveHosted(hosted.filter { it.id != id } + HostedWidget(id, appWidgetId, provider, cw, ch))
        if (id !in homeIds) setHome(homeIds + id)
        return id
    }

    fun updateHosted(h: HostedWidget) { saveHosted(hosted.map { if (it.id == h.id) h else it }) }

    /** Removes it from the home screen and returns its appWidgetId so the caller can free it. */
    fun removeHosted(id: String): Int? {
        val h = hostedWidget(id) ?: return null
        saveHosted(hosted.filter { it.id != id })
        setHome(homeIds - id)
        unpin(id)
        return h.appWidgetId
    }

    val folders: List<FolderSpec> get() = foldersRaw.unpack().mapNotNull { FolderSpec.parse(it) }

    fun folder(id: String): FolderSpec? = folders.firstOrNull { it.id == id }

    val folderMembers: Set<String> get() = folders.flatMap { it.members }.toSet()

    private fun saveFolders(list: List<FolderSpec>) { foldersRaw = list.joinToString("\n") { it.pack() } }

    /** A folder with fewer than two apps goes away; a lone app takes its place on the home screen. */
    private fun cleanFolders() {
        val all = folders
        val broken = all.filter { it.members.size < 2 }
        if (broken.isEmpty()) return
        val home = homeIds.toMutableList()
        for (f in broken) {
            val at = home.indexOf(f.id)
            if (at >= 0) {
                home.removeAt(at)
                f.members.forEachIndexed { i, m -> if (m !in home) home.add(at + i, m) }
                f.members.firstOrNull()?.let { inheritPin(f.id, it) }
            }
        }
        val inDock = broken.filter { it.id in dockIds }
        if (inDock.isNotEmpty()) {
            val dock = dockIds.toMutableList()
            for (f in inDock) {
                val at = dock.indexOf(f.id)
                dock.removeAt(at)
                f.members.firstOrNull()?.let { if (it !in dock) dock.add(at.coerceAtMost(dock.size), it) }
            }
            setDock(dock)
        }
        saveFolders(all.filter { it.members.size >= 2 })
        setHome(home)
    }

    private fun pullFromFolders(id: String) {
        if (folders.any { id in it.members }) {
            saveFolders(folders.map { it.copy(members = it.members - id) })
            cleanFolders()
        }
    }

    /** Dropping [dragged] on the middle of [target] makes a folder of the two (or adds it to the folder). */
    fun mergeIntoFolder(dragged: String, target: String) {
        if (dragged.startsWith("w:") || dragged.startsWith("f:") || target.startsWith("w:") || dragged == target) return
        val home = homeIds.toMutableList()
        val dock = dockIds.toMutableList()
        if (target !in home) return
        home.remove(dragged)
        dock.remove(dragged)
        val idx = home.indexOf(target)
        if (target.startsWith("f:")) {
            saveFolders(folders.map { if (it.id == target && dragged !in it.members) it.copy(members = it.members + dragged) else it })
        } else {
            val fid = "f:${System.currentTimeMillis()}"
            saveFolders(folders + FolderSpec(fid, "Folder", listOf(target, dragged)))
            home[idx] = fid
            inheritPin(target, fid)
        }
        setHome(home)
        setDock(dock)
    }

    fun renameFolder(id: String, name: String) {
        val clean = name.replace('|', ' ').replace(';', ' ').replace('\n', ' ').take(24)
        saveFolders(folders.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun takeOutOfFolder(folderId: String, appId: String) {
        val home = homeIds.toMutableList()
        val at = home.indexOf(folderId)
        if (appId !in home) home.add(if (at >= 0) at + 1 else home.size, appId)
        setHome(home)
        saveFolders(folders.map { if (it.id == folderId) it.copy(members = it.members - appId) else it })
        cleanFolders()
    }

    fun dissolveFolder(id: String) {
        val f = folder(id) ?: return
        val home = homeIds.toMutableList()
        val at = home.indexOf(id).let { if (it < 0) home.size else it }
        home.remove(id)
        f.members.forEachIndexed { i, m -> if (m !in home) home.add((at + i).coerceAtMost(home.size), m) }
        f.members.firstOrNull()?.let { inheritPin(id, it) }
        setHome(home)
        if (id in dockIds) setDock(dockIds - id)
        saveFolders(folders.filter { it.id != id })
    }

    /** Drops [id] on the home screen at a grid cell (taking it out of the dock if it came from there). */
    fun placeAt(id: String, page: Int, col: Int, row: Int) {
        val dock = dockIds.toMutableList()
        if (dock.remove(id)) setDock(dock)
        if (id !in homeIds) setHome(homeIds + id)
        pinTo(id, page, col, row)
    }

    fun setHome(list: List<String>) { homeRaw = list.pack() }
    fun setDock(list: List<String>) { dockRaw = list.pack() }

    fun resetAppearance() {
        resettable.forEach { it.reset() }
    }

    fun resetLayout() {
        homeRaw = ""
        dockRaw = ""
        hiddenRaw = ""
        seeded = false
        aboutPlaced = false
        customizePlaced = false
        widgetsRaw = ""
        foldersRaw = ""
        pinsRaw = ""
        hostedRaw = ""
        widgetsMigrated = false
    }

    // ---------- Arranging apps ----------

    fun appendNew(ids: List<String>) {
        val have = homeIds.toSet() + dockIds.toSet() + folderMembers
        val add = ids.filter { it !in have }
        if (add.isNotEmpty()) setHome(homeIds + add)
    }

    fun addToHome(id: String) {
        pullFromFolders(id)
        removeFromDock(id, toHome = false)
        if (id !in homeIds) setHome(homeIds + id)
    }

    fun removeFromHome(id: String) {
        pullFromFolders(id)
        setHome(homeIds - id)
        setDock(dockIds - id)
    }

    fun addToDock(id: String) {
        pullFromFolders(id)
        val dock = dockIds.toMutableList()
        val home = homeIds.toMutableList()
        home.remove(id)
        dock.remove(id)
        dock.add(id)
        while (dock.size > dockCount) home.add(dock.removeAt(0))
        setDock(dock)
        setHome(home)
    }

    fun removeFromDock(id: String, toHome: Boolean = true) {
        setDock(dockIds - id)
        if (toHome && id !in homeIds) setHome(homeIds + id)
    }

    /** If the dock got smaller, push the extra apps back onto the home screen instead of losing them. */
    fun normalizeDock() {
        val d = dockIds
        if (d.size > dockCount) {
            setHome(homeIds + d.drop(dockCount).filter { it !in homeIds })
            setDock(d.take(dockCount))
        }
    }

    fun hide(id: String) {
        removeFromHome(id)
        if (id !in hiddenIds) hiddenRaw = (hiddenIds + id).pack()
    }

    fun unhideAll() {
        val back = hiddenIds
        hiddenRaw = ""
        setHome(homeIds + back.filter { it !in homeIds && it !in dockIds && it !in folderMembers })
    }

    /** Swaps two apps' positions, whether they are on the home pages or in the dock. */
    fun swap(a: String, b: String) {
        val home = homeIds.toMutableList()
        val dock = dockIds.toMutableList()
        val la = listOf(home, dock).firstOrNull { a in it } ?: return
        val lb = listOf(home, dock).firstOrNull { b in it } ?: return
        val ia = la.indexOf(a)
        val ib = lb.indexOf(b)
        la[ia] = b
        lb[ib] = a
        setHome(home)
        setDock(dock)
    }

    /**
     * Drag and drop. Puts [id] into the slot of [target] (or at the end when [target] is null),
     * in the dock or on the home pages. If the dock overflows, its extra app goes back home.
     */
    fun dropOnto(id: String, target: String?, intoDock: Boolean, after: Boolean = false) {
        val home = homeIds.toMutableList()
        val dock = dockIds.toMutableList()
        val fromDock = id in dock
        val slot = if (fromDock) -1 else home.indexOf(id)
        val dest = if (intoDock) dock else home
        val fi = dest.indexOf(id)
        val ti = if (target != null) dest.indexOf(target) else -1
        home.remove(id)
        dock.remove(id)
        val at = when {
            ti < 0 -> dest.size
            after -> if (fi in 0 until ti) ti else ti + 1
            else -> ti
        }.coerceIn(0, dest.size)
        dest.add(at, id)
        if (intoDock) unpin(id)
        if (dock.size > dockCount) {
            val extra = dock.lastOrNull { it != id } ?: dock.first()
            dock.remove(extra)
            home.add(if (slot in 0..home.size) slot else home.size, extra)
        }
        setHome(home)
        setDock(dock)
    }

    private fun String.unpack(): List<String> = if (isEmpty()) emptyList() else split('\n')
    private fun List<String>.pack(): String = joinToString("\n")
}

/**
 * A widget on the home screen. size: s (2x2), w (4x2), l (4x4).
 * frost, tint, corner and text are per-widget looks; -1 / 0 mean "use the global look".
 */
data class WidgetSpec(
    val id: String,
    val type: String,
    val size: String,
    val style: String,
    val frost: Float = -1f,
    val tint: Int = 0,
    val corner: Float = -1f,
    val text: Int = 0,
    val cw: Int = 0,
    val ch: Int = 0,
    /** Extra per-type setting (a clock's second city, as "TimeZoneId;Label"). */
    val extra: String = "",
) {
    fun pack() = "$id|$type|$size|$style|$frost|$tint|$corner|$text|$cw|$ch|${extra.replace('|', ' ').replace('\n', ' ')}"

    /** Cells wide and tall. */
    fun span(cols: Int): Pair<Int, Int> = if (cw > 0 && ch > 0) cw.coerceAtMost(cols) to ch else when (size) {
        "s" -> 2.coerceAtMost(cols) to 2
        "l" -> cols to 4
        else -> cols to 2
    }

    companion object {
        fun parse(line: String): WidgetSpec? {
            val p = line.split('|')
            if (p.size < 4) return null
            return WidgetSpec(
                p[0], p[1], p[2], p[3],
                p.getOrNull(4)?.toFloatOrNull() ?: -1f,
                p.getOrNull(5)?.toIntOrNull() ?: 0,
                p.getOrNull(6)?.toFloatOrNull() ?: -1f,
                p.getOrNull(7)?.toIntOrNull() ?: 0,
                p.getOrNull(8)?.toIntOrNull() ?: 0,
                p.getOrNull(9)?.toIntOrNull() ?: 0,
                p.getOrNull(10) ?: "",
            )
        }
    }
}

/** A folder on the home screen: a name and the apps inside it (their component ids). */
data class FolderSpec(val id: String, val name: String, val members: List<String>) {
    fun pack() = "$id|$name|${members.joinToString(";")}"

    companion object {
        fun parse(line: String): FolderSpec? {
            val p = line.split('|')
            if (p.size < 3) return null
            return FolderSpec(p[0], p[1], p[2].split(';').filter { it.isNotEmpty() })
        }
    }
}

/** Where an item is pinned on the home grid. */
class Pin(val page: Int, val col: Int, val row: Int)

/** A real Android widget from another app, living on the home screen. */
data class HostedWidget(val id: String, val appWidgetId: Int, val provider: String, val cw: Int, val ch: Int) {
    fun pack() = "$id|$appWidgetId|$provider|$cw|$ch"

    companion object {
        fun parse(line: String): HostedWidget? {
            val p = line.split('|')
            if (p.size < 5) return null
            val aw = p[1].toIntOrNull() ?: return null
            return HostedWidget(p[0], aw, p[2], p[3].toIntOrNull() ?: 2, p[4].toIntOrNull() ?: 2)
        }
    }
}
