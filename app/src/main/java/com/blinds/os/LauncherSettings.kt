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

    // ---------- Theme ----------
    var theme by pref("theme", "system")                    // system | dark | light
    var accent by pref("accent", 0xFF0A84FF.toInt())

    // ---------- Behaviour ----------
    var haptics by pref("haptics", true)
    var swipeSearch by pref("swipeSearch", true)

    // ---------- Layout data (not touched by "reset appearance") ----------
    var seeded by pref("seeded", false, resets = false)
    var dockChecked by pref("dockChecked", false, resets = false)
    var aboutPlaced by pref("aboutPlaced", false, resets = false)
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
    fun addWidget(type: String, size: String, style: String, before: String?) {
        val id = "w:$type:${System.currentTimeMillis()}"
        saveWidgets(widgets + WidgetSpec(id, type, size, style))
        val home = homeIds.toMutableList()
        val at = if (before != null) home.indexOf(before).let { if (it < 0) 0 else it } else 0
        home.add(at, id)
        setHome(home)
    }

    fun updateWidget(spec: WidgetSpec) {
        saveWidgets(widgets.map { if (it.id == spec.id) spec else it })
    }

    fun removeWidget(id: String) {
        saveWidgets(widgets.filter { it.id != id })
        setHome(homeIds - id)
    }

    /** One time (26.3.2 -> 26.4): the old clock card becomes the first clock widget. */
    fun migrateWidgets() {
        if (widgetsMigrated) return
        widgetsMigrated = true
        if (clockWidget && widgets.none { it.type == "clock" }) {
            addWidget("clock", "wide", if (clockStyle == "bare") "bare" else "digital", null)
        }
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
        widgetsRaw = ""
        widgetsMigrated = false
    }

    // ---------- Arranging apps ----------

    fun appendNew(ids: List<String>) {
        val have = homeIds.toSet() + dockIds.toSet()
        val add = ids.filter { it !in have }
        if (add.isNotEmpty()) setHome(homeIds + add)
    }

    fun addToHome(id: String) {
        removeFromDock(id, toHome = false)
        if (id !in homeIds) setHome(homeIds + id)
    }

    fun removeFromHome(id: String) {
        setHome(homeIds - id)
        setDock(dockIds - id)
    }

    fun addToDock(id: String) {
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
        setHome(homeIds + back.filter { it !in homeIds && it !in dockIds })
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

/** A widget on the home screen. size: s (2x2), w (4x2), l (4x4). */
data class WidgetSpec(val id: String, val type: String, val size: String, val style: String) {
    fun pack() = "$id|$type|$size|$style"

    /** Cells wide and tall. */
    fun span(cols: Int): Pair<Int, Int> = when (size) {
        "s" -> 2.coerceAtMost(cols) to 2
        "l" -> cols to 4
        else -> cols to 2
    }

    companion object {
        fun parse(line: String): WidgetSpec? {
            val p = line.split('|')
            return if (p.size >= 4) WidgetSpec(p[0], p[1], p[2], p[3]) else null
        }
    }
}
